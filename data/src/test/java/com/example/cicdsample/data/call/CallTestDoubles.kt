package com.example.cicdsample.data.call

import com.example.cicdsample.data.audio.AudioCapture
import com.example.cicdsample.data.audio.AudioDeviceFactory
import com.example.cicdsample.data.audio.AudioPlayback
import com.example.cicdsample.data.net.RtpTransport
import com.example.cicdsample.data.net.RtpTransportFactory
import com.example.cicdsample.data.rtp.RtpPacket
import java.io.IOException
import java.net.SocketException

/**
 * 소켓 대신 큐를 쓰는 전송 통로.
 *
 * 저장소가 [RtpTransport] 인터페이스에만 의존하는 덕에, 상태 전이·수신 타임아웃·통계를
 * 실제 UDP 없이 확인할 수 있다. 통화 상태 머신은 실기기에서 재현하기 가장 번거로운 부분이다.
 */
class FakeRtpTransport(override val localPort: Int = 5_004) : RtpTransport {

    /** 보낸 패킷의 바이트열. 순서를 그대로 유지한다. */
    val sent = mutableListOf<ByteArray>()

    var lastHost: String? = null
        private set

    var lastPort: Int? = null
        private set

    var closed = false
        private set

    /** 설정하면 [send] 가 이 예외를 던진다. */
    var sendError: IOException? = null

    private val inbound = ArrayDeque<ByteArray>()

    /** 상대가 보낸 패킷을 큐에 넣는다. 다음 수신 드레인에서 읽힌다. */
    fun enqueue(packet: RtpPacket) = enqueueRaw(packet.toBytes())

    /** RTP 로 해석되지 않는 바이트열까지 넣어 볼 수 있다. */
    fun enqueueRaw(bytes: ByteArray) {
        inbound.addLast(bytes)
    }

    fun sentPackets(): List<RtpPacket> = sent.mapNotNull { RtpPacket.parse(it) }

    override fun send(bytes: ByteArray, length: Int, host: String, port: Int) {
        sendError?.let { throw it }
        sent += bytes.copyOf(length)
        lastHost = host
        lastPort = port
    }

    override fun receive(buffer: ByteArray, timeoutMs: Int): Int {
        // 실제 소켓과 같은 규약 — 받을 것이 없으면 예외가 아니라 -1 이다.
        val next = inbound.removeFirstOrNull() ?: return -1
        next.copyInto(buffer)
        return next.size
    }

    override fun close() {
        closed = true
    }
}

/** 언제나 같은 통로를 준다 — 테스트가 보낸 패킷을 그 자리에서 확인할 수 있다. */
class FakeRtpTransportFactory(
    private val transport: RtpTransport = FakeRtpTransport(),
    private val failToOpen: Boolean = false,
) : RtpTransportFactory {

    override fun open(localPort: Int): RtpTransport {
        if (failToOpen) throw SocketException("포트를 열 수 없다")
        return transport
    }
}

/**
 * 정해진 개수만큼만 프레임을 주는 마이크.
 *
 * 다 마르면 0 을 돌려주고, 저장소 루프는 그때 20ms 를 쉰다 —
 * 그래서 테스트가 가상 시간을 20ms 씩 밀며 틱을 셀 수 있다.
 */
class FakeAudioCapture : AudioCapture {

    /** false 면 마이크가 열리지 않는 상황(주로 권한 거부)을 흉내낸다. */
    var startResult = true

    /** 아직 내줄 프레임 수. 테스트가 통화 중에 더 채워 넣을 수 있다. */
    var pendingFrames = 0

    /** true 면 [read] 가 장치 오류(-1)를 낸다. */
    var failRead = false

    var started = false
        private set

    var closed = false
        private set

    /** 프레임마다 값을 1씩 올려 채운다 — 어떤 프레임이 어떤 패킷으로 나갔는지 알 수 있다. */
    private var fill = 1

    override fun start(): Boolean {
        started = startResult
        return startResult
    }

    override fun read(buffer: ByteArray): Int {
        if (failRead) return -1
        if (pendingFrames <= 0) return 0
        pendingFrames--
        buffer.fill(fill++.toByte())
        return buffer.size
    }

    override fun close() {
        closed = true
    }
}

/** 쓴 것을 그대로 모아 두는 스피커. */
class FakeAudioPlayback : AudioPlayback {

    var startResult = true

    var started = false
        private set

    var closed = false
        private set

    val written = mutableListOf<ByteArray>()

    /** 무음으로 메운 프레임은 세지 않는다 — 실제로 재생된 상대 음성만 본다. */
    fun voiceFrames(): List<ByteArray> = written.filter { frame -> frame.any { it != 0.toByte() } }

    override fun start(): Boolean {
        started = startResult
        return startResult
    }

    override fun write(pcm: ByteArray, length: Int) {
        written += pcm.copyOf(length)
    }

    override fun close() {
        closed = true
    }
}

class FakeAudioDeviceFactory(
    val capture: FakeAudioCapture = FakeAudioCapture(),
    val playback: FakeAudioPlayback = FakeAudioPlayback(),
) : AudioDeviceFactory {

    /** enter 와 exit 의 균형을 본다. 통화가 끝났는데 0 이 아니면 모드를 되돌리지 않은 것이다. */
    var callModeDepth = 0
        private set

    var enterCount = 0
        private set

    override fun createCapture(): AudioCapture = capture

    override fun createPlayback(): AudioPlayback = playback

    override fun enterCallMode() {
        enterCount++
        callModeDepth++
    }

    override fun exitCallMode() {
        callModeDepth--
    }
}
