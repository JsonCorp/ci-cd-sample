package com.example.cicdsample.ui.call

import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallStage
import com.example.cicdsample.domain.model.call.RtpStats

/**
 * 통화 화면이 그리는 데 필요한 전부.
 *
 * 이 하나만 만들면 어떤 통화 상태든 렌더링할 수 있어서 Compose UI 테스트가
 * ViewModel 도 Hilt 도 소켓도 없이 돌아간다.
 *
 * @param localSdp 상대에게 건넬 내 SDP. 포트를 열지 못했으면 빈 문자열이다.
 * @param localAddress "192.168.0.5:5004" 요약. SDP 전체를 옮기지 않고 말로 알려 줄 때 쓴다.
 * @param peerLabel 통화 중인 상대 주소. 통화 전에는 null.
 */
data class CallUiState(
    val stage: CallStage = CallStage.Idle,
    val hostInput: String = "",
    val portInput: String = DEFAULT_PEER_PORT,
    val remoteSdpInput: String = "",
    val localSdp: String = "",
    val localAddress: String? = null,
    val peerLabel: String? = null,
    val micMuted: Boolean = false,
    val audioStats: RtpStats = RtpStats.EMPTY,
    val endReason: CallEndReason? = null,
    val errorMessage: String? = null,
) {
    /** 통화가 진행 중인지. 버튼을 켜고 끄는 기준이다. */
    val inProgress: Boolean
        get() = stage == CallStage.Connecting || stage == CallStage.Active

    /**
     * 통화를 걸 수 있는지.
     *
     * 주소 형식 검증은 하지 않는다 — 그것은 도메인(`StartCallUseCase`)의 일이고,
     * 여기서 같은 규칙을 한 번 더 쓰면 두 곳이 어긋난다. 여기서는 "빈칸인가"만 본다.
     */
    val canStart: Boolean
        get() = !inProgress && hostInput.isNotBlank() && portInput.isNotBlank()

    companion object {
        /**
         * 상대 포트 기본값. :data 가 자기 오디오 포트로 먼저 시도하는 값과 같다 —
         * 양쪽이 기본값을 그대로 두면 포트를 맞추는 수고가 없어진다.
         */
        const val DEFAULT_PEER_PORT = "5004"
    }
}
