package com.example.cicdsample.data.net

import com.example.cicdsample.domain.model.call.MediaKind
import com.example.cicdsample.domain.model.call.MediaSession
import com.example.cicdsample.domain.model.call.Payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SDP 생성·해석 (RFC 4566).
 *
 * 사람이 이 텍스트를 복사해 옮기는 것이 이 앱의 시그널링 전부다.
 * 한 글자 어긋나면 통화가 아예 시작되지 않으므로 왕복과 방어를 모두 확인한다.
 */
class SdpCodecTest {

    private val codec = SdpCodec()

    private val audioSession = MediaSession(
        kind = MediaKind.AUDIO,
        payload = Payload.PCMU,
        localPort = 5_004,
        ssrc = 0x1234_5678L,
    )

    private val videoSession = MediaSession(
        kind = MediaKind.VIDEO,
        payload = Payload.H264,
        localPort = 5_006,
        ssrc = 0x8765_4321L,
    )

    // ── 생성 ──────────────────────────────────────────────────────────

    @Test
    fun `필수 줄이 순서대로 들어간다`() {
        val sdp = codec.encode(listOf(audioSession), "192.168.0.10")
        val lines = sdp.lines().filter { it.isNotBlank() }

        assertEquals("v=0", lines[0])
        assertTrue("o= 줄이 없다", lines[1].startsWith("o="))
        assertTrue("s= 줄이 없다", lines[2].startsWith("s="))
        assertEquals("c=IN IP4 192.168.0.10", lines[3])
        assertEquals("t=0 0", lines[4])
    }

    @Test
    fun `오디오 미디어 줄과 rtpmap 이 정확하다`() {
        val sdp = codec.encode(listOf(audioSession), "192.168.0.10")

        assertTrue(sdp.contains("m=audio 5004 RTP/AVP 0"))
        // PCMU 는 정적 페이로드 0 이지만 rtpmap 을 함께 적어 두면 해석이 명확해진다.
        assertTrue(sdp.contains("a=rtpmap:0 PCMU/8000"))
        assertTrue(sdp.contains("a=sendrecv"))
    }

    @Test
    fun `영상 세션을 넣으면 m=video 가 붙는다`() {
        val sdp = codec.encode(listOf(audioSession, videoSession), "192.168.0.10")

        assertTrue(sdp.contains("m=video 5006 RTP/AVP 96"))
        assertTrue(sdp.contains("a=rtpmap:96 H264/90000"))
    }

    @Test
    fun `영상 세션이 없으면 m=video 가 없다`() {
        val sdp = codec.encode(listOf(audioSession), "192.168.0.10")

        assertTrue("영상 줄이 새어 나왔다", !sdp.contains("m=video"))
    }

    @Test
    fun `IPv6 주소는 IP6 로 표기한다`() {
        val sdp = codec.encode(listOf(audioSession), "fe80::1")

        assertTrue(sdp.contains("c=IN IP6 fe80::1"))
        assertTrue(sdp.contains("IN IP6"))
    }

    @Test
    fun `오디오 없는 세션 목록은 거부한다`() {
        assertTrue(runCatching { codec.encode(listOf(videoSession), "192.168.0.10") }.isFailure)
        assertTrue(runCatching { codec.encode(emptyList(), "192.168.0.10") }.isFailure)
    }

    // ── 왕복 ──────────────────────────────────────────────────────────

    @Test
    fun `만든 SDP 를 다시 읽으면 주소와 포트가 보존된다`() {
        val sdp = codec.encode(listOf(audioSession, videoSession), "10.0.1.5")

        val peer = codec.decode(sdp).getOrThrow()

        assertEquals("10.0.1.5", peer.host)
        assertEquals(5_004, peer.audioPort)
        assertEquals(5_006, peer.videoPort)
    }

    @Test
    fun `오디오만 있는 SDP 를 왕복하면 영상 포트는 null 이다`() {
        val sdp = codec.encode(listOf(audioSession), "10.0.1.5")

        val peer = codec.decode(sdp).getOrThrow()

        assertNull(peer.videoPort)
    }

    // ── 해석 ──────────────────────────────────────────────────────────

    @Test
    fun `상용 단말이 보낼 만한 SDP 에서 필요한 줄만 골라 읽는다`() {
        // 우리가 쓰지 않는 속성이 잔뜩 붙어 와도 동작해야 한다.
        val foreign = """
            v=0
            o=alice 2890844526 2890844526 IN IP4 host.example.com
            s=SIP Call
            c=IN IP4 203.0.113.7
            b=AS:64
            t=0 0
            m=audio 49170 RTP/AVP 0 8 101
            a=rtpmap:0 PCMU/8000
            a=rtpmap:8 PCMA/8000
            a=rtpmap:101 telephone-event/8000
            a=fmtp:101 0-15
            a=ptime:20
            a=sendrecv
            m=video 51372 RTP/AVP 96
            a=rtpmap:96 H264/90000
            a=fmtp:96 profile-level-id=42e01f
            a=rtcp-mux
        """.trimIndent()

        val peer = codec.decode(foreign).getOrThrow()

        assertEquals("203.0.113.7", peer.host)
        assertEquals(49_170, peer.audioPort)
        assertEquals(51_372, peer.videoPort)
    }

    @Test
    fun `CRLF 줄바꿈과 앞뒤 공백을 견딘다`() {
        // RFC 4566 은 CRLF 를 규정한다. 복사해 붙이는 과정에서 공백이 붙기도 한다.
        val sdp = "v=0\r\n  c=IN IP4 192.168.1.1  \r\nt=0 0\r\nm=audio 6000 RTP/AVP 0\r\n"

        val peer = codec.decode(sdp).getOrThrow()

        assertEquals("192.168.1.1", peer.host)
        assertEquals(6_000, peer.audioPort)
    }

    @Test
    fun `멀티캐스트 TTL 표기를 잘라낸다`() {
        val sdp = "v=0\nc=IN IP4 239.1.1.1/127\nm=audio 6000 RTP/AVP 0\n"

        assertEquals("239.1.1.1", codec.decode(sdp).getOrThrow().host)
    }

    @Test
    fun `포트 0 인 영상 줄은 영상을 끈 것으로 본다`() {
        // RFC 4566 §5.14 — 포트 0 은 그 미디어를 쓰지 않겠다는 뜻이다.
        val sdp = "v=0\nc=IN IP4 192.168.1.1\nm=audio 6000 RTP/AVP 0\nm=video 0 RTP/AVP 96\n"

        val peer = codec.decode(sdp).getOrThrow()

        assertEquals(6_000, peer.audioPort)
        assertNull("포트 0 을 영상 포트로 받아들였다", peer.videoPort)
    }

    @Test
    fun `첫 오디오 줄만 쓴다`() {
        val sdp = """
            v=0
            c=IN IP4 192.168.1.1
            m=audio 6000 RTP/AVP 0
            m=audio 7000 RTP/AVP 8
        """.trimIndent()

        assertEquals(6_000, codec.decode(sdp).getOrThrow().audioPort)
    }

    // ── 방어 ──────────────────────────────────────────────────────────

    private fun assertFails(expected: SdpError, sdp: String) {
        val result = codec.decode(sdp)

        assertTrue("실패해야 하는데 통과했다", result.isFailure)
        assertEquals(expected, (result.exceptionOrNull() as? SdpParseException)?.reason)
    }

    @Test
    fun `빈 입력은 EMPTY`() {
        assertFails(SdpError.EMPTY, "")
        assertFails(SdpError.EMPTY, "   \n\n  ")
    }

    @Test
    fun `c 줄이 없으면 NO_CONNECTION`() {
        assertFails(SdpError.NO_CONNECTION, "v=0\nm=audio 6000 RTP/AVP 0\n")
    }

    @Test
    fun `오디오 줄이 없으면 NO_AUDIO`() {
        assertFails(SdpError.NO_AUDIO, "v=0\nc=IN IP4 192.168.1.1\nm=video 6000 RTP/AVP 96\n")
    }

    @Test
    fun `포트가 숫자가 아니면 BAD_PORT`() {
        assertFails(SdpError.BAD_PORT, "v=0\nc=IN IP4 192.168.1.1\nm=audio abc RTP/AVP 0\n")
    }

    @Test
    fun `오디오 포트가 0 뿐이면 BAD_PORT`() {
        // 오디오를 끈 SDP 로는 이 앱이 통화를 만들 수 없다.
        assertFails(SdpError.BAD_PORT, "v=0\nc=IN IP4 192.168.1.1\nm=audio 0 RTP/AVP 0\n")
    }

    @Test
    fun `포트가 범위를 넘으면 BAD_PORT`() {
        assertFails(SdpError.BAD_PORT, "v=0\nc=IN IP4 192.168.1.1\nm=audio 70000 RTP/AVP 0\n")
    }

    @Test
    fun `c 줄 필드가 모자라면 NO_CONNECTION`() {
        assertFails(SdpError.NO_CONNECTION, "v=0\nc=IN IP4\nm=audio 6000 RTP/AVP 0\n")
    }

    @Test
    fun `m 줄 필드가 모자라면 무시하고 결국 NO_AUDIO`() {
        assertFails(SdpError.NO_AUDIO, "v=0\nc=IN IP4 192.168.1.1\nm=audio\n")
    }
}
