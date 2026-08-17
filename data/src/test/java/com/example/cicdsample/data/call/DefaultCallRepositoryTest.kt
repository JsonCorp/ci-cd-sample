package com.example.cicdsample.data.call

import com.example.cicdsample.data.net.SdpCodec
import com.example.cicdsample.data.rtp.G711Codec
import com.example.cicdsample.data.rtp.RtpPacket
import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallSnapshot
import com.example.cicdsample.domain.model.call.CallStage
import com.example.cicdsample.domain.model.call.MediaSession
import com.example.cicdsample.domain.model.call.Payload
import com.example.cicdsample.domain.model.call.PeerAddress
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 통화 저장소 테스트. 소켓도 마이크도 없이 JVM 에서 돈다 —
 * 전송 통로와 오디오 장치가 인터페이스라서 페이크를 꽂을 수 있고,
 * 루프의 박자가 `delay` 라서 가상 시간으로 8초를 즉시 흘려보낼 수 있다.
 */
// 가상 시간을 손으로 미는 API(advanceTimeBy/runCurrent)가 아직 실험 단계다.
// 20ms 틱을 한 칸씩 확인하려면 이 API 가 필요하다.
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCallRepositoryTest {

    private val transport = FakeRtpTransport(localPort = 5_004)
    private val devices = FakeAudioDeviceFactory()

    private val peer = PeerAddress(host = "192.168.0.9", audioPort = 6_000, videoPort = null)

    private fun TestScope.repository(
        transportFactory: FakeRtpTransportFactory = FakeRtpTransportFactory(transport),
    ) = DefaultCallRepository(
        transportFactory = transportFactory,
        audioFactory = devices,
        sdpCodec = SdpCodec(),
        // backgroundScope 는 테스트가 끝나면 알아서 취소된다 — 통화 루프를 손으로 정리하지 않아도 된다.
        scope = backgroundScope,
    )

    private suspend fun DefaultCallRepository.snapshot(): CallSnapshot = observeCall().first()

    /**
     * 20ms 한 틱을 진행한다.
     *
     * `advanceTimeBy` 는 경계 시각에 예약된 작업을 실행하지 않으므로 `runCurrent` 가 필요하다.
     */
    private fun TestScope.tick(count: Int = 1) {
        repeat(count) {
            advanceTimeBy(FRAME_MS)
            runCurrent()
        }
    }

    @Test
    fun `start 하면 Connecting 이 되고 마이크 프레임이 RTP 로 나간다`() = runTest {
        val repository = repository()
        devices.capture.pendingFrames = 3

        repository.start(peer, withVideo = false)
        runCurrent()

        val sent = transport.sentPackets()
        assertEquals(CallStage.Connecting, repository.snapshot().stage)
        assertEquals(3, sent.size)
        assertEquals(Payload.PCMU.payloadType, sent[0].payloadType)
        // 순번은 1씩, 타임스탬프는 한 프레임(160샘플)씩 오른다.
        assertEquals((sent[0].sequenceNumber + 1) and RtpPacket.MAX_SEQUENCE, sent[1].sequenceNumber)
        assertEquals(
            (sent[0].timestamp + G711Codec.SAMPLES_PER_FRAME) and MAX_UINT32,
            sent[1].timestamp,
        )
        // 첫 패킷은 talkspurt 의 시작이므로 marker 가 서고, 이어지는 패킷은 서지 않는다.
        assertTrue(sent[0].marker)
        assertFalse(sent[1].marker)
        // 페이로드는 20ms 프레임 하나 분량의 μ-law 다.
        assertEquals(G711Codec.BYTES_PER_FRAME, sent[0].payload.size)
        assertEquals(peer.host, transport.lastHost)
        assertEquals(peer.audioPort, transport.lastPort)
    }

    @Test
    fun `상대 패킷이 도착하면 Active 로 넘어가고 지터버퍼가 찬 뒤 재생된다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()

        // 깊이(3)를 채워야 꺼내기 시작한다.
        repeat(4) { index -> transport.enqueue(remotePacket(sequence = 100 + index)) }
        tick()

        val snapshot = repository.snapshot()
        assertEquals(CallStage.Active, snapshot.stage)
        assertEquals(1, devices.playback.voiceFrames().size)
        assertEquals(G711Codec.PCM_BYTES_PER_FRAME, devices.playback.written.first().size)
    }

    @Test
    fun `상대 패킷이 끊기면 수신 타임아웃으로 끝난다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()

        advanceTimeBy(MediaSession.RECEIVE_TIMEOUT_MS)
        runCurrent()

        val snapshot = repository.snapshot()
        assertEquals(CallStage.Ended, snapshot.stage)
        assertEquals(CallEndReason.ReceiveTimeout, snapshot.endReason)
        // 장치와 오디오 모드는 루프가 스스로 정리한다.
        assertTrue(devices.capture.closed)
        assertTrue(devices.playback.closed)
        assertEquals(0, devices.callModeDepth)
    }

    @Test
    fun `타임아웃 직전까지는 통화를 유지한다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()

        // 한도보다 딱 한 틱 적게 돌린다. advanceTimeBy 는 경계 시각에 예약된 작업을
        // 실행하지 않으므로, 여기에 runCurrent 를 붙이면 그 마지막 틱까지 돌아 통화가 끊긴다.
        advanceTimeBy(MediaSession.RECEIVE_TIMEOUT_MS - FRAME_MS)

        assertEquals(CallStage.Connecting, repository.snapshot().stage)
    }

    @Test
    fun `음소거 중에는 보내지 않고 해제 후 첫 패킷에 marker 가 붙는다`() = runTest {
        val repository = repository()
        devices.capture.pendingFrames = 1
        repository.start(peer, withVideo = false)
        runCurrent()
        val first = transport.sentPackets().single()

        repository.setMicMuted(true)
        devices.capture.pendingFrames = 2
        tick()

        assertEquals(1, transport.sent.size)
        assertTrue(repository.snapshot().micMuted)

        repository.setMicMuted(false)
        devices.capture.pendingFrames = 1
        tick()

        val resumed = transport.sentPackets().last()
        assertEquals(2, transport.sent.size)
        // 순번은 '보낸' 패킷에만 붙으므로 바로 다음 값이다.
        assertEquals((first.sequenceNumber + 1) and RtpPacket.MAX_SEQUENCE, resumed.sequenceNumber)
        // 타임스탬프는 음소거 동안에도 흘렀다 — 세 프레임 뒤 값이어야 상대의 재생 위치가 맞는다.
        assertEquals(
            (first.timestamp + 3 * G711Codec.SAMPLES_PER_FRAME) and MAX_UINT32,
            resumed.timestamp,
        )
        assertTrue(resumed.marker)
    }

    @Test
    fun `음소거가 길어지면 무음 keepalive 로 상대의 수신 타임아웃을 막는다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()
        repository.setMicMuted(true)

        // keepalive 간격만큼 마이크 프레임이 흘렀다(= 2초).
        devices.capture.pendingFrames = KEEPALIVE_TICKS
        tick()

        val sent = transport.sentPackets()
        assertEquals(1, sent.size)
        // 보낸 것은 무음 한 프레임이다 — μ-law 로 부호화한 0 샘플.
        assertEquals(G711Codec.BYTES_PER_FRAME, sent.single().payload.size)
        assertTrue(sent.single().payload.all { it == G711Codec.encodeSample(0).toByte() })
        assertTrue(repository.snapshot().inProgress)
    }

    @Test
    fun `깨진 패킷은 통화 성립 근거가 되지 않는다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()

        transport.enqueueRaw(ByteArray(4) { 0x7F }) // 고정 헤더도 못 채운 길이
        transport.enqueueRaw(ByteArray(20)) // 버전 비트가 2가 아니다
        tick()

        assertEquals(CallStage.Connecting, repository.snapshot().stage)
        assertTrue(devices.playback.written.isEmpty())
    }

    @Test
    fun `모르는 SSRC 는 지터버퍼에 넣지 않아 통계가 오염되지 않는다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()

        repeat(3) { transport.enqueue(remotePacket(sequence = 10 + it, ssrc = REMOTE_SSRC)) }
        // 같은 포트로 끼어든 다른 소스. 받아 주면 순번이 490쯤 뛰어 유실로 잡힌다.
        repeat(3) { transport.enqueue(remotePacket(sequence = 500 + it, ssrc = OTHER_SSRC)) }

        tick(STATS_PUSH_TICKS)

        val stats = repository.snapshot().audioStats
        assertEquals(3L, stats.received)
        assertEquals(3L, stats.expected)
        assertEquals(0L, stats.lost)
        assertEquals(0.0, stats.lossPercent, 0.0)
    }

    @Test
    fun `시작 전에 쌓여 있던 패킷은 통화에 섞이지 않는다`() = runTest {
        val repository = repository()
        repeat(5) { transport.enqueue(remotePacket(sequence = 900 + it)) }

        repository.start(peer, withVideo = false)
        runCurrent()

        assertEquals(CallStage.Connecting, repository.snapshot().stage)
        assertTrue(devices.playback.written.isEmpty())
    }

    @Test
    fun `전송이 실패하면 TransportError 로 끝난다`() = runTest {
        val repository = repository()
        transport.sendError = IOException("네트워크에 닿지 않는다")
        devices.capture.pendingFrames = 1

        repository.start(peer, withVideo = false)
        runCurrent()

        val snapshot = repository.snapshot()
        assertEquals(CallStage.Ended, snapshot.stage)
        assertEquals(CallEndReason.TransportError, snapshot.endReason)
        assertTrue(devices.capture.closed)
        assertEquals(0, devices.callModeDepth)
    }

    @Test
    fun `마이크가 죽으면 TransportError 로 끝난다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()

        devices.capture.failRead = true
        tick()

        assertEquals(CallEndReason.TransportError, repository.snapshot().endReason)
    }

    @Test
    fun `마이크를 열지 못하면 PermissionDenied 로 끝난다`() = runTest {
        val repository = repository()
        devices.capture.startResult = false

        repository.start(peer, withVideo = false)

        val snapshot = repository.snapshot()
        assertEquals(CallStage.Ended, snapshot.stage)
        assertEquals(CallEndReason.PermissionDenied, snapshot.endReason)
        assertTrue(devices.capture.closed)
        // 마이크가 없으면 스피커는 열지 않는다.
        assertFalse(devices.playback.started)
        assertEquals(0, devices.callModeDepth)
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun `스피커를 열지 못하면 마이크까지 되돌린다`() = runTest {
        val repository = repository()
        devices.playback.startResult = false

        repository.start(peer, withVideo = false)

        assertEquals(CallEndReason.TransportError, repository.snapshot().endReason)
        assertTrue(devices.capture.closed)
        assertTrue(devices.playback.closed)
        assertEquals(0, devices.callModeDepth)
    }

    @Test
    fun `소켓을 열지 못하면 통화가 시작되지 않는다`() = runTest {
        val repository = repository(FakeRtpTransportFactory(failToOpen = true))

        repository.start(peer, withVideo = false)

        assertEquals(CallEndReason.TransportError, repository.snapshot().endReason)
        assertEquals(emptyList<MediaSession>(), repository.localSessions())
        assertEquals("", repository.localSdp("192.168.0.5"))
        // 장치는 아예 열지 않는다.
        assertFalse(devices.capture.started)
        assertEquals(0, devices.enterCount)
    }

    @Test
    fun `end 는 통화를 끊고 장치를 되돌리지만 통로는 열어 둔다`() = runTest {
        val repository = repository()
        devices.capture.pendingFrames = 1
        repository.start(peer, withVideo = false)
        runCurrent()

        repository.end(CallEndReason.LocalHangup)

        val snapshot = repository.snapshot()
        assertEquals(CallStage.Ended, snapshot.stage)
        assertEquals(CallEndReason.LocalHangup, snapshot.endReason)
        assertTrue(devices.capture.closed)
        assertTrue(devices.playback.closed)
        assertEquals(0, devices.callModeDepth)
        // 포트가 바뀌면 이미 상대에게 건네준 SDP 가 거짓이 된다.
        assertFalse(transport.closed)
        assertEquals(transport.localPort, repository.localSessions().single().localPort)
    }

    @Test
    fun `스스로 끝난 뒤의 end 는 종료 이유를 덮어쓰지 않는다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()
        advanceTimeBy(MediaSession.RECEIVE_TIMEOUT_MS)
        runCurrent()

        repository.end(CallEndReason.LocalHangup)

        assertEquals(CallEndReason.ReceiveTimeout, repository.snapshot().endReason)
    }

    @Test
    fun `통화 중 두 번째 start 는 무시한다`() = runTest {
        val repository = repository()
        repository.start(peer, withVideo = false)
        runCurrent()

        repository.start(peer.copy(host = "10.0.0.2"), withVideo = false)

        assertEquals(peer.host, repository.snapshot().peer?.host)
        // 마이크를 두 번 열지 않았다는 증거.
        assertEquals(1, devices.enterCount)
    }

    @Test
    fun `영상 플래그는 통화 중에만 바뀐다`() = runTest {
        val repository = repository()

        repository.setVideoEnabled(true)
        assertFalse(repository.snapshot().videoEnabled)

        repository.start(peer, withVideo = true)
        runCurrent()
        assertTrue(repository.snapshot().videoEnabled)

        repository.setVideoEnabled(false)
        assertFalse(repository.snapshot().videoEnabled)
    }

    @Test
    fun `localSdp 는 실제로 열린 포트를 담고 다시 해석된다`() = runTest {
        val repository = repository()

        val sessions = repository.localSessions()
        val sdp = repository.localSdp("192.168.0.5")
        val parsed = repository.parseRemoteSdp(sdp).getOrThrow()

        assertEquals(1, sessions.size)
        assertEquals(Payload.PCMU, sessions.single().payload)
        assertEquals(transport.localPort, sessions.single().localPort)
        assertEquals("192.168.0.5", parsed.host)
        assertEquals(transport.localPort, parsed.audioPort)
        // 영상은 아직 열지 않으므로 m=video 줄이 없다.
        assertNull(parsed.videoPort)
    }

    @Test
    fun `잘못된 SDP 는 실패로 돌아온다`() = runTest {
        val repository = repository()

        assertTrue(repository.parseRemoteSdp("이건 SDP 가 아니다").isFailure)
    }

    private fun remotePacket(sequence: Int, ssrc: Long = REMOTE_SSRC) = RtpPacket(
        payloadType = Payload.PCMU.payloadType,
        sequenceNumber = sequence,
        timestamp = sequence.toLong() * G711Codec.SAMPLES_PER_FRAME,
        ssrc = ssrc,
        marker = false,
        payload = ByteArray(G711Codec.BYTES_PER_FRAME) { 0x11 },
    )

    private companion object {
        const val FRAME_MS = 20L
        const val MAX_UINT32 = 0xFFFF_FFFFL
        const val REMOTE_SSRC = 0x1111_1111L
        const val OTHER_SSRC = 0x2222_2222L

        /** 저장소가 통계를 스냅샷에 싣는 주기(500ms). */
        const val STATS_PUSH_TICKS = 25

        /** 음소거 중 무음 keepalive 를 보내는 주기(2초). */
        const val KEEPALIVE_TICKS = 100
    }
}
