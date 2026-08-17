package com.example.cicdsample.domain.usecase.call

import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallSnapshot
import com.example.cicdsample.domain.model.call.MediaSession
import com.example.cicdsample.domain.model.call.PeerAddress
import com.example.cicdsample.domain.repository.CallRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** 통화 상태를 구독한다. 화면은 이 하나만 보면 된다. */
class ObserveCallUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    operator fun invoke(): Flow<CallSnapshot> = repository.observeCall()
}

/**
 * 사용자가 통화를 끊는다.
 *
 * 이유를 [CallEndReason.LocalHangup] 으로 고정한다 — 타임아웃이나 전송 오류로 끊는 것은
 * :data 가 스스로 판단해 넣으므로, 사용자 조작 경로에서 다른 이유가 섞일 일이 없다.
 */
class EndCallUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    suspend operator fun invoke() = repository.end(CallEndReason.LocalHangup)
}

/** 마이크 음소거를 전환한다. */
class ToggleMicUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    suspend operator fun invoke(muted: Boolean) = repository.setMicMuted(muted)
}

/** 통화 중 영상 송신을 전환한다. */
class ToggleVideoUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setVideoEnabled(enabled)
}

/**
 * 내가 열어 둔 미디어 세션.
 *
 * 화면이 "내 주소 192.168.0.5:5004" 같은 한 줄을 보여주려고 쓴다 — 사용자가 SDP 전체를
 * 옮기지 않고 말로 알려 주는 경우가 더 많다.
 */
class GetLocalSessionsUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    operator fun invoke(): List<MediaSession> = repository.localSessions()
}

/**
 * 상대에게 보여줄 내 SDP 를 만든다.
 *
 * @param localHost 내 단말의 IP. 화면이 알아내 넘긴다 —
 *   도메인이 네트워크 인터페이스를 뒤지기 시작하면 순수하지 않게 된다.
 */
class BuildLocalSdpUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    operator fun invoke(localHost: String): String = repository.localSdp(localHost)
}

/** 상대가 준 SDP 에서 주소를 뽑는다. */
class ParseRemoteSdpUseCase @Inject constructor(
    private val repository: CallRepository,
) {
    operator fun invoke(text: String): Result<PeerAddress> = repository.parseRemoteSdp(text)
}
