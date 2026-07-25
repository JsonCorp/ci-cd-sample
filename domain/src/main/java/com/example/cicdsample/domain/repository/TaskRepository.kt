package com.example.cicdsample.domain.repository

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * 저장소 인터페이스는 **도메인이 소유**한다.
 *
 * 구현(:data)이 인터페이스(:domain)에 의존하는 방향이라, 도메인은 저장 방식(메모리/Room/네트워크)을
 * 전혀 모른다. 테스트에서는 이 인터페이스에 페이크를 꽂으면 끝난다.
 */
interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>

    suspend fun addTask(title: String, priority: Priority): Task

    suspend fun setDone(id: Long, done: Boolean): Boolean

    suspend fun deleteTask(id: Long): Boolean

    suspend fun deleteCompleted(): Int
}
