package com.example.cicdsample.data.call

import com.example.cicdsample.data.audio.AudioCapture
import com.example.cicdsample.data.audio.AudioDeviceFactory
import com.example.cicdsample.data.audio.AudioPlayback
import com.example.cicdsample.data.net.RtpTransport
import com.example.cicdsample.data.net.RtpTransportFactory
import com.example.cicdsample.data.net.SdpCodec
import com.example.cicdsample.data.rtp.G711Codec
import com.example.cicdsample.data.rtp.JitterBuffer
import com.example.cicdsample.data.rtp.RtpPacket
import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallSnapshot
import com.example.cicdsample.domain.model.call.CallStage
import com.example.cicdsample.domain.model.call.MediaKind
import com.example.cicdsample.domain.model.call.MediaSession
import com.example.cicdsample.domain.model.call.Payload
import com.example.cicdsample.domain.model.call.PeerAddress
import com.example.cicdsample.domain.repository.CallRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 도메인이 선언한 [CallRepository] 의 유일한 구현.
 *
 * 나가는 길은 마이크 → G.711 → RTP → UDP 이고, 들어오는 길은 그 역순에 지터버퍼가 하나 더 붙는다.
 *
 * ### 왜 한 코루틴에서 다 하는가
 * 수신 드레인·재생·송신을 스레드 셋으로 나누면 순번·타임스탬프·통계를 공유하느라 락이 생기고,
 * 20ms 마다 스레드 셋을 깨우는 비용이 정작 하는 일보다 커진다. 한 바퀴에 세 가지를 순서대로 한다.
 *
 * ### 박자는 마이크가 만든다
 * `AudioRecord.read` 는 한 프레임(20ms)이 찰 때까지 블로킹하므로 별도 타이머가 필요 없다.
 * 장치가 프레임을 못 채워 짧게 돌려줄 때만 [delay] 로 쉰다. 이 구조 덕에 테스트에서는
 * 페이크 마이크가 프레임을 즉시 주고, 다 마르면 가상 시간에서 park 한다 — 실시간을 기다리지 않는다.
 */
@Singleton
class DefaultCallRepository @Inject constructor(
    private val transportFactory: RtpTransportFactory,
    private val audioFactory: AudioDeviceFactory,
    private val sdpCodec: SdpCodec,
    @CallScope private val scope: CoroutineScope,
) : CallRepository {

    private val snapshot = MutableStateFlow(CallSnapshot())

    /** [start] 와 [end] 를 직렬화한다. 버튼을 두 번 누른 결과로 마이크를 두 번 열지 않게 한다. */
    private val lifecycle = Mutex()

    /**
     * 오디오 통로는 통화가 끝나도 닫지 않는다.
     *
     * 시그널링이 사람이라 사용자는 SDP 를 미리 상대에게 복사해 둔다. 통화마다 소켓을 새로 열면
     * OS 가 다른 포트를 줄 수 있어 이미 건네준 SDP 가 거짓이 된다. SIP 단말이 자기 RTP 포트를
     * 계속 물고 있는 것과 같은 이유다.
     */
    private var audioTransport: RtpTransport? = null
    private var audioSession: MediaSession? = null

    private var callJob: Job? = null

    override fun observeCall(): Flow<CallSnapshot> = snapshot.asStateFlow()

    override fun localSessions(): List<MediaSession> = listOfNotNull(ensureAudioSession())

    override suspend fun start(peer: PeerAddress, withVideo: Boolean) {
        lifecycle.withLock {
            // 이미 통화 중이면 새 요청은 버린다. 겹치면 마이크를 두 번 열게 된다.
            if (snapshot.value.inProgress) return

            val session = ensureAudioSession()
            val transport = audioTransport
            if (session == null || transport == null) {
                snapshot.value = endedSnapshot(peer, withVideo, CallEndReason.TransportError)
                return
            }

            audioFactory.enterCallMode()

            val capture = audioFactory.createCapture()
            if (!capture.start()) {
                // 마이크가 열리지 않는 가장 흔한 이유는 권한이다.
                capture.close()
                audioFactory.exitCallMode()
                snapshot.value = endedSnapshot(peer, withVideo, CallEndReason.PermissionDenied)
                return
            }

            val playback = audioFactory.createPlayback()
            if (!playback.start()) {
                capture.close()
                playback.close()
                audioFactory.exitCallMode()
                // 도메인의 종료 이유는 넷뿐이다. 재생 장치 실패는 사용자에게 "기기 문제로 통화가
                // 안 됐다" 로 보이므로 전송 오류와 같은 칸에 넣는다.
                snapshot.value = endedSnapshot(peer, withVideo, CallEndReason.TransportError)
                return
            }

            discardPending(transport)

            snapshot.value = CallSnapshot(
                stage = CallStage.Connecting,
                peer = peer,
                videoEnabled = withVideo,
            )
            callJob = scope.launch {
                CallLoop(peer, session, transport, capture, playback).run()
            }
        }
    }

    override suspend fun end(reason: CallEndReason) {
        lifecycle.withLock {
            if (!snapshot.value.inProgress) return

            // 장치를 닫는 것은 루프의 finally 다 — 여는 쪽과 닫는 쪽을 한 군데로 모아 둔다.
            callJob?.cancelAndJoin()
            callJob = null

            // 루프가 스스로 끝냈으면(타임아웃·전송 오류) 그 이유를 덮어쓰지 않는다.
            snapshot.update { current ->
                if (current.stage == CallStage.Ended) {
                    current
                } else {
                    current.copy(stage = CallStage.Ended, endReason = reason)
                }
            }
        }
    }

    /**
     * 음소거는 통화 중에만 의미가 있다. [start] 가 스냅샷을 새로 만들므로
     * 통화 전에 켜 둔 음소거는 통화에 반영되지 않는다.
     */
    override suspend fun setMicMuted(muted: Boolean) {
        snapshot.update { if (it.inProgress) it.copy(micMuted = muted) else it }
    }

    /**
     * 지금은 상태만 바꾼다. 카메라 캡처와 H.264 송신은 다음 단계다 —
     * [com.example.cicdsample.data.rtp.H264Packetizer] 와 붙일 자리(루프)는 이미 있지만,
     * 미리보기 Surface 를 만드는 화면이 없는 상태에서는 켤 수단이 없다.
     */
    override suspend fun setVideoEnabled(enabled: Boolean) {
        snapshot.update { if (it.inProgress) it.copy(videoEnabled = enabled) else it }
    }

    override fun localSdp(localHost: String): String {
        val sessions = localSessions()
        // 포트를 열지 못하면 보여줄 것이 없다. 예외로 올리지 않는 이유는 계약이 String 이기
        // 때문이고, 화면은 빈 문자열을 "아직 포트를 열지 못했다" 로 다룬다.
        if (sessions.none { it.kind == MediaKind.AUDIO }) return ""
        return sdpCodec.encode(sessions, localHost)
    }

    override fun parseRemoteSdp(text: String): Result<PeerAddress> = sdpCodec.decode(text)

    /**
     * 오디오 소켓을 (필요하면) 열고 세션을 만든다.
     *
     * 통화 전에도 포트를 알아야 SDP 를 만들 수 있으므로 여기서 미리 연다.
     * 기본 포트를 먼저 시도하는 이유는 사람이 외우기 쉬워서다 — 이미 쓰이고 있으면
     * OS 가 고른 포트로 물러난다. 통화를 아예 못 하는 것보다 낫다.
     *
     * 화면 스레드에서도 불리므로 [synchronized] 로 한 번만 열리게 막는다.
     */
    private fun ensureAudioSession(): MediaSession? = synchronized(this) {
        audioSession?.let { return it }

        val transport = openTransport(DEFAULT_AUDIO_PORT)
            ?: openTransport(ANY_PORT)
            ?: return null

        val session = MediaSession(
            kind = MediaKind.AUDIO,
            payload = Payload.PCMU,
            localPort = transport.localPort,
            ssrc = Random.nextLong(MAX_UINT32 + 1),
        )
        audioTransport = transport
        audioSession = session
        return session
    }

    private fun openTransport(localPort: Int): RtpTransport? =
        runCatching { transportFactory.open(localPort) }.getOrNull()

    /**
     * 소켓에 남아 있던 패킷을 버린다.
     *
     * 통로를 계속 열어 두므로 지난 통화의 꼬리가 남아 있을 수 있다. 그것을 그대로 먹으면
     * 순번이 뒤엉켜 지터버퍼가 새 통화의 첫 프레임들을 유실로 판정한다.
     */
    private fun discardPending(transport: RtpTransport) {
        val scratch = ByteArray(MAX_DATAGRAM_BYTES)
        var drained = 0
        // 상대가 이미 쏘고 있을 수도 있으니 한도를 둔다 — 여기서 오래 머물면 통화 시작이 늦는다.
        while (drained < STALE_DRAIN_LIMIT && transport.receive(scratch, POLL_TIMEOUT_MS) >= 0) {
            drained++
        }
    }

    private fun endedSnapshot(
        peer: PeerAddress,
        withVideo: Boolean,
        reason: CallEndReason,
    ) = CallSnapshot(
        stage = CallStage.Ended,
        peer = peer,
        videoEnabled = withVideo,
        endReason = reason,
    )

    /**
     * 통화 한 건의 송수신 루프.
     *
     * 통화마다 인스턴스를 새로 만든다 — 순번·타임스탬프·상대 SSRC 가 통화 사이로 새지 않는다.
     */
    private inner class CallLoop(
        private val peer: PeerAddress,
        private val session: MediaSession,
        private val transport: RtpTransport,
        private val capture: AudioCapture,
        private val playback: AudioPlayback,
    ) {
        private val jitter = JitterBuffer(clockRate = session.payload.clockRate)
        private val pcmFrame = ByteArray(G711Codec.PCM_BYTES_PER_FRAME)
        private val datagram = ByteArray(MAX_DATAGRAM_BYTES)

        // 순번과 타임스탬프는 무작위에서 출발한다(RFC 3550 §5.1). 0 에서 시작하면 세션이 겹칠 때
        // 지난 통화의 패킷을 새 통화의 것으로 착각할 여지가 남는다.
        private var sequence = Random.nextInt(RtpPacket.MAX_SEQUENCE + 1)
        private var timestamp = Random.nextLong(MAX_UINT32 + 1)

        /** 처음 받은 SSRC 를 상대로 고정한다. 같은 포트로 끼어드는 다른 소스는 무시한다. */
        private var remoteSsrc: Long? = null

        /** 무음 뒤 첫 패킷에는 marker 를 세운다 — talkspurt 의 시작(RFC 3551 §4.1). */
        private var markNextFrame = true

        private var silentTicks = 0
        private var ticksSincePush = 0

        /** 음소거가 이어진 프레임 수. keepalive 를 보낼 때가 됐는지 센다. */
        private var mutedTicks = 0

        suspend fun run() {
            try {
                while (currentCoroutineContext().isActive) {
                    // 순서에 뜻이 있다. 받은 것을 먼저 보고 상태를 확정한 뒤에 마이크를 읽는다 —
                    // 송신은 프레임이 찰 때까지 블로킹하므로, 뒤에 두면 상태 전이가 한 틱 늦는다.
                    val heard = drainInbound()
                    if (!stillHearingPeer(heard)) return
                    playOut()
                    pushStats()
                    if (!sendOneFrame()) return
                }
            } catch (_: IOException) {
                // 소켓이 죽었다. 통화는 여기서 끝난다.
                finishWith(CallEndReason.TransportError)
            } finally {
                // 취소(사용자 끊기)로 끝났을 때만 마지막 통계를 싣는다. 스스로 끝낸 경우에는
                // finishWith 가 이미 같은 스냅샷에 통계까지 넣어 두었다.
                snapshot.update { if (it.inProgress) it.copy(audioStats = jitter.stats()) else it }
                capture.close()
                playback.close()
                audioFactory.exitCallMode()
            }
        }

        /**
         * 소켓에 쌓인 것을 지터버퍼로 옮긴다.
         *
         * @return 상대가 보낸 것으로 인정한 패킷 수. 깨진 패킷과 모르는 SSRC 는 세지 않는다 —
         *   엉뚱한 트래픽 하나로 끊긴 통화가 계속 열려 있으면 사용자는 왜 소리가 안 나는지 모른다.
         */
        private fun drainInbound(): Int {
            var heard = 0
            var polls = 0
            while (polls < MAX_PACKETS_PER_TICK) {
                polls++
                val length = transport.receive(datagram, POLL_TIMEOUT_MS)
                if (length < 0) break // 지금은 조용하다

                val packet = RtpPacket.parse(datagram, length) ?: continue // 깨진 패킷은 버린다
                if (packet.payloadType != session.payload.payloadType) continue

                val known = remoteSsrc
                if (known == null) {
                    remoteSsrc = packet.ssrc
                } else if (known != packet.ssrc) {
                    // 다른 소스를 받아 주면 순번 공간이 섞여 지터버퍼가 정상 패킷까지 버린다.
                    continue
                }

                heard++
                jitter.offer(packet, arrivalTicks())
            }
            return heard
        }

        /** 지터버퍼에서 한 프레임을 꺼내 스피커로 보낸다. */
        private fun playOut() {
            val next = jitter.poll()
            if (next != null) {
                val pcm = G711Codec.decode(next.payload)
                playback.write(pcm, pcm.size)
                return
            }
            // 꺼낼 것이 없다. 통화가 붙은 뒤라면 무음으로 메운다 —
            // AudioTrack 을 굶기면 언더런이 '딸깍' 소리로 들린다.
            if (snapshot.value.stage == CallStage.Active) playback.write(SILENCE, SILENCE.size)
        }

        /**
         * 마이크 한 프레임을 읽어 보낸다. 이 호출이 루프의 박자를 만든다.
         *
         * @return 통화를 계속할지. false 면 이미 종료 상태를 스냅샷에 넣었다.
         */
        private suspend fun sendOneFrame(): Boolean {
            val read = capture.read(pcmFrame)

            if (read < 0) {
                // 마이크가 죽었다. 도메인 종료 이유가 넷뿐이라 장치 오류도 전송 오류로 묶는다.
                finishWith(CallEndReason.TransportError)
                return false
            }

            if (read < pcmFrame.size) {
                // 장치가 아직 한 프레임을 못 채웠다. 한 프레임만큼 쉬고 다시 본다 —
                // 쉬지 않으면 굶은 마이크를 상대로 루프가 CPU 를 태운다.
                delay(FRAME_MS)
                return true
            }

            if (snapshot.value.micMuted) {
                // 음소거는 마이크 프레임을 보내지 않는다. 다만 아주 침묵하면 상대의 수신
                // 타임아웃(8초)에 걸려 통화가 끊긴다 — 영상이 흐르지 않는 오디오 전용 통화에서는
                // 음성 패킷이 유일한 생존 신호다. 그래서 2초마다 무음 한 프레임만 보낸다
                // (RFC 6263 이 말하는 RTP keepalive).
                if (++mutedTicks >= KEEPALIVE_TICKS) {
                    mutedTicks = 0
                    sendPacket(SILENCE_ULAW)
                }
                // 타임스탬프는 계속 흘려야 상대의 재생 위치가 어긋나지 않고,
                // 다시 켤 때 marker 로 talkspurt 시작을 알린다.
                timestamp = nextTimestamp()
                markNextFrame = true
                return true
            }

            mutedTicks = 0
            sendPacket(G711Codec.encode(pcmFrame, read))
            timestamp = nextTimestamp()
            return true
        }

        private fun sendPacket(payload: ByteArray) {
            val packet = RtpPacket(
                payloadType = session.payload.payloadType,
                sequenceNumber = sequence,
                timestamp = timestamp,
                ssrc = session.ssrc,
                marker = markNextFrame,
                payload = payload,
            )
            val bytes = packet.toBytes()
            transport.send(bytes, bytes.size, peer.host, peer.audioPort)

            // 순번은 '보낸' 패킷에만 붙는다. 보내지 않은 프레임까지 세면 상대가 유실로 읽는다.
            sequence = (sequence + 1) and RtpPacket.MAX_SEQUENCE
            markNextFrame = false
        }

        /**
         * 상대가 아직 살아 있는지 본다. 첫 패킷이 곧 통화 성립이다 — 시그널링이 없으므로
         * '수락' 같은 신호가 따로 없다.
         *
         * @return 통화를 계속할지.
         */
        private fun stillHearingPeer(heard: Int): Boolean {
            if (heard > 0) {
                silentTicks = 0
                if (snapshot.value.stage == CallStage.Connecting) {
                    snapshot.update { it.copy(stage = CallStage.Active) }
                }
                return true
            }

            silentTicks++
            if (silentTicks < SILENT_TICK_LIMIT) return true

            finishWith(CallEndReason.ReceiveTimeout)
            return false
        }

        private fun pushStats() {
            if (++ticksSincePush < STATS_PUSH_TICKS) return
            ticksSincePush = 0
            snapshot.update { it.copy(audioStats = jitter.stats()) }
        }

        private fun finishWith(reason: CallEndReason) {
            snapshot.update {
                it.copy(stage = CallStage.Ended, endReason = reason, audioStats = jitter.stats())
            }
        }

        private fun nextTimestamp(): Long =
            (timestamp + G711Codec.SAMPLES_PER_FRAME) and MAX_UINT32

        /** 도착 시각을 페이로드 클럭(8kHz) 단위로 환산한다. 지터 계산에만 쓴다. */
        private fun arrivalTicks(): Long =
            System.nanoTime() / (NANOS_PER_SECOND / session.payload.clockRate)
    }

    private companion object {
        /** 사람이 외우기 쉬운 기본 포트. RFC 3550 은 RTP 에 짝수 포트를 권한다. */
        const val DEFAULT_AUDIO_PORT = 5_004

        /** OS 가 빈 포트를 고르게 한다. */
        const val ANY_PORT = 0

        /** 한 프레임 = 20ms. G.711 프레임 길이이자 루프 한 바퀴의 목표 주기다. */
        const val FRAME_MS = 20L

        /**
         * 소켓 폴 시간. 0 을 넘기면 안 된다 — `DatagramSocket.soTimeout` 의 0 은 '무한 대기'다.
         * 1ms 면 사실상 논블로킹이고, 남은 19ms 는 마이크가 쓴다.
         */
        const val POLL_TIMEOUT_MS = 1

        /** 한 틱에 처리할 패킷 수 한도. 20ms 면 보통 한 개지만 몰려 올 때를 위해 여유를 둔다. */
        const val MAX_PACKETS_PER_TICK = 8

        /** 통화를 시작할 때 버릴 잔여 패킷 수 한도. */
        const val STALE_DRAIN_LIMIT = 64

        /** MTU 한 장. 오디오는 172바이트지만 상대가 확장 헤더를 붙여 보낼 수 있다. */
        const val MAX_DATAGRAM_BYTES = 1_500

        /** 통계는 500ms 마다만 넣는다. 20ms 마다 갱신하면 화면이 초당 50번 다시 그려진다. */
        const val STATS_PUSH_TICKS = 25

        const val MAX_UINT32 = 0xFFFF_FFFFL

        const val NANOS_PER_SECOND = 1_000_000_000L

        /**
         * 연속 무수신 틱 한도. 시계를 따로 주입하지 않고 틱을 세는 이유는 한 틱이 곧 20ms 이기
         * 때문이다 — 실기기에서는 마이크가, 테스트에서는 가상 시간이 그 20ms 를 만든다.
         */
        val SILENT_TICK_LIMIT = (MediaSession.RECEIVE_TIMEOUT_MS / FRAME_MS).toInt()

        /**
         * 음소거 중 생존 신호를 보내는 간격(2초). 상대의 수신 타임아웃(8초)보다 넉넉히 짧아
         * 패킷 하나를 잃어도 여유가 남는다.
         */
        const val KEEPALIVE_TICKS = 100

        /** 16비트 PCM 에서 0 은 무음이다. 매 틱 새로 만들지 않고 한 장을 돌려 쓴다. */
        val SILENCE = ByteArray(G711Codec.PCM_BYTES_PER_FRAME)

        /** 무음 한 프레임의 μ-law. keepalive 로 그대로 보낸다. */
        val SILENCE_ULAW = G711Codec.encode(SILENCE)
    }
}
