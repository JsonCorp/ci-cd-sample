package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.FakeTaskRepository
import com.example.cicdsample.domain.task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleTaskUseCaseTest {

    @Test
    fun `미완료 항목을 누르면 완료가 된다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1, done = false)))

        val result = ToggleTaskUseCase(repository)(1L)

        assertEquals(true, result.getOrThrow())
        assertTrue(repository.current().single().done)
    }

    @Test
    fun `완료 항목을 다시 누르면 미완료로 돌아온다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1, done = true)))

        val result = ToggleTaskUseCase(repository)(1L)

        assertEquals(false, result.getOrThrow())
        assertFalse(repository.current().single().done)
    }

    @Test
    fun `없는 id 는 실패로 알린다`() = runTest {
        val repository = FakeTaskRepository(listOf(task(id = 1)))

        val result = ToggleTaskUseCase(repository)(999L)

        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertFalse(repository.current().single().done)
    }
}
