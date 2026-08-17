package com.example.cicdsample.data.rtp

/**
 * RTP 패킷 하나 (RFC 3550 §5.1).
 *
 * 고정 헤더 12바이트의 배치는 이렇다.
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 *
 * 이 클래스가 순수 Kotlin 인 것이 중요하다 — 소켓도 코덱도 없이 직렬화·파싱을
 * 왕복 테스트할 수 있고, 그래서 헤더 비트를 잘못 넣는 실수가 CI 에서 즉시 잡힌다.
 *
 * @param sequenceNumber 0~65535. 넘치면 0으로 돌아간다(랩어라운드).
 * @param timestamp 32비트 부호 없는 값이라 [Long] 으로 담는다.
 * @param ssrc 32비트 부호 없는 값이라 [Long] 으로 담는다.
 */
data class RtpPacket(
    val payloadType: Int,
    val sequenceNumber: Int,
    val timestamp: Long,
    val ssrc: Long,
    val marker: Boolean,
    val payload: ByteArray,
) {
    init {
        require(payloadType in 0..127) { "페이로드 타입은 0~127 이다: $payloadType" }
        require(sequenceNumber in 0..MAX_SEQUENCE) { "순번은 0~$MAX_SEQUENCE 이다: $sequenceNumber" }
        require(timestamp in 0..MAX_UINT32) { "타임스탬프가 32비트를 넘는다: $timestamp" }
        require(ssrc in 0..MAX_UINT32) { "SSRC 가 32비트를 넘는다: $ssrc" }
    }

    /** 헤더 12바이트 + 페이로드를 이어 붙인 전송 바이트열. */
    fun toBytes(): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size)

        // 버전 2 고정. 패딩·확장 없음, CSRC 개수 0.
        out[0] = (VERSION shl 6).toByte()
        out[1] = (payloadType or if (marker) MARKER_BIT else 0).toByte()

        out[2] = (sequenceNumber ushr 8).toByte()
        out[3] = sequenceNumber.toByte()

        out[4] = (timestamp ushr 24).toByte()
        out[5] = (timestamp ushr 16).toByte()
        out[6] = (timestamp ushr 8).toByte()
        out[7] = timestamp.toByte()

        out[8] = (ssrc ushr 24).toByte()
        out[9] = (ssrc ushr 16).toByte()
        out[10] = (ssrc ushr 8).toByte()
        out[11] = ssrc.toByte()

        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    // ByteArray 필드가 있으므로 equals/hashCode 를 직접 쓴다 —
    // data class 기본 구현은 배열을 참조로 비교해 내용이 같아도 다르다고 한다.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RtpPacket) return false
        return payloadType == other.payloadType &&
            sequenceNumber == other.sequenceNumber &&
            timestamp == other.timestamp &&
            ssrc == other.ssrc &&
            marker == other.marker &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = payloadType
        result = 31 * result + sequenceNumber
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + ssrc.hashCode()
        result = 31 * result + marker.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE = 12
        const val MAX_SEQUENCE = 0xFFFF
        const val VERSION = 2

        private const val MARKER_BIT = 0x80
        private const val MAX_UINT32 = 0xFFFF_FFFFL

        /**
         * 바이트열을 패킷으로 읽는다. 형식이 어긋나면 **예외를 던지지 않고 null** 을 준다.
         *
         * 소켓으로 들어오는 값은 신뢰할 수 없다. 잘못된 패킷 하나로 수신 루프가 죽으면
         * 통화 전체가 끊기므로, 조용히 버리고 다음 패킷을 받는 편이 맞다.
         *
         * @param length 실제로 읽어야 할 길이. 소켓 버퍼는 보통 실제 수신량보다 크다.
         */
        fun parse(bytes: ByteArray, length: Int = bytes.size): RtpPacket? {
            if (length < HEADER_SIZE || length > bytes.size) return null

            val version = (bytes[0].toInt() and 0xC0) ushr 6
            if (version != VERSION) return null

            val hasPadding = (bytes[0].toInt() and 0x20) != 0
            val hasExtension = (bytes[0].toInt() and 0x10) != 0
            val csrcCount = bytes[0].toInt() and 0x0F

            // CSRC 목록과 확장 헤더는 이 앱이 만들지 않지만, 상대가 붙여 보낼 수 있으니 건너뛴다.
            var offset = HEADER_SIZE + csrcCount * 4
            if (length < offset) return null

            if (hasExtension) {
                if (length < offset + 4) return null
                val extWords = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)
                offset += 4 + extWords * 4
                if (length < offset) return null
            }

            var end = length
            if (hasPadding) {
                val padLength = bytes[length - 1].toInt() and 0xFF
                // 패딩 길이가 남은 바이트보다 크면 깨진 패킷이다.
                if (padLength == 0 || padLength > end - offset) return null
                end -= padLength
            }

            val marker = (bytes[1].toInt() and MARKER_BIT) != 0
            val payloadType = bytes[1].toInt() and 0x7F

            val sequenceNumber = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)

            val timestamp = ((bytes[4].toLong() and 0xFF) shl 24) or
                ((bytes[5].toLong() and 0xFF) shl 16) or
                ((bytes[6].toLong() and 0xFF) shl 8) or
                (bytes[7].toLong() and 0xFF)

            val ssrc = ((bytes[8].toLong() and 0xFF) shl 24) or
                ((bytes[9].toLong() and 0xFF) shl 16) or
                ((bytes[10].toLong() and 0xFF) shl 8) or
                (bytes[11].toLong() and 0xFF)

            return RtpPacket(
                payloadType = payloadType,
                sequenceNumber = sequenceNumber,
                timestamp = timestamp,
                ssrc = ssrc,
                marker = marker,
                payload = bytes.copyOfRange(offset, end),
            )
        }

        /**
         * 순번 a 가 b 보다 뒤인지 판단한다 (RFC 1982 의 순환 비교).
         *
         * 65535 다음은 0 이므로 단순 크기 비교로는 랩어라운드에서 순서가 뒤집힌다.
         * 두 값의 차이를 16비트 부호 있는 값으로 보면 랩 구간에서도 옳게 나온다.
         */
        fun isNewer(a: Int, b: Int): Boolean {
            val diff = (a - b) and MAX_SEQUENCE
            return diff != 0 && diff < 0x8000
        }

        /** 순번 a 에서 b 까지의 거리. 랩어라운드를 고려한다. */
        fun sequenceDistance(a: Int, b: Int): Int = (a - b) and MAX_SEQUENCE
    }
}
