package com.example.cicdsample.domain.usecase

import app.cash.turbine.test
import com.example.cicdsample.domain.FakeTaskRepository
import com.example.cicdsample.domain.model.TaskStats
import com.example.cicdsample.domain.task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetTaskStatsUseCaseTest {

    @Test
    fun `빈 목록이면 0으로 나누지 않고 EMPTY 를 낸다`() = runTest {
        val stats = GetTaskStatsUseCase(FakeTaskRepository())().first()

        assertEquals(TaskStats.EMPTY, stats)
    }

    @Test
    fun `절반이 완료면 50 퍼센트다`() = runTest {
        val repository = FakeTaskRepository(
            listOf(task(id = 1, done = true), task(id = 2, done = false)),
        )

        val stats = GetTaskStatsUseCase(repository)().first()

        assertEquals(TaskStats(total = 2, done = 1, completionRate = 50), stats)
    }

    @Test
    fun `나누어떨어지지 않으면 반올림한다`() = runTest {
        // 2/3 = 66.67% -> 67
        val repository = FakeTaskRepository(
            listOf(task(id = 1, done = true), task(id = 2, done = true), task(id = 3, done = false)),
        )

        val stats = GetTaskStatsUseCase(repository)().first()

        assertEquals(67, stats.completionRate)
    }

    @Test
    fun `전부 완료면 100 퍼센트다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1, done = true)))

        val stats = GetTaskStatsUseCase(repository)().first()

        assertEquals(100, stats.completionRate)
    }

    @Test
    fun `항목이 추가되면 통계가 다시 방출된다`() = runTest {
        val repository = FakeTaskRepository()
        val useCase = GetTaskStatsUseCase(repository)

        useCase().test {
            assertEquals(TaskStats.EMPTY, awaitItem())

            repository.addTask("새 할 일", com.example.cicdsample.domain.model.Priority.NORMAL)

            assertEquals(TaskStats(total = 1, done = 0, completionRate = 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
