package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.FakeTaskRepository
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.TaskFilter
import com.example.cicdsample.domain.task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveTasksUseCaseTest {

    private val tasks = listOf(
        task(id = 1, title = "낮은 순위", priority = Priority.LOW, order = 0),
        task(id = 2, title = "완료된 일", priority = Priority.HIGH, done = true, order = 1),
        task(id = 3, title = "급한 일", priority = Priority.HIGH, order = 2),
        task(id = 4, title = "보통 일", priority = Priority.NORMAL, order = 3),
        task(id = 5, title = "먼저 등록한 급한 일", priority = Priority.HIGH, order = 4),
    )

    private fun useCase() = ObserveTasksUseCase(FakeTaskRepository(tasks))

    @Test
    fun `ALL 은 완료 항목까지 포함한다`() = runTest {
        val result = useCase()(TaskFilter.ALL).first()

        assertEquals(5, result.size)
    }

    @Test
    fun `ACTIVE 는 미완료만 남긴다`() = runTest {
        val result = useCase()(TaskFilter.ACTIVE).first()

        assertEquals(4, result.size)
        assertEquals(emptyList<Long>(), result.filter { it.done }.map { it.id })
    }

    @Test
    fun `DONE 은 완료만 남긴다`() = runTest {
        val result = useCase()(TaskFilter.DONE).first()

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `완료 항목은 항상 목록 맨 아래로 내려간다`() = runTest {
        val result = useCase()(TaskFilter.ALL).first()

        assertEquals(2L, result.last().id)
    }

    @Test
    fun `우선순위가 높은 것이 먼저 오고 같으면 먼저 등록한 것이 먼저 온다`() = runTest {
        val result = useCase()(TaskFilter.ACTIVE).first()

        // HIGH(order 2) -> HIGH(order 4) -> NORMAL -> LOW
        assertEquals(listOf(3L, 5L, 4L, 1L), result.map { it.id })
    }

    @Test
    fun `빈 저장소면 빈 목록을 낸다`() = runTest {
        val result = ObserveTasksUseCase(FakeTaskRepository())(TaskFilter.ALL).first()

        assertEquals(emptyList<Long>(), result.map { it.id })
    }
}
