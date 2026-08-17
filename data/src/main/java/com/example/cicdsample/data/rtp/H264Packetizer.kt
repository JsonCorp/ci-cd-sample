package com.example.cicdsample.data.rtp

/**
 * H.264 를 RTP 로 실어 보내기 (RFC 6184).
 *
 * NAL 유닛 하나가 MTU 에 들어가면 그대로 한 패킷에 담고(Single NAL Unit Mode),
 * 넘치면 **FU-A** 로 쪼갠다. 키프레임의 IDR NAL 은 보통 수십 KB 라 반드시 쪼개진다.
 *
 * FU-A 패킷 구조:
 * ```
 * +---------------+---------------+
 * |  FU indicator |   FU header   |  ... 페이로드 조각
 * +---------------+---------------+
 *
 * FU indicator:  F(1) NRI(2) Type=28(5)
 * FU header:     S(1) E(1) R(1) Type(5)   ← 원본 NAL 타입
 * ```
 * S 는 첫 조각, E 는 마지막 조각에만 1 이다.
 *
 * 순수 로직이라 카메라도 코덱도 없이 쪼개기·재조립을 왕복 테스트할 수 있다.
 */
object H264Packetizer {

    const val FU_A_TYPE = 28
    const val STAP_A_TYPE = 24

    /**
     * IPv4 + UDP 헤더를 뺀 안전한 페이로드 상한.
     *
     * 이더넷 MTU 1500 에서 IP 20 + UDP 8 + RTP 12 = 40 을 빼면 1460 이지만,
     * VPN·터널이 끼면 더 줄어든다. 1200 은 실무에서 널리 쓰는 보수적인 값이다.
     */
    const val MAX_PAYLOAD_SIZE = 1_200

    /**
     * NAL 유닛 하나를 RTP 페이로드 목록으로 쪼갠다.
     *
     * @param nal Annex B 시작코드(`00 00 00 01`)를 **제거한** NAL 유닛
     * @param maxPayloadSize RTP 페이로드 하나의 최대 크기
     * @return 순서대로 보낼 페이로드 목록. 빈 입력이면 빈 목록.
     */
    fun packetize(nal: ByteArray, maxPayloadSize: Int = MAX_PAYLOAD_SIZE): List<ByteArray> {
        require(maxPayloadSize > 2) { "FU-A 는 헤더 2바이트가 필요하다: $maxPayloadSize" }
        if (nal.isEmpty()) return emptyList()

        // 들어가면 쪼개지 않는다 — 헤더 2바이트를 아끼는 것이 아니라,
        // 받는 쪽이 재조립 상태를 만들지 않아도 되게 하는 것이 목적이다.
        if (nal.size <= maxPayloadSize) return listOf(nal)

        val header = nal[0].toInt() and 0xFF
        val fBit = header and 0x80
        val nri = header and 0x60
        val nalType = header and 0x1F

        val indicator = (fBit or nri or FU_A_TYPE).toByte()

        // 첫 바이트는 원본 NAL 헤더라 조각 페이로드에 넣지 않는다 —
        // FU 헤더가 그 정보를 대신 담는다.
        val body = nal.copyOfRange(1, nal.size)
        val chunkSize = maxPayloadSize - 2

        val out = ArrayList<ByteArray>((body.size + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < body.size) {
            val take = minOf(chunkSize, body.size - offset)
            val isFirst = offset == 0
            val isLast = offset + take >= body.size

            val fragment = ByteArray(take + 2)
            fragment[0] = indicator
            fragment[1] = (
                (if (isFirst) 0x80 else 0) or
                    (if (isLast) 0x40 else 0) or
                    nalType
                ).toByte()
            body.copyInto(fragment, 2, offset, offset + take)

            out += fragment
            offset += take
        }
        return out
    }

    /**
     * Annex B 바이트스트림을 NAL 유닛 목록으로 자른다.
     *
     * MediaCodec 이 내주는 H.264 출력은 `00 00 00 01` 또는 `00 00 01` 로 구분된
     * Annex B 형식이다. RTP 는 시작코드를 쓰지 않으므로 떼어내야 한다.
     */
    fun splitAnnexB(stream: ByteArray, length: Int = stream.size): List<ByteArray> {
        require(length <= stream.size) { "length 가 버퍼보다 크다: $length > ${stream.size}" }
        val starts = ArrayList<Pair<Int, Int>>() // (NAL 시작 위치, 시작코드 길이)

        var i = 0
        while (i + 2 < length) {
            if (stream[i] == 0.toByte() && stream[i + 1] == 0.toByte()) {
                if (stream[i + 2] == 1.toByte()) {
                    starts += (i + 3) to 3
                    i += 3
                    continue
                }
                if (i + 3 < length && stream[i + 2] == 0.toByte() && stream[i + 3] == 1.toByte()) {
                    starts += (i + 4) to 4
                    i += 4
                    continue
                }
            }
            i++
        }
        if (starts.isEmpty()) return emptyList()

        val out = ArrayList<ByteArray>(starts.size)
        for ((index, entry) in starts.withIndex()) {
            val (begin, _) = entry
            // 다음 NAL 의 시작코드 앞까지가 이 NAL 의 끝이다.
            val end = if (index + 1 < starts.size) {
                starts[index + 1].let { (nextBegin, codeLength) -> nextBegin - codeLength }
            } else {
                length
            }
            if (end > begin) out += stream.copyOfRange(begin, end)
        }
        return out
    }

    /**
     * 받은 FU-A 조각을 다시 NAL 유닛으로 합친다.
     *
     * 스레드 안전하지 않다. 수신 루프 하나에서만 쓴다.
     */
    class Reassembler {

        private var buffer = ByteArrayBuilder()
        private var expectedNalType = -1
        private var started = false

        /** 재조립 중이던 것을 버린다. 순번이 끊기면 그 프레임은 살릴 수 없다. */
        fun reset() {
            buffer = ByteArrayBuilder()
            expectedNalType = -1
            started = false
        }

        /**
         * 페이로드 하나를 넣는다.
         *
         * @return 완성된 NAL 유닛. 아직 모자라면 null.
         */
        fun offer(payload: ByteArray): ByteArray? {
            if (payload.isEmpty()) return null

            val type = payload[0].toInt() and 0x1F

            // 쪼개지지 않은 NAL 은 그대로 완성품이다.
            if (type != FU_A_TYPE) {
                reset()
                return payload
            }

            if (payload.size < 2) return null

            val fuHeader = payload[1].toInt() and 0xFF
            val isFirst = (fuHeader and 0x80) != 0
            val isLast = (fuHeader and 0x40) != 0
            val nalType = fuHeader and 0x1F

            if (isFirst) {
                buffer = ByteArrayBuilder()
                started = true
                expectedNalType = nalType
                // 원본 NAL 헤더를 되살린다 — FU indicator 의 F/NRI 를 그대로 쓴다.
                val indicator = payload[0].toInt() and 0xFF
                buffer.append(((indicator and 0xE0) or nalType).toByte())
            } else if (!started || nalType != expectedNalType) {
                // 첫 조각을 못 받았거나 다른 NAL 의 조각이 섞였다. 이 프레임은 포기한다.
                reset()
                return null
            }

            buffer.append(payload, 2, payload.size)

            if (!isLast) return null

            val complete = buffer.toByteArray()
            reset()
            return complete
        }
    }

    /** 조각을 이어 담는 최소 버퍼. ByteArrayOutputStream 을 쓰지 않아 순수 Kotlin 으로 남는다. */
    private class ByteArrayBuilder {
        private var data = ByteArray(INITIAL_CAPACITY)
        private var size = 0

        fun append(byte: Byte) {
            ensure(size + 1)
            data[size++] = byte
        }

        fun append(source: ByteArray, from: Int, to: Int) {
            val take = to - from
            if (take <= 0) return
            ensure(size + take)
            source.copyInto(data, size, from, to)
            size += take
        }

        fun toByteArray(): ByteArray = data.copyOf(size)

        private fun ensure(capacity: Int) {
            if (capacity <= data.size) return
            var next = data.size
            while (next < capacity) next *= 2
            data = data.copyOf(next)
        }

        companion object {
            const val INITIAL_CAPACITY = 4_096
        }
    }
}
