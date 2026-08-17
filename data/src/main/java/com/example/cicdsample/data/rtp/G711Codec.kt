package com.example.cicdsample.data.rtp

/**
 * G.711 μ-law 코덱 (ITU-T G.711, RFC 3551 §4.5.14).
 *
 * 16비트 선형 PCM 한 샘플을 1바이트로 줄인다 — 8kHz 모노에서 64kbit/s.
 * 압축률은 낮지만 이 앱에서 쓰는 이유는 셋이다.
 *
 * 1. **기기 지원에 좌우되지 않는다.** MediaCodec 을 거치지 않으므로
 *    Opus 인코더가 없는 기기에서도 똑같이 동작한다.
 * 2. **순수 산술이라 전부 단위 테스트된다.** 왕복 오차 한계까지 검증할 수 있다.
 * 3. **정적 페이로드 타입 0** 이라 협상 없이도 상대가 무엇인지 안다.
 *
 * 대신 대역폭을 많이 쓰고 8kHz 협대역이라 음질은 전화 수준이다.
 */
object G711Codec {

    private const val BIAS = 0x84
    private const val CLIP = 32_635
    private const val SIGN_BIT = 0x80
    private const val QUANT_MASK = 0x0F
    private const val SEG_SHIFT = 4

    /**
     * 세그먼트 경계표. BIAS 를 더한 값이 어느 구간에 드는지로 지수를 정한다.
     *
     * 이 표가 한 칸이라도 밀리면 복원값이 2배씩 어긋난다 — 소리가 나기는 하지만
     * 음량과 파형이 망가져 "잡음이 섞인다" 로만 보인다. 단위 테스트가 잡아 준 자리다.
     */
    private val SEGMENT_ENDS = intArrayOf(
        0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF, 0x3FFF, 0x7FFF,
    )

    /**
     * 선형 PCM 샘플 하나를 μ-law 바이트로 압축한다.
     *
     * @param sample 16비트 부호 있는 샘플
     * @return μ-law 바이트 (0~255 범위의 Int)
     */
    fun encodeSample(sample: Int): Int {
        // 부호를 분리하고 절댓값으로 다룬다.
        var value = sample
        val sign = if (value < 0) {
            value = -value
            0x80
        } else {
            0
        }

        // 클리핑. G.711 이 표현할 수 있는 범위를 넘으면 잘라낸다.
        if (value > CLIP) value = CLIP
        value += BIAS

        val segment = SEGMENT_ENDS.indexOfFirst { value <= it }.let { if (it < 0) 7 else it }

        // μ-law 는 최종 결과를 비트 반전해 담는다.
        val mantissa = (value ushr (segment + 3)) and QUANT_MASK
        return (sign or (segment shl SEG_SHIFT) or mantissa).inv() and 0xFF
    }

    /**
     * μ-law 바이트 하나를 선형 PCM 샘플로 되돌린다.
     *
     * @param encoded μ-law 바이트 (0~255 범위의 Int)
     * @return 16비트 부호 있는 샘플
     */
    fun decodeSample(encoded: Int): Int {
        val inverted = encoded.inv() and 0xFF

        var value = ((inverted and QUANT_MASK) shl 3) + BIAS
        value = value shl ((inverted and 0x70) ushr SEG_SHIFT)
        value -= BIAS

        return if ((inverted and SIGN_BIT) != 0) -value else value
    }

    /**
     * 16비트 리틀엔디안 PCM 버퍼를 μ-law 바이트열로 압축한다.
     *
     * AudioRecord 가 [android.media.AudioFormat.ENCODING_PCM_16BIT] 로 주는 버퍼가
     * 리틀엔디안이라 그 배치를 그대로 받는다.
     *
     * @param pcm 입력 버퍼. 길이가 홀수면 마지막 바이트는 버린다.
     * @param length 실제로 읽을 바이트 수
     */
    fun encode(pcm: ByteArray, length: Int = pcm.size): ByteArray {
        require(length <= pcm.size) { "length 가 버퍼보다 크다: $length > ${pcm.size}" }
        val sampleCount = length / 2
        val out = ByteArray(sampleCount)
        for (i in 0 until sampleCount) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            out[i] = encodeSample(sample.toShort().toInt()).toByte()
        }
        return out
    }

    /** μ-law 바이트열을 16비트 리틀엔디안 PCM 으로 되돌린다. */
    fun decode(ulaw: ByteArray, length: Int = ulaw.size): ByteArray {
        require(length <= ulaw.size) { "length 가 버퍼보다 크다: $length > ${ulaw.size}" }
        val out = ByteArray(length * 2)
        for (i in 0 until length) {
            val sample = decodeSample(ulaw[i].toInt() and 0xFF)
            out[i * 2] = sample.toByte()
            out[i * 2 + 1] = (sample shr 8).toByte()
        }
        return out
    }

    /** 8kHz 에서 20ms 에 해당하는 샘플 수. RTP 타임스탬프도 이만큼씩 올라간다. */
    const val SAMPLES_PER_FRAME = 160

    /** 20ms 프레임 하나의 μ-law 바이트 수. 샘플당 1바이트이므로 샘플 수와 같다. */
    const val BYTES_PER_FRAME = SAMPLES_PER_FRAME

    /** 20ms 프레임 하나의 PCM 바이트 수 (16비트이므로 샘플당 2바이트). */
    const val PCM_BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2
}
