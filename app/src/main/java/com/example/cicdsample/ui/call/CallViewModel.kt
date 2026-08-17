package com.example.cicdsample.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cicdsample.domain.model.call.MediaKind
import com.example.cicdsample.domain.model.call.PeerAddressError
import com.example.cicdsample.domain.usecase.call.BuildLocalSdpUseCase
import com.example.cicdsample.domain.usecase.call.EndCallUseCase
import com.example.cicdsample.domain.usecase.call.GetLocalSessionsUseCase
import com.example.cicdsample.domain.usecase.call.ObserveCallUseCase
import com.example.cicdsample.domain.usecase.call.ParseRemoteSdpUseCase
import com.example.cicdsample.domain.usecase.call.PeerAddressValidationException
import com.example.cicdsample.domain.usecase.call.StartCallUseCase
import com.example.cicdsample.domain.usecase.call.ToggleMicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 통화 화면의 상태를 만들고 사용자의 의도를 유스케이스로 넘긴다.
 *
 * 주소 검증도, 상태 전이도, 타임아웃 판정도 여기에 없다 — 각각 :domain 과 :data 에 있다.
 * 그래서 이 클래스에 남는 것은 조립과 문구 변환뿐이고, 테스트는 페이크 저장소 하나로 끝난다.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    observeCall: ObserveCallUseCase,
    getLocalSessions: GetLocalSessionsUseCase,
    buildLocalSdp: BuildLocalSdpUseCase,
    localHostProvider: LocalHostProvider,
    private val startCall: StartCallUseCase,
    private val endCall: EndCallUseCase,
    private val toggleMic: ToggleMicUseCase,
    private val parseRemoteSdp: ParseRemoteSdpUseCase,
) : ViewModel() {

    private data class FormState(
        val host: String = "",
        val port: String = CallUiState.DEFAULT_PEER_PORT,
        val remoteSdp: String = "",
        val errorMessage: String? = null,
    )

    private val form = MutableStateFlow(FormState())

    /**
     * 내 주소와 SDP 는 통화보다 먼저 정해진다 — 사용자가 이 텍스트를 상대에게 건네야
     * 상대가 어디로 보낼지 알 수 있다. 통화 중에 바뀌지 않으므로 한 번만 읽는다.
     *
     * 여기서 :data 가 UDP 포트를 바인딩하지만 로컬 syscall 이라 DNS 조회도 왕복도 없다 —
     * 메인 스레드를 막지 않는다.
     */
    private val localHost: String? = localHostProvider.localHost()

    private val localSdp: String = localHost?.let { buildLocalSdp(it) } ?: ""

    private val localAddress: String? = localHost?.let { host ->
        getLocalSessions()
            .firstOrNull { it.kind == MediaKind.AUDIO }
            ?.let { session -> "$host:${session.localPort}" }
    }

    val uiState: StateFlow<CallUiState> = combine(
        observeCall(),
        form,
    ) { snapshot, formState ->
        CallUiState(
            stage = snapshot.stage,
            hostInput = formState.host,
            portInput = formState.port,
            remoteSdpInput = formState.remoteSdp,
            localSdp = localSdp,
            localAddress = localAddress,
            peerLabel = snapshot.peer?.let { "${it.host}:${it.audioPort}" },
            micMuted = snapshot.micMuted,
            audioStats = snapshot.audioStats,
            endReason = snapshot.endReason,
            errorMessage = formState.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        // 구독 전 첫 프레임에도 내 주소는 보여야 한다.
        initialValue = CallUiState(localSdp = localSdp, localAddress = localAddress),
    )

    fun onHostChange(value: String) {
        // 다시 입력하기 시작하면 이전 에러는 치운다.
        form.update { it.copy(host = value, errorMessage = null) }
    }

    fun onPortChange(value: String) {
        // 숫자만 남긴다. 키보드 종류에 기대지 않고 붙여넣기까지 막는다.
        form.update { it.copy(port = value.filter(Char::isDigit), errorMessage = null) }
    }

    fun onRemoteSdpChange(value: String) {
        form.update { it.copy(remoteSdp = value, errorMessage = null) }
    }

    /**
     * 상대가 준 SDP 에서 주소를 뽑아 입력칸을 채운다.
     *
     * 사람이 IP 와 포트를 눈으로 옮겨 적다 틀리는 것을 막는 것이 이 버튼의 존재 이유다.
     */
    fun onApplyRemoteSdp() {
        parseRemoteSdp(form.value.remoteSdp)
            .onSuccess { peer ->
                form.update {
                    it.copy(
                        host = peer.host,
                        port = peer.audioPort.toString(),
                        errorMessage = null,
                    )
                }
            }
            .onFailure {
                form.update { it.copy(errorMessage = "상대 SDP 에서 주소를 찾지 못했습니다.") }
            }
    }

    fun onStartClick() {
        val current = form.value
        val port = current.port.toIntOrNull()
        if (port == null) {
            form.update { it.copy(errorMessage = PORT_RANGE_MESSAGE) }
            return
        }

        viewModelScope.launch {
            // 영상은 아직 보내지 않는다 — 카메라와 H.264 송신은 다음 단계다.
            startCall(host = current.host, audioPort = port, videoPort = null, withVideo = false)
                .onFailure { error -> form.update { it.copy(errorMessage = error.toMessage()) } }
        }
    }

    fun onEndClick() {
        viewModelScope.launch { endCall() }
    }

    fun onMicToggle(muted: Boolean) {
        viewModelScope.launch { toggleMic(muted) }
    }

    /**
     * 마이크 권한이 거부됐다. 화면이 알려 준다 —
     * 권한 API 는 Android 것이라 ViewModel 이 아니라 [CallRoute] 가 다룬다.
     */
    fun onMicPermissionDenied() {
        form.update { it.copy(errorMessage = "마이크 권한을 허용해야 통화할 수 있습니다.") }
    }

    private fun Throwable.toMessage(): String =
        when ((this as? PeerAddressValidationException)?.reason) {
            PeerAddressError.HOST_BLANK -> "상대 주소를 입력해 주세요."
            PeerAddressError.HOST_INVALID -> "IP 주소나 호스트명 형식이 아닙니다."
            PeerAddressError.PORT_OUT_OF_RANGE -> PORT_RANGE_MESSAGE
            PeerAddressError.PORT_CONFLICT -> "오디오와 영상 포트가 같습니다."
            null -> "통화를 시작하지 못했습니다."
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        val PORT_RANGE_MESSAGE =
            "포트는 ${StartCallUseCase.MIN_PORT}~${StartCallUseCase.MAX_PORT} 사이여야 합니다."
    }
}
