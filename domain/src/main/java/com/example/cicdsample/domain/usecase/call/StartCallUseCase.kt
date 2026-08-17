package com.example.cicdsample.domain.usecase.call

import com.example.cicdsample.domain.model.call.PeerAddress
import com.example.cicdsample.domain.model.call.PeerAddressError
import com.example.cicdsample.domain.repository.CallRepository
import javax.inject.Inject

/** [StartCallUseCase] 가 주소를 거부할 때 [Result] 에 담기는 예외. */
class PeerAddressValidationException(val reason: PeerAddressError) :
    IllegalArgumentException(reason.name)

/**
 * 통화를 시작한다. 주소 검증 규칙이 전부 여기 모여 있어 소켓 없이 단위 테스트로 확인된다.
 *
 * 검증 순서가 곧 사용자에게 보여줄 우선순위다 — 호스트가 비어 있으면 포트 얘기를 꺼내지 않는다.
 *
 * - 호스트가 공백이면 [PeerAddressError.HOST_BLANK]
 * - IPv4/IPv6/호스트명 형식이 아니면 [PeerAddressError.HOST_INVALID]
 * - 포트가 [MIN_PORT]~[MAX_PORT] 밖이면 [PeerAddressError.PORT_OUT_OF_RANGE]
 * - 오디오와 영상 포트가 같으면 [PeerAddressError.PORT_CONFLICT]
 */
class StartCallUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    suspend operator fun invoke(
        host: String,
        audioPort: Int,
        videoPort: Int?,
        withVideo: Boolean,
    ): Result<PeerAddress> {
        val trimmed = host.trim()

        if (trimmed.isEmpty()) return failure(PeerAddressError.HOST_BLANK)
        if (!trimmed.isValidHost()) return failure(PeerAddressError.HOST_INVALID)

        if (audioPort !in MIN_PORT..MAX_PORT) return failure(PeerAddressError.PORT_OUT_OF_RANGE)

        // 영상을 쓰지 않기로 했으면 영상 포트는 아예 보지 않는다.
        val effectiveVideoPort = videoPort.takeIf { withVideo }
        if (withVideo) {
            if (effectiveVideoPort == null || effectiveVideoPort !in MIN_PORT..MAX_PORT) {
                return failure(PeerAddressError.PORT_OUT_OF_RANGE)
            }
            if (effectiveVideoPort == audioPort) return failure(PeerAddressError.PORT_CONFLICT)
        }

        val peer = PeerAddress(
            host = trimmed,
            audioPort = audioPort,
            videoPort = effectiveVideoPort,
        )
        repository.start(peer, withVideo)
        return Result.success(peer)
    }

    private fun failure(reason: PeerAddressError): Result<PeerAddress> =
        Result.failure(PeerAddressValidationException(reason))

    companion object {
        /**
         * 1024 미만은 특권 포트라 안드로이드 앱이 바인딩할 수 없다.
         * 상대 포트도 같은 범위로 제한해 두면 "왜 안 되지" 하는 시간을 줄인다.
         */
        const val MIN_PORT = 1024
        const val MAX_PORT = 65_535

        /**
         * 호스트 형식 검사. **DNS 를 조회하지 않는다** — 그래야 순수 함수로 남고
         * 테스트가 네트워크에 묶이지 않는다. 실제 해석 실패는 전송 계층이 알려 준다.
         */
        private fun String.isValidHost(): Boolean =
            isValidIpv4() || isValidIpv6() || isValidHostname()

        private fun String.isValidIpv4(): Boolean {
            val parts = split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                // "01" 같은 앞자리 0 은 8진수로 해석될 여지가 있어 막는다.
                part.isNotEmpty() &&
                    part.length <= 3 &&
                    part.all(Char::isDigit) &&
                    (part.length == 1 || part[0] != '0') &&
                    part.toInt() in 0..255
            }
        }

        private fun String.isValidIpv6(): Boolean {
            // 대괄호 표기([::1])도 받아 준다.
            val body = removeSurrounding("[", "]")
            if (!body.contains(':')) return false
            // "::" 은 한 번만 쓸 수 있다.
            if (body.split("::").size > 2) return false
            val groups = body.split(':').filter { it.isNotEmpty() }
            return groups.isNotEmpty() &&
                groups.size <= 8 &&
                groups.all { g -> g.length <= 4 && g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
        }

        private fun String.isValidHostname(): Boolean {
            if (length > 253) return false
            val labels = split('.')
            return labels.all { label ->
                label.isNotEmpty() &&
                    label.length <= 63 &&
                    label.first() != '-' &&
                    label.last() != '-' &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            } && labels.any { label -> label.any { it.isLetter() } }
        }
    }
}
