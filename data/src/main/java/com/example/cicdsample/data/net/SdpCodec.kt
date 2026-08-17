package com.example.cicdsample.data.net

import com.example.cicdsample.domain.model.call.MediaKind
import com.example.cicdsample.domain.model.call.MediaSession
import com.example.cicdsample.domain.model.call.PeerAddress
import com.example.cicdsample.domain.model.call.Payload
import javax.inject.Inject

/** SDP 를 읽지 못한 이유. */
enum class SdpError {
    /** 빈 문자열이거나 줄이 하나도 없다. */
    EMPTY,

    /** `c=` 줄이 없어 어디로 보낼지 알 수 없다. */
    NO_CONNECTION,

    /** `m=audio` 줄이 없다. 이 앱은 오디오 없는 통화를 만들지 않는다. */
    NO_AUDIO,

    /** 포트 숫자가 잘못됐다. */
    BAD_PORT,
}

/** [SdpCodec] 이 실패할 때 [Result] 에 담기는 예외. */
class SdpParseException(val reason: SdpError) : IllegalArgumentException(reason.name)

/**
 * SDP(RFC 4566) 생성·해석.
 *
 * 단말 대 단말이라 교환기가 없다 — 사람이 이 텍스트를 복사해 옮기는 것이 시그널링 전부다.
 * 그래서 형식을 최소한으로 유지하되 표준을 따른다. 상용 SIP 단말이 만든 SDP 도
 * 필요한 줄만 골라 읽을 수 있어야 한다.
 *
 * 순수 문자열 로직이라 소켓 없이 전부 테스트된다.
 */
class SdpCodec @Inject constructor() {

    /**
     * 내 세션들을 SDP 텍스트로 만든다.
     *
     * @param sessions 열어 둔 미디어 세션. 오디오가 반드시 하나 있어야 한다.
     * @param localHost 내 단말 IP. `c=` 줄에 들어간다.
     */
    fun encode(sessions: List<MediaSession>, localHost: String): String {
        require(sessions.any { it.kind == MediaKind.AUDIO }) { "오디오 세션이 없다" }

        val audio = sessions.first { it.kind == MediaKind.AUDIO }
        val video = sessions.firstOrNull { it.kind == MediaKind.VIDEO }
        val addressType = if (localHost.isIpv6()) "IP6" else "IP4"

        return buildString {
            appendLine("v=0")
            // o= 의 세션 id/버전은 재협상 추적용이다. 재협상이 없으므로 SSRC 를 그대로 쓴다.
            appendLine("o=- ${audio.ssrc} 1 IN $addressType $localHost")
            appendLine("s=CicdSample")
            appendLine("c=IN $addressType $localHost")
            appendLine("t=0 0")

            appendMediaLine(audio, addressType)
            video?.let { appendMediaLine(it, addressType) }
        }
    }

    private fun StringBuilder.appendMediaLine(session: MediaSession, addressType: String) {
        val kind = if (session.kind == MediaKind.AUDIO) "audio" else "video"
        val payload = session.payload

        appendLine("m=$kind ${session.localPort} RTP/AVP ${payload.payloadType}")
        appendLine("a=rtpmap:${payload.payloadType} ${payload.encodingName}/${payload.clockRate}")
        // 이 앱은 보내고 받는다. 한쪽만 하는 모드는 만들지 않는다.
        appendLine("a=sendrecv")
        if (addressType == "IP6") {
            // 참고용으로만 남긴다 — 해석할 때는 c= 줄을 본다.
            appendLine("a=ssrc:${session.ssrc}")
        }
    }

    /**
     * 상대 SDP 에서 주소를 뽑는다.
     *
     * 모르는 줄은 조용히 건너뛴다 — 상용 단말은 우리가 쓰지 않는 속성을 잔뜩 넣어 보낸다.
     * 필요한 것은 `c=` 의 주소와 `m=audio` / `m=video` 의 포트뿐이다.
     */
    fun decode(text: String): Result<PeerAddress> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) return failure(SdpError.EMPTY)

        val host = lines.firstNotNullOfOrNull { line -> line.parseConnectionHost() }
            ?: return failure(SdpError.NO_CONNECTION)

        var audioPort: Int? = null
        var videoPort: Int? = null
        var sawAudioLine = false
        var sawVideoLine = false

        for (line in lines) {
            if (!line.startsWith("m=")) continue
            val fields = line.removePrefix("m=").split(' ').filter { it.isNotEmpty() }
            if (fields.size < 2) continue

            val kind = fields[0]
            val port = fields[1].toIntOrNull()

            when (kind) {
                "audio" -> {
                    sawAudioLine = true
                    if (port == null) return failure(SdpError.BAD_PORT)
                    // 포트 0 은 "이 미디어를 쓰지 않는다"는 뜻이다(RFC 4566 §5.14).
                    if (port != 0 && audioPort == null) audioPort = port
                }
                "video" -> {
                    sawVideoLine = true
                    if (port == null) return failure(SdpError.BAD_PORT)
                    if (port != 0 && videoPort == null) videoPort = port
                }
            }
        }

        if (!sawAudioLine) return failure(SdpError.NO_AUDIO)
        val resolvedAudioPort = audioPort ?: return failure(SdpError.BAD_PORT)
        if (resolvedAudioPort !in 1..65_535) return failure(SdpError.BAD_PORT)
        if (videoPort != null && videoPort !in 1..65_535) return failure(SdpError.BAD_PORT)

        // 영상 줄이 있었지만 포트가 0 이면 상대가 영상을 끈 것이다 — null 로 둔다.
        val resolvedVideoPort = if (sawVideoLine) videoPort else null

        return Result.success(
            PeerAddress(host = host, audioPort = resolvedAudioPort, videoPort = resolvedVideoPort),
        )
    }

    /** `c=IN IP4 192.168.0.42` 에서 주소만 꺼낸다. */
    private fun String.parseConnectionHost(): String? {
        if (!startsWith("c=")) return null
        val fields = removePrefix("c=").split(' ').filter { it.isNotEmpty() }
        if (fields.size < 3) return null
        // 멀티캐스트 TTL 표기(`224.0.0.1/127`)가 붙어 올 수 있어 잘라낸다.
        return fields[2].substringBefore('/').takeIf { it.isNotEmpty() }
    }

    private fun failure(reason: SdpError): Result<PeerAddress> =
        Result.failure(SdpParseException(reason))

    private companion object {
        /** IPv6 판정. 콜론이 있으면 IPv6 로 본다 — SDP 에서는 이 구분이면 충분하다. */
        fun String.isIpv6(): Boolean = contains(':')
    }
}
