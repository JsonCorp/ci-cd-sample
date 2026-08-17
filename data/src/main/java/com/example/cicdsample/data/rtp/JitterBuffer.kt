package com.example.cicdsample.data.rtp

import com.example.cicdsample.domain.model.call.RtpStats

/**
 * 지터 버퍼. 네트워크가 뒤섞어 보낸 패킷을 순번대로 다시 세우고 통계를 낸다.
 *
 * UDP 는 순서도 도착도 보장하지 않는다. 받은 즉시 재생하면 순서가 뒤바뀐 음성이 그대로
 * 들리므로, 몇 패킷 분량을 모아 두고 순번대로 꺼낸다. 모으는 양이 지연과 맞바꾸는 값이다.
 *
 * **순수 Kotlin 이라 소켓 없이 전부 테스트된다** — 순서 뒤바뀜, 중복, 랩어라운드,
 * 너무 늦게 온 패킷 폐기가 모두 단위 테스트 대상이다.
 *
 * 스레드 안전하지 않다. 수신 루프 하나에서만 쓴다.
 *
 * @param targetDepth 꺼내기 전에 모아 둘 패킷 수. 클수록 유실에 강하고 지연이 늘어난다.
 * @param clockRate 페이로드의 샘플링 주기. 지터를 밀리초로 환산할 때 쓴다.
 */
class JitterBuffer(
    private val targetDepth: Int = DEFAULT_TARGET_DEPTH,
    private val clockRate: Int = 8_000,
) {
    init {
        require(targetDepth >= 1) { "버퍼 깊이는 1 이상이다: $targetDepth" }
        require(clockRate > 0) { "클럭 레이트는 양수다: $clockRate" }
    }

    /** 순번 → 패킷. 정렬해서 꺼내려고 TreeMap 대신 직접 관리한다(순번이 순환하므로). */
    private val buffered = HashMap<Int, RtpPacket>()

    private var baseSequence: Int? = null
    private var highestSequence: Int? = null

    /** 다음에 꺼내야 할 순번. null 이면 아직 한 번도 꺼내지 않았다. */
    private var nextToPop: Int? = null

    private var receivedCount = 0L
    private var duplicateCount = 0L
    private var outOfOrderCount = 0L
    private var lateDropCount = 0L

    // 지터 계산용 (RFC 3550 6.4.1)
    private var jitter = 0.0
    private var lastTransitTicks: Long? = null

    /**
     * 패킷을 넣는다.
     *
     * @param arrivalTicks 도착 시각을 [clockRate] 단위로 환산한 값. 지터 계산에만 쓴다.
     * @return 받아들였으면 true, 중복이거나 너무 늦어 버렸으면 false.
     */
    fun offer(packet: RtpPacket, arrivalTicks: Long): Boolean {
        val seq = packet.sequenceNumber

        if (baseSequence == null) {
            baseSequence = seq
            highestSequence = seq
        }

        // 이미 꺼낸 구간보다 뒤처진 패킷은 살릴 수 없다.
        val cursor = nextToPop
        if (cursor != null && !RtpPacket.isNewer(seq, cursor) && seq != cursor) {
            lateDropCount++
            return false
        }

        if (buffered.containsKey(seq)) {
            duplicateCount++
            return false
        }

        val highest = highestSequence
        if (highest != null) {
            if (RtpPacket.isNewer(seq, highest)) {
                highestSequence = seq
            } else if (seq != highest) {
                // 최고 순번보다 낮은데 아직 안 꺼낸 구간이면, 순서가 뒤바뀐 채 살아 돌아온 것이다.
                outOfOrderCount++
            }
        }

        buffered[seq] = packet
        receivedCount++
        updateJitter(packet.timestamp, arrivalTicks)
        return true
    }

    /**
     * 다음 패킷을 꺼낸다.
     *
     * 버퍼가 [targetDepth] 만큼 차기 전에는 null 을 준다 — 재생을 조금 늦춰야
     * 순서 뒤바뀜을 흡수할 여유가 생긴다.
     *
     * 기다리는 순번이 없고 버퍼가 깊이를 넘겼으면 **그 순번은 유실로 보고 건너뛴다.**
     * 없는 패킷을 계속 기다리면 통화가 그 자리에서 멈춘다.
     */
    fun poll(): RtpPacket? {
        if (buffered.size < targetDepth) return null

        val cursor = nextToPop ?: lowestBufferedSequence() ?: return null

        buffered.remove(cursor)?.let { packet ->
            nextToPop = (cursor + 1) and RtpPacket.MAX_SEQUENCE
            return packet
        }

        // 기다리던 순번이 없다. 버퍼가 깊이를 넘겼으니 유실로 확정하고 다음으로 넘어간다.
        val next = lowestBufferedSequence() ?: return null
        nextToPop = next
        return poll()
    }

    /** 버퍼를 비운다. 통계는 유지한다 — 통화 중 누적 지표라서 초기화하면 의미가 없다. */
    fun clear() {
        buffered.clear()
        nextToPop = null
    }

    /** 현재까지의 수신 품질. */
    fun stats(): RtpStats {
        val base = baseSequence
        val highest = highestSequence
        val expected = if (base == null || highest == null) {
            0L
        } else {
            RtpPacket.sequenceDistance(highest, base).toLong() + 1
        }
        // 늦게 도착해 버린 패킷도 재생되지 못했으므로 유실로 센다.
        val lost = (expected - receivedCount + lateDropCount).coerceAtLeast(0)

        return RtpStats(
            expected = expected,
            received = receivedCount,
            lost = lost,
            outOfOrder = outOfOrderCount,
            duplicated = duplicateCount,
            jitterMs = jitter * 1_000.0 / clockRate,
        )
    }

    /**
     * 버퍼에 든 순번 중 가장 앞선 것.
     *
     * 단순 최솟값이 아니다 — 랩어라운드 구간에서는 65535 가 0 보다 앞이므로
     * 순환 비교로 골라야 한다.
     */
    private fun lowestBufferedSequence(): Int? =
        buffered.keys.reduceOrNull { acc, seq -> if (RtpPacket.isNewer(acc, seq)) seq else acc }

    /**
     * 도착 간격 지터 (RFC 3550 6.4.1).
     *
     * ```
     * D(i,j) = (Rj - Ri) - (Sj - Si)
     * J(i)   = J(i-1) + (|D(i-1,i)| - J(i-1)) / 16
     * ```
     */
    private fun updateJitter(rtpTimestamp: Long, arrivalTicks: Long) {
        val transit = arrivalTicks - rtpTimestamp
        val previous = lastTransitTicks
        if (previous != null) {
            val d = transit - previous
            jitter += (kotlin.math.abs(d) - jitter) / 16.0
        }
        lastTransitTicks = transit
    }

    companion object {
        /**
         * 20ms 패킷 3개 = 60ms 지연. 랩 안에서 쓰기에 충분하고,
         * 사람이 대화 지연을 느끼기 시작하는 150ms 보다 넉넉히 아래다.
         */
        const val DEFAULT_TARGET_DEPTH = 3
    }
}
