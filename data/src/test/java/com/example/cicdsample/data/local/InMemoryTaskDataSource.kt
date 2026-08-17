package com.example.cicdsample.data.local

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [TaskLocalDataSource] 의 인메모리 구현. **테스트 전용**이다.
 *
 * 프로덕션은 Room([RoomTaskDataSource])을 쓴다. 이 페이크가 있어서
 * [com.example.cicdsample.data.repository.DefaultTaskRepository] 의 동작을
 * 에뮬레이터 없이 JVM 에서 검증할 수 있다 — Room 자체의 정확성(스키마·마이그레이션·쿼리)은
 * `src/androidTest` 의 계측 테스트가 따로 본다.
 *
 * src/main 이 아니라 여기 있는 이유: 프로덕션에서 쓰이지 않으므로 APK 에 실릴 이유가 없고,
 * main 에 두면 커버리지 분모에 잡혀 수치가 왜곡된다.
 *
 * id/order 발급이 겹치지 않도록 [Mutex] 로 쓰기를 직렬화한다.
 */
class InMemoryTaskDataSource : TaskLocalDataSource {

    private val state = MutableStateFlow<List<Task>>(emptyList())
    private val mutex = Mutex()
    private var nextId = 1L
    private var nextOrder = 0

    override val tasks: Flow<List<Task>> = state.asStateFlow()

    override suspend fun insert(title: String, priority: Priority, dueDate: Long?): Task =
        mutex.withLock {
            val task = Task(
                id = nextId++,
                title = title,
                priority = priority,
                done = false,
                order = nextOrder++,
                dueDate = dueDate,
            )
            state.update { it + task }
            task
        }

    override suspend fun updateDone(id: Long, done: Boolean): Boolean = mutex.withLock {
        if (state.value.none { it.id == id }) return false
        state.update { tasks -> tasks.map { if (it.id == id) it.copy(done = done) else it } }
        true
    }

    override suspend fun delete(id: Long): Boolean = mutex.withLock {
        if (state.value.none { it.id == id }) return false
        state.update { tasks -> tasks.filterNot { it.id == id } }
        true
    }

    override suspend fun deleteDone(): Int = mutex.withLock {
        val removed = state.value.count { it.done }
        state.update { tasks -> tasks.filterNot { it.done } }
        removed
    }
}
