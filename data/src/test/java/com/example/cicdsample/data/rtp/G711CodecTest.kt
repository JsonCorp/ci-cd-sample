package com.example.cicdsample.data.rtp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G.711 μ-law 코덱 (ITU-T G.711).
 *
 * 손실 압축이라 왕복이 완전 일치하지 않는다 — 그래서 "오차 한계 안에 있는가" 로 검증한다.
 * 이 코덱을 고른 이유가 곧 이 테스트가 존재할 수 있는 이유다: 순수 산술이라
 * 마이크도 코덱 하드웨어도 없이 전부 확인된다.
 */
class G711CodecTest {

    @Test
    fun `0 은 0 근처로 왕복한다`() {
        val restored = G711Codec.decodeSample(G711Codec.encodeSample(0))

        assertTrue("0 이 $restored 로 갔다", abs(restored) <= 8)
    }

    @Test
    fun `한 바이트로 압축된다`() {
        listOf(0, 1000, -1000, 32_767, -32_768).forEach { sample ->
            val encoded = G711Codec.encodeSample(sample)
            assertTrue("μ-law 는 0~255 여야 한다: $encoded", encoded in 0..255)
        }
    }

    @Test
    fun `부호가 보존된다`() {
        // 부호 비트를 잘못 다루면 소리가 반전돼 잡음처럼 들린다.
        listOf(100, 1_000, 8_000, 20_000).forEach { magnitude ->
            val positive = G711Codec.decodeSample(G711Codec.encodeSample(magnitude))
            val negative = G711Codec.decodeSample(G711Codec.encodeSample(-magnitude))

            assertTrue("양수가 음수로 갔다: $magnitude -> $positive", positive > 0)
            assertTrue("음수가 양수로 갔다: -$magnitude -> $negative", negative < 0)
        }
    }

    @Test
    fun `왕복 오차가 진폭에 비례해 제한된다`() {
        // μ-law 는 로그 압축이라 큰 값에서 오차가 커진다. 작은 값에서는 촘촘하다.
        // 상대 오차 약 8% 안에 들어야 전화 품질이 나온다.
        var maxRelativeError = 0.0
        for (sample in -32_000..32_000 step 37) {
            val restored = G711Codec.decodeSample(G711Codec.encodeSample(sample))
            val denominator = maxOf(abs(sample), 256)
            val relative = abs(restored - sample).toDouble() / denominator
            maxRelativeError = maxOf(maxRelativeError, relative)
        }

        assertTrue("상대 오차가 너무 크다: $maxRelativeError", maxRelativeError < 0.08)
    }

    @Test
    fun `단조성이 유지된다`() {
        // 입력이 커지면 복원값도 (같거나) 커져야 한다. 세그먼트 경계에서 튀면 여기서 걸린다.
        var previous = G711Codec.decodeSample(G711Codec.encodeSample(0))
        for (sample in 0..32_000 step 13) {
            val restored = G711Codec.decodeSample(G711Codec.encodeSample(sample))
            assertTrue("단조성이 깨졌다: $sample 에서 $previous -> $restored", restored >= previous)
            previous = restored
        }
    }

    @Test
    fun `클리핑 범위를 넘어도 죽지 않는다`() {
        // Short 범위 전체가 들어올 수 있다.
        listOf(32_767, -32_768, 40_000, -40_000).forEach { sample ->
            val encoded = G711Codec.encodeSample(sample)
            assertTrue(encoded in 0..255)
        }
    }

    @Test
    fun `PCM 버퍼를 리틀엔디안으로 읽는다`() {
        // AudioRecord 가 ENCODING_PCM_16BIT 로 주는 배치가 리틀엔디안이다.
        // 바이트 순서를 뒤집으면 전혀 다른 샘플이 되어 잡음만 나온다.
        val sample = 0x1234
        val pcm = byteArrayOf(0x34, 0x12) // 리틀엔디안

        val viaBuffer = G711Codec.encode(pcm)
        val viaSample = G711Codec.encodeSample(sample)

        assertEquals(1, viaBuffer.size)
        assertEquals(viaSample.toByte(), viaBuffer[0])
    }

    @Test
    fun `음수 샘플도 리틀엔디안으로 옳게 읽는다`() {
        // -1000 = 0xFC18 → 리틀엔디안이면 18 FC
        val pcm = byteArrayOf(0x18, 0xFC.toByte())

        val restored = G711Codec.decodeSample(G711Codec.encode(pcm)[0].toInt() and 0xFF)

        assertTrue("음수가 양수로 읽혔다: $restored", restored < 0)
        assertTrue("복원값이 -1000 에서 너무 멀다: $restored", abs(restored - (-1000)) < 100)
    }

    @Test
    fun `버퍼 왕복 후 길이가 맞는다`() {
        val pcm = ByteArray(G711Codec.PCM_BYTES_PER_FRAME) { (it * 7).toByte() }

        val ulaw = G711Codec.encode(pcm)
        val back = G711Codec.decode(ulaw)

        assertEquals("20ms 프레임은 μ-law ${G711Codec.BYTES_PER_FRAME} 바이트다", G711Codec.BYTES_PER_FRAME, ulaw.size)
        assertEquals(pcm.size, back.size)
    }

    @Test
    fun `홀수 길이 PCM 은 마지막 바이트를 버린다`() {
        // 샘플 하나가 2바이트라 홀수는 불완전한 샘플이다. 죽는 것보다 버리는 게 낫다.
        val encoded = G711Codec.encode(byteArrayOf(0x34, 0x12, 0x56))

        assertEquals(1, encoded.size)
    }

    @Test
    fun `length 인자로 부분만 처리한다`() {
        // AudioRecord 는 요청한 만큼이 아니라 읽은 만큼만 채운다.
        val buffer = ByteArray(1024)

        val encoded = G711Codec.encode(buffer, length = 320)

        assertEquals(160, encoded.size)
    }

    @Test
    fun `length 가 버퍼보다 크면 거부한다`() {
        listOf<() -> Unit>(
            { G711Codec.encode(ByteArray(10), length = 100) },
            { G711Codec.decode(ByteArray(10), length = 100) },
        ).forEach { call ->
            assertTrue(runCatching { call() }.isFailure)
        }
    }

    @Test
    fun `20ms 프레임 상수가 서로 맞는다`() {
        // 8kHz 에서 20ms = 160 샘플. RTP 타임스탬프도 이만큼 올라간다.
        assertEquals(160, G711Codec.SAMPLES_PER_FRAME)
        assertEquals(G711Codec.SAMPLES_PER_FRAME, G711Codec.BYTES_PER_FRAME)
        assertEquals(G711Codec.SAMPLES_PER_FRAME * 2, G711Codec.PCM_BYTES_PER_FRAME)
    }
}
