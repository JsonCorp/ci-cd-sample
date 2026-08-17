package com.example.cicdsample.data.rtp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 지터 버퍼. UDP 가 뒤섞어 보낸 패킷을 순번대로 다시 세운다.
 *
 * 실기기에서 이 로직이 틀리면 "소리가 지글거린다" 로만 나타나 원인을 짚기 어렵다.
 * 순서 뒤바뀜·중복·유실·랩어라운드를 여기서 결정적으로 확인한다.
 */
class JitterBufferTest {

    private fun packet(seq: Int, timestamp: Long = seq * 160L) = RtpPacket(
        payloadType = 0,
        sequenceNumber = seq,
        timestamp = timestamp,
        ssrc = 1L,
        marker = false,
        payload = byteArrayOf(seq.toByte()),
    )

    /** 도착 시각은 지터 계산에만 쓰이므로 순서 검증에서는 균일하게 준다. */
    private fun JitterBuffer.put(seq: Int) = offer(packet(seq), arrivalTicks = seq * 160L)

    private fun JitterBuffer.drain(): List<Int> {
        val out = ArrayList<Int>()
        while (true) {
            val next = poll() ?: break
            out += next.sequenceNumber
        }
        return out
    }

    @Test
    fun `깊이가 찰 때까지는 꺼내지 않는다`() {
        // 바로 꺼내 버리면 순서 뒤바뀜을 흡수할 여유가 없다.
        val buffer = JitterBuffer(targetDepth = 3)

        buffer.put(1)
        assertNull(buffer.poll())
        buffer.put(2)
        assertNull(buffer.poll())

        buffer.put(3)
        assertEquals(1, buffer.poll()?.sequenceNumber)
    }

    @Test
    fun `깊이만큼은 항상 버퍼에 남겨 둔다`() {
        // 이게 지터 버퍼의 존재 이유다. 다 꺼내 버리면 다음 패킷이 늦을 때 재생이 끊긴다.
        // 깊이 3 에 패킷 5개를 넣으면 3개만 나오고 2개는 여유분으로 남는다.
        val buffer = JitterBuffer(targetDepth = 3)

        (1..5).forEach { buffer.put(it) }

        assertEquals(listOf(1, 2, 3), buffer.drain())

        // 뒤늦게 더 들어오면 다시 꺼낼 수 있다.
        buffer.put(6)
        assertEquals(listOf(4), buffer.drain())
    }

    @Test
    fun `순서대로 온 패킷은 순서대로 나온다`() {
        val buffer = JitterBuffer(targetDepth = 1)

        (1..5).forEach { buffer.put(it) }

        assertEquals(listOf(1, 2, 3, 4, 5), buffer.drain())
    }

    @Test
    fun `순서가 뒤바뀐 패킷을 제자리에 세운다`() {
        // 깊이 게이트와 섞이지 않게 1 로 둔다 — 넣은 뒤 꺼내므로 재정렬은 그대로 검증된다.
        val buffer = JitterBuffer(targetDepth = 1)

        // 3 이 2 보다 먼저 도착했다.
        buffer.put(1)
        buffer.put(3)
        buffer.put(2)
        buffer.put(4)

        assertEquals(listOf(1, 2, 3, 4), buffer.drain())
        assertEquals(1L, buffer.stats().outOfOrder)
    }

    @Test
    fun `중복 패킷은 한 번만 받아들인다`() {
        val buffer = JitterBuffer(targetDepth = 2)

        assertTrue(buffer.put(1))
        assertFalse(buffer.put(1))
        assertTrue(buffer.put(2))

        assertEquals(1L, buffer.stats().duplicated)
        assertEquals(listOf(1), buffer.drain())
    }

    @Test
    fun `없는 순번은 유실로 보고 건너뛴다`() {
        // 오지 않는 패킷을 계속 기다리면 통화가 그 자리에서 멈춘다.
        val buffer = JitterBuffer(targetDepth = 1)

        buffer.put(1)
        buffer.put(3) // 2 는 오지 않는다
        buffer.put(4)
        buffer.put(5)

        assertEquals(listOf(1, 3, 4, 5), buffer.drain())
    }

    @Test
    fun `이미 지나간 순번이 늦게 오면 버린다`() {
        val buffer = JitterBuffer(targetDepth = 2)

        buffer.put(5)
        buffer.put(6)
        buffer.poll() // 5 를 꺼냈다 — 이제 5 는 지나갔다

        assertFalse("이미 재생한 순번은 살릴 수 없다", buffer.put(5))
        assertTrue(buffer.stats().lost >= 1)
    }

    @Test
    fun `랩어라운드를 넘어가도 순서가 유지된다`() {
        val buffer = JitterBuffer(targetDepth = 1)

        buffer.put(65_534)
        buffer.put(65_535)
        buffer.put(0)
        buffer.put(1)

        assertEquals(listOf(65_534, 65_535, 0, 1), buffer.drain())
    }

    @Test
    fun `랩어라운드 구간에서 순서가 뒤바뀌어도 세운다`() {
        val buffer = JitterBuffer(targetDepth = 1)

        // 0 이 65535 보다 먼저 도착했다.
        buffer.put(65_534)
        buffer.put(0)
        buffer.put(65_535)
        buffer.put(1)
        buffer.put(2)

        assertEquals(listOf(65_534, 65_535, 0, 1, 2), buffer.drain())
    }

    @Test
    fun `기대 패킷 수는 랩어라운드를 넘어 세어진다`() {
        val buffer = JitterBuffer(targetDepth = 1)

        buffer.put(65_535)
        buffer.put(0)
        buffer.put(1)

        // 65535, 0, 1 → 3개
        assertEquals(3L, buffer.stats().expected)
        assertEquals(3L, buffer.stats().received)
    }

    @Test
    fun `유실률을 계산한다`() {
        val buffer = JitterBuffer(targetDepth = 1)

        buffer.put(1)
        buffer.put(2)
        // 3, 4 유실
        buffer.put(5)

        val stats = buffer.stats()
        assertEquals(5L, stats.expected)
        assertEquals(3L, stats.received)
        assertEquals(2L, stats.lost)
        assertEquals(40.0, stats.lossPercent, 0.01)
    }

    @Test
    fun `패킷이 없으면 유실률은 0 이다`() {
        // 0으로 나누면 NaN 이 화면에 뜬다.
        assertEquals(0.0, JitterBuffer().stats().lossPercent, 0.0)
    }

    @Test
    fun `도착 간격이 일정하면 지터는 0 에 가깝다`() {
        val buffer = JitterBuffer(targetDepth = 1, clockRate = 8_000)

        // 타임스탬프와 도착 시각이 같은 폭으로 증가 = 지터 없음
        (1..10).forEach { buffer.offer(packet(it, timestamp = it * 160L), arrivalTicks = it * 160L) }

        assertEquals(0.0, buffer.stats().jitterMs, 0.01)
    }

    @Test
    fun `도착이 들쭉날쭉하면 지터가 커진다`() {
        val buffer = JitterBuffer(targetDepth = 1, clockRate = 8_000)

        // 도착 간격이 160, 400, 160, 400 … 으로 흔들린다.
        var arrival = 0L
        (1..10).forEach { i ->
            arrival += if (i % 2 == 0) 400L else 160L
            buffer.offer(packet(i, timestamp = i * 160L), arrivalTicks = arrival)
        }

        assertTrue("흔들리는데 지터가 0 이다", buffer.stats().jitterMs > 1.0)
    }

    @Test
    fun `clear 는 버퍼만 비우고 통계는 남긴다`() {
        val buffer = JitterBuffer(targetDepth = 2)
        buffer.put(1)
        buffer.put(1) // 중복
        buffer.put(2)

        buffer.clear()

        assertNull(buffer.poll())
        assertEquals("통화 중 누적 지표는 유지돼야 한다", 1L, buffer.stats().duplicated)
    }

    @Test
    fun `잘못된 생성 인자는 거부한다`() {
        listOf<() -> Unit>(
            { JitterBuffer(targetDepth = 0) },
            { JitterBuffer(clockRate = 0) },
        ).forEach { build ->
            assertTrue(runCatching { build() }.isFailure)
        }
    }
}
