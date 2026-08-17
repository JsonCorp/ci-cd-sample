package com.example.cicdsample.ui.call

import com.example.cicdsample.domain.FakeCallRepository
import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallSnapshot
import com.example.cicdsample.domain.model.call.CallStage
import com.example.cicdsample.domain.model.call.PeerAddress
import com.example.cicdsample.domain.model.call.RtpStats
import com.example.cicdsample.domain.usecase.call.BuildLocalSdpUseCase
import com.example.cicdsample.domain.usecase.call.EndCallUseCase
import com.example.cicdsample.domain.usecase.call.GetLocalSessionsUseCase
import com.example.cicdsample.domain.usecase.call.ObserveCallUseCase
import com.example.cicdsample.domain.usecase.call.ParseRemoteSdpUseCase
import com.example.cicdsample.domain.usecase.call.StartCallUseCase
import com.example.cicdsample.domain.usecase.call.ToggleMicUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 통화 ViewModel 테스트.
 *
 * 소켓도 마이크도 없이 JVM 에서 돈다 — 저장소 자리에 :domain 이 공개한 페이크를 꽂기 때문이다.
 * 확인할 것은 "상태가 제대로 합쳐지는가"와 "도메인이 낸 실패가 어떤 문구가 되는가" 둘이다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeCallRepository = FakeCallRepository(),
        localHost: String? = "192.168.0.5",
    ) = CallViewModel(
        observeCall = ObserveCallUseCase(repository),
        getLocalSessions = GetLocalSessionsUseCase(repository),
        buildLocalSdp = BuildLocalSdpUseCase(repository),
        localHostProvider = LocalHostProvider { localHost },
        startCall = StartCallUseCase(repository),
        endCall = EndCallUseCase(repository),
        toggleMic = ToggleMicUseCase(repository),
        parseRemoteSdp = ParseRemoteSdpUseCase(repository),
    )

    /** uiState 는 `WhileSubscribed` 라 구독자가 없으면 갱신되지 않는다. */
    private fun TestScope.subscribe(viewModel: CallViewModel) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
    }

    @Test
    fun `내 주소와 SDP 는 구독 전에도 채워져 있다`() = runTest {
        val viewModel = viewModel()

        // 화면이 열리는 첫 프레임부터 상대에게 건넬 주소가 보여야 한다.
        val state = viewModel.uiState.value
        assertEquals("192.168.0.5:5004", state.localAddress)
        assertTrue(state.localSdp.contains("c=IN IP4 192.168.0.5"))
        assertTrue(state.localSdp.contains("m=audio 5004"))
    }

    @Test
    fun `내 IP 를 찾지 못하면 SDP 도 비어 있다`() = runTest {
        val viewModel = viewModel(localHost = null)

        val state = viewModel.uiState.value
        assertNull(state.localAddress)
        assertEquals("", state.localSdp)
    }

    @Test
    fun `통화를 시작하면 Connecting 과 상대 주소가 상태에 들어온다`() = runTest {
        val repository = FakeCallRepository()
        val viewModel = viewModel(repository)
        subscribe(viewModel)

        viewModel.onHostChange("192.168.0.9")
        viewModel.onStartClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CallStage.Connecting, state.stage)
        assertEquals("192.168.0.9:5004", state.peerLabel)
        assertTrue(state.inProgress)
        assertNull(state.errorMessage)
        // 영상은 아직 보내지 않는다 — 카메라와 H.264 송신은 다음 단계다.
        assertEquals(false, repository.startedWithVideo)
    }

    @Test
    fun `주소를 넣지 않으면 통화 버튼이 잠긴다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        assertFalse(viewModel.uiState.value.canStart)

        viewModel.onHostChange("192.168.0.9")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canStart)
    }

    @Test
    fun `형식이 아닌 주소는 안내 문구가 된다`() = runTest {
        val repository = FakeCallRepository()
        val viewModel = viewModel(repository)
        subscribe(viewModel)

        viewModel.onHostChange("1.2.3")
        viewModel.onStartClick()
        advanceUntilIdle()

        assertEquals("IP 주소나 호스트명 형식이 아닙니다.", viewModel.uiState.value.errorMessage)
        assertEquals(CallStage.Idle, viewModel.uiState.value.stage)
        assertTrue(repository.startedPeers.isEmpty())
    }

    @Test
    fun `포트를 비우면 범위 안내를 낸다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onHostChange("192.168.0.9")
        viewModel.onPortChange("")
        viewModel.onStartClick()
        advanceUntilIdle()

        assertEquals(
            "포트는 1024~65535 사이여야 합니다.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `특권 포트도 같은 범위 안내를 낸다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onHostChange("192.168.0.9")
        viewModel.onPortChange("80")
        viewModel.onStartClick()
        advanceUntilIdle()

        assertEquals(
            "포트는 1024~65535 사이여야 합니다.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `포트 입력은 숫자만 남는다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onPortChange("50a04!")
        advanceUntilIdle()

        assertEquals("5004", viewModel.uiState.value.portInput)
    }

    @Test
    fun `상대 SDP 를 적용하면 주소 입력이 채워진다`() = runTest {
        val repository = FakeCallRepository()
        repository.sdpResult = Result.success(
            PeerAddress(host = "192.168.0.9", audioPort = 6_000, videoPort = null),
        )
        val viewModel = viewModel(repository)
        subscribe(viewModel)

        viewModel.onRemoteSdpChange("v=0\nc=IN IP4 192.168.0.9\nm=audio 6000 RTP/AVP 0")
        viewModel.onApplyRemoteSdp()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("192.168.0.9", state.hostInput)
        assertEquals("6000", state.portInput)
        assertNull(state.errorMessage)
    }

    @Test
    fun `읽을 수 없는 SDP 는 안내 문구를 낸다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onRemoteSdpChange("이건 SDP 가 아니다")
        viewModel.onApplyRemoteSdp()
        advanceUntilIdle()

        assertEquals("상대 SDP 에서 주소를 찾지 못했습니다.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `다시 입력하기 시작하면 에러 문구가 사라진다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)
        viewModel.onHostChange("1.2.3")
        viewModel.onStartClick()
        advanceUntilIdle()
        assertEquals("IP 주소나 호스트명 형식이 아닙니다.", viewModel.uiState.value.errorMessage)

        viewModel.onHostChange("192.168.0.9")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `끊기를 누르면 사용자 끊기로 종료된다`() = runTest {
        val repository = FakeCallRepository()
        val viewModel = viewModel(repository)
        subscribe(viewModel)
        viewModel.onHostChange("192.168.0.9")
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onEndClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CallStage.Ended, state.stage)
        assertEquals(CallEndReason.LocalHangup, state.endReason)
        assertEquals(listOf(CallEndReason.LocalHangup), repository.endReasons)
        assertFalse(state.inProgress)
    }

    @Test
    fun `음소거를 켜고 끈다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)
        viewModel.onHostChange("192.168.0.9")
        viewModel.onStartClick()
        advanceUntilIdle()

        viewModel.onMicToggle(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.micMuted)

        viewModel.onMicToggle(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.micMuted)
    }

    @Test
    fun `마이크 권한이 거부되면 안내 문구가 남는다`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onMicPermissionDenied()
        advanceUntilIdle()

        assertEquals("마이크 권한을 허용해야 통화할 수 있습니다.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `저장소가 통화 성립과 통계를 알리면 그대로 따라온다`() = runTest {
        val repository = FakeCallRepository()
        val viewModel = viewModel(repository)
        subscribe(viewModel)

        // :data 가 첫 패킷을 받아 Active 로 넘긴 상황.
        repository.emit(
            CallSnapshot(
                stage = CallStage.Active,
                peer = PeerAddress(host = "192.168.0.9", audioPort = 5_004, videoPort = null),
                audioStats = RtpStats(expected = 100, received = 98, lost = 2, jitterMs = 3.5),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CallStage.Active, state.stage)
        assertEquals(98L, state.audioStats.received)
        assertEquals(2.0, state.audioStats.lossPercent, 0.01)
    }
}
