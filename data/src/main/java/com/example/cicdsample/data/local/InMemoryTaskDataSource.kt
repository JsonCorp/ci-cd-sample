package com.example.cicdsample.data.local

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 프로세스 메모리에만 사는 로컬 데이터 소스.
 *
 * 이 샘플의 주제는 파이프라인이라 Room 대신 메모리를 쓴다. 저장 방식이 바뀌어도
 * [com.example.cicdsample.domain.repository.TaskRepository] 인터페이스는 그대로이므로
 * 도메인과 UI 는 한 줄도 바뀌지 않는다.
 *
 * id/order 발급이 겹치지 않도록 [Mutex] 로 쓰기를 직렬화한다.
 */
@Singleton
class InMemoryTaskDataSource @Inject constructor() {

    private val state = MutableStateFlow<List<Task>>(emptyList())
    private val mutex = Mutex()
    private var nextId = 1L
    private var nextOrder = 0

    val tasks: Flow<List<Task>> = state.asStateFlow()

    suspend fun insert(title: String, priority: Priority): Task = mutex.withLock {
        val task = Task(
            id = nextId++,
            title = title,
            priority = priority,
            done = false,
            order = nextOrder++,
        )
        state.update { it + task }
        task
    }

    suspend fun updateDone(id: Long, done: Boolean): Boolean = mutex.withLock {
        if (state.value.none { it.id == id }) return false
        state.update { tasks -> tasks.map { if (it.id == id) it.copy(done = done) else it } }
        true
    }

    suspend fun delete(id: Long): Boolean = mutex.withLock {
        if (state.value.none { it.id == id }) return false
        state.update { tasks -> tasks.filterNot { it.id == id } }
        true
    }

    suspend fun deleteDone(): Int = mutex.withLock {
        val removed = state.value.count { it.done }
        state.update { tasks -> tasks.filterNot { it.done } }
        removed
    }
}
