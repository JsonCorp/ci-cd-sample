package com.example.cicdsample.data.repository

import com.example.cicdsample.data.local.TaskLocalDataSource
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 도메인이 선언한 [TaskRepository] 의 유일한 구현.
 *
 * 구체 구현이 아니라 [TaskLocalDataSource] 인터페이스에 의존한다 — 프로덕션에는 Room 이,
 * 단위 테스트에는 인메모리 페이크가 꽂힌다. 캐시/원격이 붙어도 이 클래스만 바뀐다.
 */
@Singleton
class DefaultTaskRepository @Inject constructor(
    private val local: TaskLocalDataSource,
) : TaskRepository {

    override fun observeTasks(): Flow<List<Task>> = local.tasks

    override suspend fun addTask(title: String, priority: Priority, dueDate: Long?): Task =
        local.insert(title, priority, dueDate)

    override suspend fun setDone(id: Long, done: Boolean): Boolean = local.updateDone(id, done)

    override suspend fun deleteTask(id: Long): Boolean = local.delete(id)

    override suspend fun deleteCompleted(): Int = local.deleteDone()
}
