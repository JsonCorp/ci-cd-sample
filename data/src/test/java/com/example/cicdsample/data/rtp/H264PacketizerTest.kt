package com.example.cicdsample.data.rtp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H.264 FU-A 쪼개기·재조립 (RFC 6184).
 *
 * 키프레임 NAL 은 수십 KB 라 반드시 쪼개진다. 재조립이 틀리면 영상이 깨지거나
 * 아예 안 나오는데, 실기기에서는 원인이 카메라인지 코덱인지 네트워크인지 구분되지 않는다.
 * 카메라도 코덱도 없이 여기서 왕복을 확인한다.
 */
class H264PacketizerTest {

    /** NAL 헤더 한 바이트를 만든다. F=0, NRI, type. */
    private fun nalHeader(nri: Int, type: Int): Byte = ((nri shl 5) or type).toByte()

    private fun nal(type: Int, bodySize: Int, nri: Int = 3): ByteArray =
        ByteArray(bodySize + 1) { index ->
            if (index == 0) nalHeader(nri, type) else (index % 251).toByte()
        }

    // ── 쪼개기 ────────────────────────────────────────────────────────

    @Test
    fun `MTU 에 들어가면 쪼개지 않는다`() {
        val small = nal(type = 1, bodySize = 100)

        val packets = H264Packetizer.packetize(small, maxPayloadSize = 1_200)

        assertEquals(1, packets.size)
        assertArrayEquals("쪼개지 않을 때는 원본 그대로다", small, packets[0])
    }

    @Test
    fun `경계값에서 정확히 갈린다`() {
        val exact = nal(type = 1, bodySize = 99) // 헤더 포함 100바이트

        assertEquals(1, H264Packetizer.packetize(exact, maxPayloadSize = 100).size)
        assertTrue(H264Packetizer.packetize(exact, maxPayloadSize = 99).size > 1)
    }

    @Test
    fun `MTU 를 넘으면 FU-A 로 쪼갠다`() {
        val large = nal(type = 5, bodySize = 3_000) // IDR 프레임

        val packets = H264Packetizer.packetize(large, maxPayloadSize = 1_200)

        assertTrue("쪼개지지 않았다", packets.size > 1)
        packets.forEach { packet ->
            assertTrue("조각이 MTU 를 넘는다: ${packet.size}", packet.size <= 1_200)
            assertEquals("모든 조각은 FU-A 타입이다", H264Packetizer.FU_A_TYPE, packet[0].toInt() and 0x1F)
        }
    }

    @Test
    fun `첫 조각에만 S 비트, 마지막 조각에만 E 비트가 선다`() {
        val packets = H264Packetizer.packetize(nal(type = 5, bodySize = 3_000), maxPayloadSize = 1_200)

        packets.forEachIndexed { index, packet ->
            val start = (packet[1].toInt() and 0x80) != 0
            val end = (packet[1].toInt() and 0x40) != 0

            assertEquals("조각 $index 의 S 비트", index == 0, start)
            assertEquals("조각 $index 의 E 비트", index == packets.lastIndex, end)
        }
    }

    @Test
    fun `조각은 원본 NAL 타입과 NRI 를 보존한다`() {
        val packets = H264Packetizer.packetize(nal(type = 5, bodySize = 3_000, nri = 3), maxPayloadSize = 500)

        packets.forEach { packet ->
            // FU indicator 의 NRI 는 원본과 같아야 한다.
            assertEquals(3, (packet[0].toInt() and 0x60) ushr 5)
            // FU header 의 하위 5비트가 원본 NAL 타입이다.
            assertEquals(5, packet[1].toInt() and 0x1F)
        }
    }

    @Test
    fun `빈 입력은 빈 목록을 낸다`() {
        assertTrue(H264Packetizer.packetize(ByteArray(0)).isEmpty())
    }

    @Test
    fun `MTU 가 헤더보다 작으면 거부한다`() {
        assertTrue(runCatching { H264Packetizer.packetize(nal(1, 100), maxPayloadSize = 2) }.isFailure)
    }

    // ── 왕복 ──────────────────────────────────────────────────────────

    @Test
    fun `쪼갠 뒤 다시 합치면 원본과 같다`() {
        val original = nal(type = 5, bodySize = 5_000)
        val reassembler = H264Packetizer.Reassembler()

        var completed: ByteArray? = null
        H264Packetizer.packetize(original, maxPayloadSize = 1_200).forEach { fragment ->
            reassembler.offer(fragment)?.let { completed = it }
        }

        assertArrayEquals(original, completed)
    }

    @Test
    fun `여러 크기에서 왕복이 성립한다`() {
        // 조각 수가 1개, 딱 2개, 나누어떨어지는 경우, 남는 경우를 모두 지난다.
        listOf(50, 199, 200, 201, 1_000, 4_096, 65_000).forEach { size ->
            val original = nal(type = 1, bodySize = size)
            val reassembler = H264Packetizer.Reassembler()

            var completed: ByteArray? = null
            H264Packetizer.packetize(original, maxPayloadSize = 200).forEach { fragment ->
                reassembler.offer(fragment)?.let { completed = it }
            }

            assertArrayEquals("본문 $size 바이트에서 왕복이 깨졌다", original, completed)
        }
    }

    @Test
    fun `쪼개지지 않은 NAL 도 재조립기를 그대로 통과한다`() {
        val small = nal(type = 7, bodySize = 20) // SPS

        val out = H264Packetizer.Reassembler().offer(small)

        assertArrayEquals(small, out)
    }

    @Test
    fun `마지막 조각 전에는 아무것도 내놓지 않는다`() {
        val fragments = H264Packetizer.packetize(nal(type = 5, bodySize = 3_000), maxPayloadSize = 1_200)
        val reassembler = H264Packetizer.Reassembler()

        fragments.dropLast(1).forEach { fragment ->
            assertNull("완성 전에 내놓았다", reassembler.offer(fragment))
        }
        assertNotNull(reassembler.offer(fragments.last()))
    }

    // ── 깨진 흐름 방어 ────────────────────────────────────────────────

    @Test
    fun `첫 조각을 놓치면 그 프레임을 포기한다`() {
        // 조각 하나만 유실돼도 그 프레임은 살릴 수 없다. 억지로 합치면 디코더가 깨진다.
        val fragments = H264Packetizer.packetize(nal(type = 5, bodySize = 3_000), maxPayloadSize = 1_200)
        val reassembler = H264Packetizer.Reassembler()

        // 첫 조각을 건너뛴다.
        val results = fragments.drop(1).map { reassembler.offer(it) }

        assertTrue("첫 조각 없이 완성품이 나왔다", results.all { it == null })
    }

    @Test
    fun `다른 NAL 의 조각이 섞이면 버린다`() {
        val a = H264Packetizer.packetize(nal(type = 5, bodySize = 3_000), maxPayloadSize = 1_200)
        val b = H264Packetizer.packetize(nal(type = 1, bodySize = 3_000), maxPayloadSize = 1_200)
        val reassembler = H264Packetizer.Reassembler()

        reassembler.offer(a[0]) // type 5 의 첫 조각
        val mixed = reassembler.offer(b[1]) // type 1 의 중간 조각

        assertNull("타입이 다른 조각을 받아들였다", mixed)
    }

    @Test
    fun `reset 후에는 새 프레임을 받는다`() {
        val fragments = H264Packetizer.packetize(nal(type = 5, bodySize = 3_000), maxPayloadSize = 1_200)
        val reassembler = H264Packetizer.Reassembler()

        reassembler.offer(fragments[0])
        reassembler.reset()

        var completed: ByteArray? = null
        fragments.forEach { reassembler.offer(it)?.let { done -> completed = done } }

        assertNotNull("reset 후 재조립이 안 된다", completed)
    }

    @Test
    fun `빈 페이로드와 잘린 FU 헤더는 null 을 낸다`() {
        val reassembler = H264Packetizer.Reassembler()

        assertNull(reassembler.offer(ByteArray(0)))
        // FU indicator 만 있고 FU header 가 없다.
        assertNull(reassembler.offer(byteArrayOf(((3 shl 5) or H264Packetizer.FU_A_TYPE).toByte())))
    }

    // ── Annex B 자르기 ────────────────────────────────────────────────

    @Test
    fun `4바이트 시작코드를 자른다`() {
        val stream = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0, 0, 0, 1, 0x68, 0xCE.toByte())

        val nals = H264Packetizer.splitAnnexB(stream)

        assertEquals(2, nals.size)
        assertArrayEquals(byteArrayOf(0x67, 0x42), nals[0])
        assertArrayEquals(byteArrayOf(0x68, 0xCE.toByte()), nals[1])
    }

    @Test
    fun `3바이트 시작코드를 자른다`() {
        val stream = byteArrayOf(0, 0, 1, 0x67, 0x42, 0, 0, 1, 0x65, 0x11)

        val nals = H264Packetizer.splitAnnexB(stream)

        assertEquals(2, nals.size)
        assertArrayEquals(byteArrayOf(0x67, 0x42), nals[0])
        assertArrayEquals(byteArrayOf(0x65, 0x11), nals[1])
    }

    @Test
    fun `시작코드가 없으면 빈 목록을 낸다`() {
        assertTrue(H264Packetizer.splitAnnexB(byteArrayOf(1, 2, 3, 4)).isEmpty())
    }

    @Test
    fun `length 인자로 부분만 자른다`() {
        // MediaCodec 은 버퍼 전체가 아니라 채운 만큼만 유효하다.
        val stream = ByteArray(100)
        byteArrayOf(0, 0, 0, 1, 0x67, 0x42).copyInto(stream)

        val nals = H264Packetizer.splitAnnexB(stream, length = 6)

        assertEquals(1, nals.size)
        assertArrayEquals(byteArrayOf(0x67, 0x42), nals[0])
    }

    @Test
    fun `Annex B 를 자른 뒤 쪼개고 다시 합치는 전 과정이 성립한다`() {
        // MediaCodec 출력 → RTP 전송 → 수신 → 디코더 입력 까지의 경로 전체다.
        val sps = nal(type = 7, bodySize = 20)
        val idr = nal(type = 5, bodySize = 4_000)
        val stream = ByteArray(4 + sps.size + 4 + idr.size)
        byteArrayOf(0, 0, 0, 1).copyInto(stream, 0)
        sps.copyInto(stream, 4)
        byteArrayOf(0, 0, 0, 1).copyInto(stream, 4 + sps.size)
        idr.copyInto(stream, 8 + sps.size)

        val reassembler = H264Packetizer.Reassembler()
        val restored = ArrayList<ByteArray>()
        H264Packetizer.splitAnnexB(stream).forEach { unit ->
            H264Packetizer.packetize(unit, maxPayloadSize = 1_200).forEach { fragment ->
                reassembler.offer(fragment)?.let { restored += it }
            }
        }

        assertEquals(2, restored.size)
        assertArrayEquals(sps, restored[0])
        assertArrayEquals(idr, restored[1])
    }
}
