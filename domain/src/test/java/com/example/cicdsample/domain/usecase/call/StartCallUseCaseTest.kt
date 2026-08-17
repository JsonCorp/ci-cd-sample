package com.example.cicdsample.domain.usecase.call

import com.example.cicdsample.domain.FakeCallRepository
import com.example.cicdsample.domain.model.call.PeerAddressError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 주소 검증 규칙 테스트.
 *
 * 이 규칙이 도메인에 모여 있어서 소켓도 DNS 도 없이 확인된다 —
 * [StartCallUseCase] 는 형식만 보고, 실제 해석 실패는 전송 계층이 알려 준다.
 *
 * 화면은 여기서 나온 [PeerAddressError] 를 그대로 문구로 바꾼다. 그래서 이 테스트가
 * 곧 "사용자에게 어떤 안내가 뜨는가" 의 근거다.
 */
class StartCallUseCaseTest {

    private val repository = FakeCallRepository()
    private val startCall = StartCallUseCase(repository)

    private fun errorOf(result: Result<*>): PeerAddressError? =
        (result.exceptionOrNull() as? PeerAddressValidationException)?.reason

    @Test
    fun `호스트가 비어 있으면 포트를 보기 전에 거부한다`() = runTest {
        // 포트도 함께 잘못됐지만, 사용자에게 먼저 알려야 하는 것은 빈 호스트다.
        val result = startCall(host = "   ", audioPort = 99, videoPort = null, withVideo = false)

        assertEquals(PeerAddressError.HOST_BLANK, errorOf(result))
        assertTrue(repository.startedPeers.isEmpty())
    }

    @Test
    fun `IPv4 주소로 통화를 시작한다`() = runTest {
        val result = startCall("192.168.0.9", audioPort = 5_004, videoPort = null, withVideo = false)

        assertEquals("192.168.0.9", result.getOrThrow().host)
        assertEquals(5_004, repository.startedPeers.single().audioPort)
        assertEquals(false, repository.startedWithVideo)
    }

    @Test
    fun `앞뒤 공백은 잘라내고 넘긴다`() = runTest {
        val result = startCall("  192.168.0.9  ", audioPort = 5_004, videoPort = null, withVideo = false)

        assertEquals("192.168.0.9", result.getOrThrow().host)
        assertEquals("192.168.0.9", repository.startedPeers.single().host)
    }

    @Test
    fun `IPv6 는 대괄호 표기까지 받는다`() = runTest {
        val plain = startCall("fe80::1", audioPort = 5_004, videoPort = null, withVideo = false)
        val bracketed = startCall("[::1]", audioPort = 5_004, videoPort = null, withVideo = false)

        assertTrue(plain.isSuccess)
        assertTrue(bracketed.isSuccess)
    }

    @Test
    fun `호스트명도 받는다`() = runTest {
        val result = startCall("my-phone.local", audioPort = 5_004, videoPort = null, withVideo = false)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `IP 처럼 보이지만 범위를 넘는 값은 거부한다`() = runTest {
        // 숫자로만 된 이름은 호스트명으로도 인정하지 않는다 — 사용자는 IP 를 넣으려던 것이다.
        val result = startCall("192.168.0.256", audioPort = 5_004, videoPort = null, withVideo = false)

        assertEquals(PeerAddressError.HOST_INVALID, errorOf(result))
    }

    @Test
    fun `자리 수가 모자란 IP 는 거부한다`() = runTest {
        assertEquals(
            PeerAddressError.HOST_INVALID,
            errorOf(startCall("1.2.3", audioPort = 5_004, videoPort = null, withVideo = false)),
        )
    }

    @Test
    fun `앞자리 0 이 붙은 IP 는 거부한다`() = runTest {
        // "010" 을 8진수로 읽는 구현이 섞여 있으면 같은 문자열이 다른 주소가 된다.
        assertEquals(
            PeerAddressError.HOST_INVALID,
            errorOf(startCall("010.1.1.1", audioPort = 5_004, videoPort = null, withVideo = false)),
        )
    }

    @Test
    fun `하이픈으로 시작하거나 밑줄이 든 호스트명은 거부한다`() = runTest {
        assertEquals(
            PeerAddressError.HOST_INVALID,
            errorOf(startCall("-bad.com", audioPort = 5_004, videoPort = null, withVideo = false)),
        )
        assertEquals(
            PeerAddressError.HOST_INVALID,
            errorOf(startCall("my_phone.local", audioPort = 5_004, videoPort = null, withVideo = false)),
        )
    }

    @Test
    fun `특권 포트는 거부한다`() = runTest {
        // 1024 미만은 안드로이드 앱이 바인딩할 수 없다. 상대 포트도 같은 범위로 제한한다.
        assertEquals(
            PeerAddressError.PORT_OUT_OF_RANGE,
            errorOf(startCall("192.168.0.9", audioPort = 1_023, videoPort = null, withVideo = false)),
        )
    }

    @Test
    fun `16비트를 넘는 포트는 거부한다`() = runTest {
        assertEquals(
            PeerAddressError.PORT_OUT_OF_RANGE,
            errorOf(startCall("192.168.0.9", audioPort = 65_536, videoPort = null, withVideo = false)),
        )
    }

    @Test
    fun `영상을 쓰는데 영상 포트가 없으면 거부한다`() = runTest {
        assertEquals(
            PeerAddressError.PORT_OUT_OF_RANGE,
            errorOf(startCall("192.168.0.9", audioPort = 5_004, videoPort = null, withVideo = true)),
        )
    }

    @Test
    fun `오디오와 영상 포트가 같으면 거부한다`() = runTest {
        assertEquals(
            PeerAddressError.PORT_CONFLICT,
            errorOf(startCall("192.168.0.9", audioPort = 5_004, videoPort = 5_004, withVideo = true)),
        )
    }

    @Test
    fun `영상을 쓰지 않으면 영상 포트는 보지도 않는다`() = runTest {
        // 잘못된 영상 포트가 남아 있어도 오디오 통화는 시작돼야 한다.
        val result = startCall("192.168.0.9", audioPort = 5_004, videoPort = 42, withVideo = false)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().videoPort)
        assertNull(repository.startedPeers.single().videoPort)
    }

    @Test
    fun `영상을 쓰면 두 포트가 그대로 전달된다`() = runTest {
        val result = startCall("192.168.0.9", audioPort = 5_004, videoPort = 5_006, withVideo = true)

        assertEquals(5_006, result.getOrThrow().videoPort)
        assertEquals(true, repository.startedWithVideo)
    }

    @Test
    fun `검증에 실패하면 저장소를 건드리지 않는다`() = runTest {
        startCall("", audioPort = 5_004, videoPort = null, withVideo = false)
        startCall("192.168.0.9", audioPort = 80, videoPort = null, withVideo = false)

        assertTrue(repository.startedPeers.isEmpty())
        assertNull(repository.startedWithVideo)
    }
}
