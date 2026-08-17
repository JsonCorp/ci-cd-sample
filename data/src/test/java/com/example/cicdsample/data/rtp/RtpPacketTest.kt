package com.example.cicdsample.data.rtp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RTP 고정 헤더 직렬화·파싱 (RFC 3550 §5.1).
 *
 * 소켓도 코덱도 없이 도는 순수 JVM 테스트다. 헤더 비트를 한 칸 잘못 넣는 실수는
 * 실기기에서는 "소리가 안 나온다" 로만 나타나 원인을 찾기 어렵다 — 여기서 잡는다.
 */
class RtpPacketTest {

    private fun packet(
        payloadType: Int = 0,
        seq: Int = 1,
        timestamp: Long = 160,
        ssrc: Long = 0x1234_5678L,
        marker: Boolean = false,
        payload: ByteArray = byteArrayOf(1, 2, 3),
    ) = RtpPacket(payloadType, seq, timestamp, ssrc, marker, payload)

    @Test
    fun `헤더는 12바이트다`() {
        val bytes = packet(payload = ByteArray(0)).toBytes()

        assertEquals(RtpPacket.HEADER_SIZE, bytes.size)
    }

    @Test
    fun `버전은 2로 고정된다`() {
        val bytes = packet().toBytes()

        assertEquals(2, (bytes[0].toInt() and 0xC0) ushr 6)
        // 패딩·확장·CSRC 는 쓰지 않는다.
        assertEquals(0, bytes[0].toInt() and 0x3F)
    }

    @Test
    fun `직렬화한 뒤 다시 읽으면 원본과 같다`() {
        val original = packet(
            payloadType = 96,
            seq = 40_000,
            timestamp = 3_000_000_000L, // 32비트 부호 있는 범위를 넘는 값
            ssrc = 0xFFFF_FFFFL,
            marker = true,
            payload = byteArrayOf(-1, 0, 127, -128, 42),
        )

        val restored = RtpPacket.parse(original.toBytes())

        assertEquals(original, restored)
    }

    @Test
    fun `마커 비트가 페이로드 타입을 오염시키지 않는다`() {
        // PT 는 7비트, 마커는 8번째 비트다. 같은 바이트를 쓰므로 섞이기 쉽다.
        val marked = RtpPacket.parse(packet(payloadType = 96, marker = true).toBytes())
        val plain = RtpPacket.parse(packet(payloadType = 96, marker = false).toBytes())

        assertEquals(96, marked?.payloadType)
        assertEquals(96, plain?.payloadType)
        assertEquals(true, marked?.marker)
        assertEquals(false, plain?.marker)
    }

    @Test
    fun `타임스탬프와 SSRC 는 32비트 부호 없는 값을 온전히 담는다`() {
        // Int 로 다뤘다면 음수로 뒤집혔을 값들이다.
        val restored = RtpPacket.parse(
            packet(timestamp = 0xFFFF_FFFFL, ssrc = 0xDEAD_BEEFL).toBytes(),
        )

        assertEquals(0xFFFF_FFFFL, restored?.timestamp)
        assertEquals(0xDEAD_BEEFL, restored?.ssrc)
    }

    @Test
    fun `헤더는 빅엔디안으로 쓴다`() {
        // RTP 는 네트워크 바이트 순서를 쓴다. 리틀엔디안으로 쓰면 상대가 못 읽는다.
        val bytes = packet(seq = 0x0102, timestamp = 0x0304_0506L, ssrc = 0x0708_090AL).toBytes()

        assertEquals(0x01, bytes[2].toInt() and 0xFF)
        assertEquals(0x02, bytes[3].toInt() and 0xFF)
        assertEquals(0x03, bytes[4].toInt() and 0xFF)
        assertEquals(0x06, bytes[7].toInt() and 0xFF)
        assertEquals(0x07, bytes[8].toInt() and 0xFF)
        assertEquals(0x0A, bytes[11].toInt() and 0xFF)
    }

    // ── 잘못된 입력 방어 ──────────────────────────────────────────────
    // 소켓으로 오는 값은 신뢰할 수 없다. 예외를 던지면 수신 루프가 죽어 통화가 끊긴다.

    @Test
    fun `헤더보다 짧은 입력은 null 을 낸다`() {
        assertNull(RtpPacket.parse(ByteArray(11)))
        assertNull(RtpPacket.parse(ByteArray(0)))
    }

    @Test
    fun `버전이 2가 아니면 null 을 낸다`() {
        val bytes = packet().toBytes()
        bytes[0] = (bytes[0].toInt() and 0x3F).toByte() // 버전 0

        assertNull(RtpPacket.parse(bytes))
    }

    @Test
    fun `length 가 버퍼보다 크면 null 을 낸다`() {
        assertNull(RtpPacket.parse(ByteArray(20), length = 100))
    }

    @Test
    fun `소켓 버퍼가 실제 수신량보다 커도 length 만큼만 읽는다`() {
        val real = packet(payload = byteArrayOf(9, 9)).toBytes()
        val socketBuffer = ByteArray(2048)
        real.copyInto(socketBuffer)

        val parsed = RtpPacket.parse(socketBuffer, length = real.size)

        assertArrayEquals(byteArrayOf(9, 9), parsed?.payload)
    }

    @Test
    fun `CSRC 목록이 붙어 있으면 건너뛰고 페이로드를 찾는다`() {
        // 이 앱은 CSRC 를 만들지 않지만 상대가 붙여 보낼 수 있다.
        val payload = byteArrayOf(7, 7, 7)
        val bytes = ByteArray(RtpPacket.HEADER_SIZE + 8 + payload.size)
        bytes[0] = ((2 shl 6) or 2).toByte() // 버전 2, CSRC 2개
        bytes[1] = 0
        payload.copyInto(bytes, RtpPacket.HEADER_SIZE + 8)

        val parsed = RtpPacket.parse(bytes)

        assertArrayEquals(payload, parsed?.payload)
    }

    @Test
    fun `패딩이 있으면 페이로드에서 제거한다`() {
        val payload = byteArrayOf(1, 2, 3)
        val padLength = 4
        val bytes = ByteArray(RtpPacket.HEADER_SIZE + payload.size + padLength)
        bytes[0] = ((2 shl 6) or 0x20).toByte() // 버전 2, 패딩 있음
        payload.copyInto(bytes, RtpPacket.HEADER_SIZE)
        bytes[bytes.size - 1] = padLength.toByte()

        val parsed = RtpPacket.parse(bytes)

        assertArrayEquals(payload, parsed?.payload)
    }

    @Test
    fun `패딩 길이가 남은 바이트보다 크면 null 을 낸다`() {
        val bytes = ByteArray(RtpPacket.HEADER_SIZE + 2)
        bytes[0] = ((2 shl 6) or 0x20).toByte()
        bytes[bytes.size - 1] = 99 // 있을 수 없는 패딩 길이

        assertNull(RtpPacket.parse(bytes))
    }

    @Test
    fun `범위를 벗어난 값으로는 만들 수 없다`() {
        // 잘못된 패킷을 만들어 내보내는 것보다 만드는 자리에서 죽는 편이 낫다.
        listOf<() -> Unit>(
            { packet(payloadType = 128) },
            { packet(payloadType = -1) },
            { packet(seq = 65_536) },
            { packet(timestamp = 0x1_0000_0000L) },
            { packet(ssrc = -1L) },
        ).forEach { build ->
            runCatching { build() }.also {
                assertTrue("범위를 벗어난 값이 통과했다", it.isFailure)
            }
        }
    }

    // ── 순번 순환 비교 (RFC 1982) ─────────────────────────────────────

    @Test
    fun `isNewer 는 보통 구간에서 크기 비교와 같다`() {
        assertTrue(RtpPacket.isNewer(100, 99))
        assertFalse(RtpPacket.isNewer(99, 100))
        assertFalse(RtpPacket.isNewer(100, 100))
    }

    @Test
    fun `isNewer 는 랩어라운드에서도 옳다`() {
        // 65535 다음은 0 이다. 단순 크기 비교라면 여기서 순서가 뒤집힌다.
        assertTrue(RtpPacket.isNewer(0, 65_535))
        assertTrue(RtpPacket.isNewer(2, 65_534))
        assertFalse(RtpPacket.isNewer(65_535, 0))
        assertFalse(RtpPacket.isNewer(65_534, 2))
    }

    @Test
    fun `sequenceDistance 는 랩어라운드를 넘어 세어 준다`() {
        assertEquals(1, RtpPacket.sequenceDistance(0, 65_535))
        // 65534 → 65535 → 0 → 1 → 2 이므로 4 걸음이다.
        assertEquals(4, RtpPacket.sequenceDistance(2, 65_534))
        assertEquals(0, RtpPacket.sequenceDistance(7, 7))
        assertEquals(100, RtpPacket.sequenceDistance(200, 100))
    }

    @Test
    fun `순번 0 과 최댓값도 정상 패킷이다`() {
        assertNotNull(RtpPacket.parse(packet(seq = 0).toBytes()))
        assertNotNull(RtpPacket.parse(packet(seq = RtpPacket.MAX_SEQUENCE).toBytes()))
    }
}
