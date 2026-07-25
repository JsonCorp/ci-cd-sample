package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.FakeTaskRepository
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddTaskUseCaseTest {

    private fun useCase(repository: FakeTaskRepository) = AddTaskUseCase(repository)

    @Test
    fun `유효한 제목이면 저장하고 저장된 항목을 돌려준다`() = runTest {
        val repository = FakeTaskRepository()

        val result = useCase(repository)("장보기", Priority.HIGH)

        assertTrue(result.isSuccess)
        assertEquals("장보기", result.getOrThrow().title)
        assertEquals(Priority.HIGH, result.getOrThrow().priority)
        assertEquals(1, repository.current().size)
    }

    @Test
    fun `앞뒤 공백은 잘라내고 저장한다`() = runTest {
        val repository = FakeTaskRepository()

        val result = useCase(repository)("   장보기   ")

        assertEquals("장보기", result.getOrThrow().title)
    }

    @Test
    fun `공백만 있으면 BLANK 로 거부한다`() = runTest {
        val repository = FakeTaskRepository()

        val result = useCase(repository)("   ")

        assertEquals(TitleError.BLANK, result.errorReason())
        assertTrue(repository.current().isEmpty())
    }

    @Test
    fun `한 글자면 TOO_SHORT 로 거부한다`() = runTest {
        val result = useCase(FakeTaskRepository())("가")

        assertEquals(TitleError.TOO_SHORT, result.errorReason())
    }

    @Test
    fun `경계값 두 글자는 통과한다`() = runTest {
        val result = useCase(FakeTaskRepository())("가나")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `경계값 40자는 통과하고 41자는 TOO_LONG 이다`() = runTest {
        val ok = useCase(FakeTaskRepository())("가".repeat(AddTaskUseCase.MAX_TITLE_LENGTH))
        val tooLong = useCase(FakeTaskRepository())("가".repeat(AddTaskUseCase.MAX_TITLE_LENGTH + 1))

        assertTrue(ok.isSuccess)
        assertEquals(TitleError.TOO_LONG, tooLong.errorReason())
    }

    @Test
    fun `같은 제목은 DUPLICATE 로 거부한다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1, title = "장보기")))

        val result = useCase(repository)("장보기")

        assertEquals(TitleError.DUPLICATE, result.errorReason())
        assertEquals(1, repository.current().size)
    }

    @Test
    fun `대소문자만 다른 제목도 DUPLICATE 로 본다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1, title = "Buy Milk")))

        val result = useCase(repository)("buy milk")

        assertEquals(TitleError.DUPLICATE, result.errorReason())
    }

    @Test
    fun `가운데 공백 개수만 다른 제목도 DUPLICATE 로 본다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1, title = "우유 사기")))

        val result = useCase(repository)("우유    사기")

        assertEquals(TitleError.DUPLICATE, result.errorReason())
    }

    @Test
    fun `우선순위를 지정하지 않으면 NORMAL 이다`() = runTest {
        val result = useCase(FakeTaskRepository())("기본 우선순위")

        assertEquals(Priority.NORMAL, result.getOrThrow().priority)
    }

    private fun Result<*>.errorReason(): TitleError =
        (exceptionOrNull() as TitleValidationException).reason
}
