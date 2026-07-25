package com.example.cicdsample.data.repository

import app.cash.turbine.test
import com.example.cicdsample.data.local.InMemoryTaskDataSource
import com.example.cicdsample.domain.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 데이터 계층 테스트. Android 프레임워크를 전혀 쓰지 않으므로 에뮬레이터 없이 JVM 에서 돈다.
 */
class DefaultTaskRepositoryTest {

    private fun repository() = DefaultTaskRepository(InMemoryTaskDataSource())

    @Test
    fun `추가한 항목에 순차 id 와 order 가 붙는다`() = runTest {
        val repository = repository()

        val first = repository.addTask("첫 번째", Priority.NORMAL)
        val second = repository.addTask("두 번째", Priority.HIGH)

        assertEquals(1L, first.id)
        assertEquals(0, first.order)
        assertEquals(2L, second.id)
        assertEquals(1, second.order)
    }

    @Test
    fun `추가하면 Flow 가 새 목록을 방출한다`() = runTest {
        val repository = repository()

        repository.observeTasks().test {
            assertEquals(0, awaitItem().size)

            repository.addTask("새 할 일", Priority.NORMAL)

            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDone 은 해당 항목만 바꾼다`() = runTest {
        val repository = repository()
        val target = repository.addTask("대상", Priority.NORMAL)
        repository.addTask("나머지", Priority.NORMAL)

        val updated = repository.setDone(target.id, true)

        val tasks = repository.observeTasks().first()
        assertTrue(updated)
        assertTrue(tasks.single { it.id == target.id }.done)
        assertFalse(tasks.single { it.id != target.id }.done)
    }

    @Test
    fun `없는 id 에 대한 setDone 과 delete 는 false 를 낸다`() = runTest {
        val repository = repository()

        assertFalse(repository.setDone(999L, true))
        assertFalse(repository.deleteTask(999L))
    }

    @Test
    fun `deleteCompleted 는 완료된 개수만큼 지운다`() = runTest {
        val repository = repository()
        val a = repository.addTask("완료 A", Priority.NORMAL)
        repository.addTask("미완료", Priority.NORMAL)
        val c = repository.addTask("완료 C", Priority.NORMAL)
        repository.setDone(a.id, true)
        repository.setDone(c.id, true)

        val removed = repository.deleteCompleted()

        assertEquals(2, removed)
        assertEquals(listOf("미완료"), repository.observeTasks().first().map { it.title })
    }

    @Test
    fun `삭제한 뒤 새로 추가해도 id 는 재사용되지 않는다`() = runTest {
        val repository = repository()
        val first = repository.addTask("지울 항목", Priority.NORMAL)
        repository.deleteTask(first.id)

        val next = repository.addTask("새 항목", Priority.NORMAL)

        assertEquals(2L, next.id)
    }
}
