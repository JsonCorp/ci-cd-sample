package com.example.cicdsample.data.repository

import com.example.cicdsample.data.local.InMemoryTaskDataSource
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 도메인이 선언한 [TaskRepository] 의 유일한 구현.
 *
 * 지금은 데이터 소스가 하나뿐이라 얇은 위임이지만, 캐시/원격이 붙어도 이 클래스만 바뀐다.
 */
@Singleton
class DefaultTaskRepository @Inject constructor(
    private val local: InMemoryTaskDataSource,
) : TaskRepository {

    override fun observeTasks(): Flow<List<Task>> = local.tasks

    override suspend fun addTask(title: String, priority: Priority): Task =
        local.insert(title, priority)

    override suspend fun setDone(id: Long, done: Boolean): Boolean = local.updateDone(id, done)

    override suspend fun deleteTask(id: Long): Boolean = local.delete(id)

    override suspend fun deleteCompleted(): Int = local.deleteDone()
}
