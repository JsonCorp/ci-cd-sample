package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.FakeTaskRepository
import com.example.cicdsample.domain.task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteTaskUseCaseTest {

    @Test
    fun `있는 항목은 지운다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1), task(id = 2)))

        val result = DeleteTaskUseCase(repository)(1L)

        assertTrue(result.isSuccess)
        assertEquals(listOf(2L), repository.current().map { it.id })
    }

    @Test
    fun `없는 항목은 실패로 알린다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1)))

        val result = DeleteTaskUseCase(repository)(999L)

        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertEquals(1, repository.current().size)
    }
}

class ClearCompletedUseCaseTest {

    @Test
    fun `완료된 항목만 지우고 지운 개수를 돌려준다`() = runTest {
        val repository = FakeTaskRepository(
            listOf(
                task(id = 1, done = true),
                task(id = 2, done = false),
                task(id = 3, done = true),
            ),
        )

        val removed = ClearCompletedUseCase(repository)()

        assertEquals(2, removed)
        assertEquals(listOf(2L), repository.current().map { it.id })
    }

    @Test
    fun `지울 게 없으면 0 이고 실패가 아니다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1, done = false)))

        val removed = ClearCompletedUseCase(repository)()

        assertEquals(0, removed)
        assertEquals(1, repository.current().size)
    }
}
