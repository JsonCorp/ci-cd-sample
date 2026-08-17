package com.example.cicdsample.ui.call

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallStage
import com.example.cicdsample.domain.model.call.RtpStats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 통화 화면 Compose UI 테스트.
 *
 * [CallScreen] 이 stateless 라 Activity 도, Hilt 도, 마이크 권한도 띄우지 않는다 —
 * 통화 중·종료 같은 상태를 손으로 만들어 넣기만 하면 된다.
 */
@RunWith(AndroidJUnit4::class)
class CallScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val localState = CallUiState(
        localAddress = "192.168.0.5:5004",
        localSdp = "v=0\nc=IN IP4 192.168.0.5\nm=audio 5004 RTP/AVP 0\n",
    )

    @Test
    fun 대기_상태에서_내_주소와_SDP_를_보여준다() {
        setContent(localState)

        composeRule.onNodeWithTag("text_call_stage").assertIsDisplayed()
        composeRule.onNodeWithText("통화 대기").assertIsDisplayed()
        composeRule.onNodeWithTag("text_local_address").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.0.5:5004").assertIsDisplayed()
        composeRule.onNodeWithTag("text_local_sdp").assertIsDisplayed()
    }

    @Test
    fun 내_주소를_못_찾으면_안내가_뜨고_SDP_는_없다() {
        setContent(CallUiState())

        composeRule.onNodeWithTag("text_local_missing").assertIsDisplayed()
        composeRule.onNodeWithTag("text_local_address").assertDoesNotExist()
    }

    @Test
    fun 상대_주소가_비면_통화_버튼이_잠긴다() {
        setContent(localState)

        composeRule.onNodeWithTag("btn_call_start").assertIsNotEnabled()
        // 통화 전에는 끊기와 음소거도 잠겨 있다.
        composeRule.onNodeWithTag("btn_call_end").assertIsNotEnabled()
        composeRule.onNodeWithTag("btn_mic").assertIsNotEnabled()
    }

    @Test
    fun 상대_주소를_채우면_통화_버튼이_열린다() {
        setContent(localState.copy(hostInput = "192.168.0.9"))

        composeRule.onNodeWithTag("btn_call_start").assertIsEnabled()
    }

    @Test
    fun 통화_중에는_끊기와_음소거가_열리고_시작은_잠긴다() {
        setContent(
            localState.copy(
                stage = CallStage.Active,
                hostInput = "192.168.0.9",
                peerLabel = "192.168.0.9:5004",
            ),
        )

        composeRule.onNodeWithText("통화 중").assertIsDisplayed()
        composeRule.onNodeWithTag("text_call_peer").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_call_end").assertIsEnabled()
        composeRule.onNodeWithTag("btn_mic").assertIsEnabled()
        composeRule.onNodeWithTag("btn_call_start").assertIsNotEnabled()
    }

    @Test
    fun 패킷을_받기_전에는_통계를_보여주지_않는다() {
        // 0 으로 채워진 표는 정보가 아니다.
        setContent(localState.copy(stage = CallStage.Active))

        composeRule.onNodeWithTag("text_call_stats").assertDoesNotExist()
    }

    @Test
    fun 통계는_수신_유실_지터_세_가지로_요약된다() {
        setContent(
            localState.copy(
                stage = CallStage.Active,
                audioStats = RtpStats(expected = 100, received = 98, lost = 2, jitterMs = 3.45),
            ),
        )

        composeRule.onNodeWithTag("text_call_stats").assertIsDisplayed()
        composeRule.onNodeWithText("수신 98/100 · 유실 2.0% · 지터 3.5ms").assertIsDisplayed()
    }

    @Test
    fun 종료되면_이유를_보여준다() {
        setContent(
            localState.copy(stage = CallStage.Ended, endReason = CallEndReason.ReceiveTimeout),
        )

        composeRule.onNodeWithTag("text_call_end").assertIsDisplayed()
        composeRule.onNodeWithText("상대 패킷이 오지 않아 끊겼습니다.").assertIsDisplayed()
    }

    @Test
    fun 연결_중_상태는_종료_이유를_보여주지_않는다() {
        // 지난 통화의 종료 이유가 남아 있어도 새 통화 화면에 잔상으로 뜨면 안 된다.
        setContent(
            localState.copy(stage = CallStage.Connecting, endReason = CallEndReason.LocalHangup),
        )

        composeRule.onNodeWithTag("text_call_end").assertDoesNotExist()
    }

    @Test
    fun 에러_문구를_보여준다() {
        setContent(localState.copy(errorMessage = "IP 주소나 호스트명 형식이 아닙니다."))

        composeRule.onNodeWithTag("text_call_error").assertIsDisplayed()
        composeRule.onNodeWithText("IP 주소나 호스트명 형식이 아닙니다.").assertIsDisplayed()
    }

    @Test
    fun 상대_IP_를_입력하면_onHostChange_로_전달된다() {
        var typed = ""
        setContent(localState, onHostChange = { typed = it })

        composeRule.onNodeWithTag("input_peer_host").performTextInput("192.168.0.9")

        assertEquals("192.168.0.9", typed)
    }

    @Test
    fun 통화_버튼을_누르면_onStartClick_이_호출된다() {
        var clicks = 0
        setContent(localState.copy(hostInput = "192.168.0.9"), onStartClick = { clicks++ })

        composeRule.onNodeWithTag("btn_call_start").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun 음소거_버튼은_현재_상태의_반대값을_넘긴다() {
        var requested: Boolean? = null
        setContent(
            localState.copy(stage = CallStage.Active, micMuted = false),
            onMicToggle = { requested = it },
        )

        composeRule.onNodeWithTag("btn_mic").performClick()

        assertEquals(true, requested)
    }

    @Test
    fun 붙여넣은_SDP_가_없으면_주소_채우기_버튼이_잠긴다() {
        setContent(localState)

        composeRule.onNodeWithTag("btn_apply_sdp").assertIsNotEnabled()
    }

    @Test
    fun SDP_를_붙여넣으면_주소_채우기_버튼이_열린다() {
        setContent(localState.copy(remoteSdpInput = "v=0"))

        composeRule.onNodeWithTag("btn_apply_sdp").assertIsEnabled()
    }

    private fun setContent(
        state: CallUiState,
        onHostChange: (String) -> Unit = {},
        onStartClick: () -> Unit = {},
        onMicToggle: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            CallScreen(
                state = state,
                onHostChange = onHostChange,
                onPortChange = {},
                onRemoteSdpChange = {},
                onApplyRemoteSdp = {},
                onStartClick = onStartClick,
                onEndClick = {},
                onMicToggle = onMicToggle,
            )
        }
    }
}
