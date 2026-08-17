package com.example.cicdsample.domain

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 도메인 테스트용 페이크 저장소.
 *
 * 저장소가 인터페이스로 도메인에 있기 때문에 Android 도, Hilt 도, mock 라이브러리도 없이
 * 이 30줄짜리 클래스 하나로 모든 유스케이스를 테스트할 수 있다.
 */
class FakeTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {

    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextOrder = (initial.maxOfOrNull { it.order } ?: -1) + 1

    override fun observeTasks(): Flow<List<Task>> = state.asStateFlow()

    override suspend fun addTask(title: String, priority: Priority, dueDate: Long?): Task {
        val task = Task(
            id = nextId++,
            title = title,
            priority = priority,
            done = false,
            order = nextOrder++,
            dueDate = dueDate,
        )
        state.update { it + task }
        return task
    }

    override suspend fun setDone(id: Long, done: Boolean): Boolean {
        if (state.value.none { it.id == id }) return false
        state.update { tasks -> tasks.map { if (it.id == id) it.copy(done = done) else it } }
        return true
    }

    override suspend fun deleteTask(id: Long): Boolean {
        if (state.value.none { it.id == id }) return false
        state.update { tasks -> tasks.filterNot { it.id == id } }
        return true
    }

    override suspend fun deleteCompleted(): Int {
        val removed = state.value.count { it.done }
        state.update { tasks -> tasks.filterNot { it.done } }
        return removed
    }

    /** 테스트에서 현재 상태를 바로 확인할 때 쓴다. */
    fun current(): List<Task> = state.value
}

/** 테스트 데이터 생성 헬퍼 — 필요한 필드만 지정하면 된다. */
fun task(
    id: Long,
    title: String = "task-$id",
    priority: Priority = Priority.NORMAL,
    done: Boolean = false,
    order: Int = id.toInt(),
    dueDate: Long? = null,
): Task = Task(
    id = id,
    title = title,
    priority = priority,
    done = done,
    order = order,
    dueDate = dueDate,
)
