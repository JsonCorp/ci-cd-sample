package com.example.cicdsample.data.net

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject

/**
 * RTP 를 실어 보내는 통로.
 *
 * 인터페이스로 떼어 둔 이유는 하나다 — 이게 있어야
 * [com.example.cicdsample.data.call.DefaultCallRepository] 의 상태 전이와 수신 타임아웃 판정을
 * **소켓 없이 단위 테스트**할 수 있다. 통화 상태 머신은 실기기에서 재현하기 가장 번거로운 부분이다.
 */
interface RtpTransport : Closeable {

    /** 실제로 바인딩된 로컬 포트. 0 을 요청했으면 OS 가 고른 값이 들어온다. */
    val localPort: Int

    fun send(bytes: ByteArray, length: Int, host: String, port: Int)

    /**
     * 패킷 하나를 받는다.
     *
     * @return 받은 바이트 수. 타임아웃이면 **-1** 을 준다 —
     *   예외로 만들면 정상 흐름인 "지금은 조용하다" 를 매번 예외로 다루게 된다.
     */
    fun receive(buffer: ByteArray, timeoutMs: Int): Int
}

/** 포트별로 통로를 연다. 테스트에서는 페이크 팩토리를 꽂는다. */
interface RtpTransportFactory {
    /** @param localPort 0 이면 OS 가 빈 포트를 고른다. */
    fun open(localPort: Int): RtpTransport
}

/** UDP 소켓 구현. */
class UdpRtpTransport(localPort: Int) : RtpTransport {

    private val socket = DatagramSocket(localPort)

    override val localPort: Int get() = socket.localPort

    /**
     * 주소 해석 결과를 캐시한다. 20ms 마다 [InetAddress.getByName] 을 부르면
     * 호스트명일 때 매 패킷마다 DNS 를 두드린다.
     */
    private var cachedHost: String? = null
    private var cachedAddress: InetAddress? = null

    override fun send(bytes: ByteArray, length: Int, host: String, port: Int) {
        val address = resolve(host)
        socket.send(DatagramPacket(bytes, length, address, port))
    }

    override fun receive(buffer: ByteArray, timeoutMs: Int): Int {
        socket.soTimeout = timeoutMs
        val packet = DatagramPacket(buffer, buffer.size)
        return try {
            socket.receive(packet)
            packet.length
        } catch (_: SocketTimeoutException) {
            // 조용한 것은 오류가 아니다. 호출부가 타임아웃 누적을 판단한다.
            -1
        }
    }

    override fun close() {
        socket.close()
    }

    private fun resolve(host: String): InetAddress {
        cachedAddress?.takeIf { cachedHost == host }?.let { return it }
        val resolved = InetAddress.getByName(host)
        cachedHost = host
        cachedAddress = resolved
        return resolved
    }
}

class UdpRtpTransportFactory @Inject constructor() : RtpTransportFactory {
    override fun open(localPort: Int): RtpTransport = UdpRtpTransport(localPort)
}
