package com.example.cicdsample.domain.model.call

/**
 * 통화 상태. 단말 대 단말이라 교환기도 서버도 없다 —
 * 상태를 바꾸는 것은 사용자 조작과 소켓 이벤트뿐이다.
 *
 * 시그널링이 없으므로 '벨이 울리는' 상태도 없다. 양쪽이 서로의 주소를 넣고
 * 각자 [Connecting] 을 시작하면, 첫 패킷이 도착한 쪽이 [Active] 로 넘어간다.
 */
enum class CallStage {
    /** 통화 전. 상대 주소를 입력받는 단계. */
    Idle,

    /** 소켓을 열고 보내기 시작했지만 아직 상대 패킷을 못 받은 상태. */
    Connecting,

    /** 양방향으로 패킷이 흐르는 상태. */
    Active,

    /** 끝났다. [CallEndReason] 이 이유를 담는다. */
    Ended,
}

/** 통화가 끝난 이유. UI 는 이 값을 문구로 바꾸기만 한다. */
enum class CallEndReason {
    /** 사용자가 끊었다. */
    LocalHangup,

    /** 상대 패킷이 [MediaSession.RECEIVE_TIMEOUT_MS] 동안 끊겼다. */
    ReceiveTimeout,

    /** 소켓을 열지 못했거나 전송 중 오류가 났다. */
    TransportError,

    /** 마이크·카메라 권한이 없다. */
    PermissionDenied,
}

/**
 * 상대 단말의 주소. 시그널링 서버가 없으므로 사람이 직접 넣는다.
 *
 * @param host IPv4/IPv6 주소 또는 호스트명
 * @param audioPort 오디오 RTP 포트. RFC 3550 은 짝수 포트를 권한다(홀수는 RTCP 용).
 * @param videoPort 영상 RTP 포트. 영상을 쓰지 않으면 null.
 */
data class PeerAddress(
    val host: String,
    val audioPort: Int,
    val videoPort: Int?,
)

/** 주소 입력이 거부된 이유. */
enum class PeerAddressError {
    HOST_BLANK,
    HOST_INVALID,
    PORT_OUT_OF_RANGE,
    PORT_CONFLICT,
}

/** 미디어 종류. 포트와 SSRC 를 각각 따로 쓴다. */
enum class MediaKind {
    AUDIO,
    VIDEO,
}

/**
 * 이 앱이 다루는 페이로드 타입.
 *
 * 오디오는 G.711 μ-law 로 고정한다 — RFC 3551 이 정한 **정적 페이로드 타입 0** 이라
 * 협상 없이도 상대가 무엇인지 안다. 코덱 구현이 순수 산술이라 기기 지원에 좌우되지 않는다.
 *
 * 영상은 H.264 이고 동적 페이로드 타입(96~127) 범위에서 96 을 쓴다.
 */
enum class Payload(val payloadType: Int, val clockRate: Int, val kind: MediaKind) {
    PCMU(payloadType = 0, clockRate = 8_000, kind = MediaKind.AUDIO),
    H264(payloadType = 96, clockRate = 90_000, kind = MediaKind.VIDEO),
    ;

    /** SDP `a=rtpmap:` 에 쓰는 인코딩 이름. */
    val encodingName: String
        get() = when (this) {
            PCMU -> "PCMU"
            H264 -> "H264"
        }
}

/**
 * 내가 열어 둔 미디어 세션 하나. SDP 를 만들 때 이 값이 그대로 들어간다.
 *
 * @param ssrc RFC 3550 의 동기화 소스 식별자. 세션마다 무작위로 뽑는다.
 */
data class MediaSession(
    val kind: MediaKind,
    val payload: Payload,
    val localPort: Int,
    val ssrc: Long,
) {
    init {
        require(payload.kind == kind) { "페이로드 $payload 는 $kind 용이 아니다" }
    }

    companion object {
        /** 이 시간 동안 상대 패킷이 없으면 통화를 끊는다. */
        const val RECEIVE_TIMEOUT_MS = 8_000L
    }
}

/**
 * 통화 한 건의 전체 상태. 화면이 그리는 데 필요한 전부가 여기 있다.
 *
 * @param micMuted 마이크를 끈 상태. 끄면 패킷 전송 자체를 멈춘다 —
 *   무음을 보내는 것보다 대역폭을 아끼고, 상대는 수신 타임아웃 대신
 *   '음성 없음'만 겪는다(영상 패킷이 계속 흐르므로 통화는 유지된다).
 */
data class CallSnapshot(
    val stage: CallStage = CallStage.Idle,
    val peer: PeerAddress? = null,
    val videoEnabled: Boolean = false,
    val micMuted: Boolean = false,
    val endReason: CallEndReason? = null,
    val audioStats: RtpStats = RtpStats.EMPTY,
    val videoStats: RtpStats = RtpStats.EMPTY,
) {
    /** 통화가 진행 중인지. UI 가 버튼을 켜고 끄는 기준. */
    val inProgress: Boolean
        get() = stage == CallStage.Connecting || stage == CallStage.Active
}

/**
 * RTP 수신 품질 지표.
 *
 * @param expected 기대한 패킷 수. 최초 순번부터 마지막 순번까지의 개수다.
 * @param received 실제로 받은 패킷 수(중복 제외).
 * @param lost 유실 추정치. 늦게 도착해 이미 버린 패킷도 유실로 센다.
 * @param outOfOrder 순서가 뒤바뀐 채 도착했지만 살려낸 패킷 수.
 * @param duplicated 같은 순번이 두 번 이상 온 횟수.
 * @param jitterMs RFC 3550 6.4.1 의 도착 간격 지터를 밀리초로 환산한 값.
 */
data class RtpStats(
    val expected: Long = 0,
    val received: Long = 0,
    val lost: Long = 0,
    val outOfOrder: Long = 0,
    val duplicated: Long = 0,
    val jitterMs: Double = 0.0,
) {
    /** 유실률 0~100. 기대 패킷이 0이면 0. */
    val lossPercent: Double
        get() = if (expected <= 0) 0.0 else (lost * 100.0 / expected)

    companion object {
        val EMPTY = RtpStats()
    }
}
