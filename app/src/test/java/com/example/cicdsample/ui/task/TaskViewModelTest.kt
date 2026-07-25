package com.example.cicdsample.ui.task

import com.example.cicdsample.domain.FakeTaskRepository
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.TaskFilter
import com.example.cicdsample.domain.task
import com.example.cicdsample.domain.usecase.AddTaskUseCase
import com.example.cicdsample.domain.usecase.ClearCompletedUseCase
import com.example.cicdsample.domain.usecase.DeleteTaskUseCase
import com.example.cicdsample.domain.usecase.GetTaskStatsUseCase
import com.example.cicdsample.domain.usecase.ObserveTasksUseCase
import com.example.cicdsample.domain.usecase.ToggleTaskUseCase
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * ViewModel 테스트도 JVM 에서 돈다 — Android 프레임워크 타입을 쓰지 않기 때문이다.
 * 저장소 자리에는 :domain 테스트에서 쓰던 페이크를 그대로 꽂는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeTaskRepository) = TaskViewModel(
        observeTasks = ObserveTasksUseCase(repository),
        getTaskStats = GetTaskStatsUseCase(repository),
        addTask = AddTaskUseCase(repository),
        toggleTask = ToggleTaskUseCase(repository),
        deleteTask = DeleteTaskUseCase(repository),
        clearCompleted = ClearCompletedUseCase(repository),
    )

    /**
     * uiState 는 `WhileSubscribed` 라 구독자가 없으면 갱신되지 않는다.
     * 테스트가 끝나면 함께 정리되는 [TestScope.backgroundScope] 로 구독만 열어 둔다.
     */
    private fun TestScope.subscribe(viewModel: TaskViewModel) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
    }

    @Test
    fun `유효한 제목을 추가하면 목록과 통계가 함께 갱신되고 입력이 비워진다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        subscribe(viewModel)

        viewModel.onTitleChange("장보기")
        viewModel.onAddClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("장보기"), state.tasks.map { it.title })
        assertEquals(1, state.stats.total)
        assertEquals("", state.inputTitle)
        assertNull(state.errorMessage)
    }

    @Test
    fun `중복 제목을 추가하면 에러 문구가 뜨고 입력은 유지된다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository(listOf(task(id = 1, title = "장보기"))))
        subscribe(viewModel)

        viewModel.onTitleChange("장보기")
        viewModel.onAddClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("이미 같은 할 일이 있습니다.", state.errorMessage)
        assertEquals("장보기", state.inputTitle)
        assertEquals(1, state.tasks.size)
    }

    @Test
    fun `짧은 제목은 최소 길이 안내를 낸다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        subscribe(viewModel)

        viewModel.onTitleChange("가")
        viewModel.onAddClick()
        advanceUntilIdle()

        assertEquals("2자 이상 입력해 주세요.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `너무 긴 제목은 최대 길이 안내를 낸다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        subscribe(viewModel)

        viewModel.onTitleChange("가".repeat(41))
        viewModel.onAddClick()
        advanceUntilIdle()

        assertEquals("40자까지 입력할 수 있습니다.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `다시 입력하기 시작하면 에러 문구가 사라진다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        subscribe(viewModel)
        viewModel.onTitleChange("가")
        viewModel.onAddClick()
        advanceUntilIdle()
        assertEquals("2자 이상 입력해 주세요.", viewModel.uiState.value.errorMessage)

        viewModel.onTitleChange("가나")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `필터를 바꾸면 목록이 다시 계산된다`() = runTest {
        val viewModel = viewModel(
            FakeTaskRepository(
                listOf(
                    task(id = 1, title = "완료된 일", done = true),
                    task(id = 2, title = "남은 일", done = false),
                ),
            ),
        )
        subscribe(viewModel)
        assertEquals(2, viewModel.uiState.value.tasks.size)

        viewModel.onFilterChange(TaskFilter.ACTIVE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TaskFilter.ACTIVE, state.filter)
        assertEquals(listOf("남은 일"), state.tasks.map { it.title })
    }

    @Test
    fun `체크하면 완료율이 올라간다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository(listOf(task(id = 1, done = false))))
        subscribe(viewModel)
        assertEquals(0, viewModel.uiState.value.stats.completionRate)

        viewModel.onToggle(1L)
        advanceUntilIdle()

        assertEquals(100, viewModel.uiState.value.stats.completionRate)
    }

    @Test
    fun `완료한 할 일 지우기를 누르면 미완료만 남는다`() = runTest {
        val viewModel = viewModel(
            FakeTaskRepository(
                listOf(
                    task(id = 1, title = "완료된 일", done = true),
                    task(id = 2, title = "남은 일", done = false),
                ),
            ),
        )
        subscribe(viewModel)

        viewModel.onClearCompleted()
        advanceUntilIdle()

        assertEquals(listOf("남은 일"), viewModel.uiState.value.tasks.map { it.title })
    }

    @Test
    fun `삭제하면 목록에서 빠진다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository(listOf(task(id = 1, title = "지울 일"))))
        subscribe(viewModel)

        viewModel.onDelete(1L)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.uiState.value.tasks.map { it.title })
    }

    @Test
    fun `우선순위를 고르면 그 값으로 저장되고 다음 입력에도 유지된다`() = runTest {
        val viewModel = viewModel(FakeTaskRepository())
        subscribe(viewModel)

        viewModel.onPriorityChange(Priority.HIGH)
        viewModel.onTitleChange("급한 일")
        viewModel.onAddClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Priority.HIGH, state.tasks.single().priority)
        assertEquals(Priority.HIGH, state.inputPriority)
    }
}
