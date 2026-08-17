package com.example.cicdsample.data.local

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * 로컬 저장소의 계약. 구현이 둘이다.
 *
 * - [RoomTaskDataSource] — 프로덕션. 실제 SQLite 에 쓴다.
 * - `InMemoryTaskDataSource` (src/test) — JVM 단위 테스트용. Android 프레임워크가 없어도 돈다.
 *
 * 이 인터페이스가 있어서 [com.example.cicdsample.data.repository.DefaultTaskRepository] 테스트가
 * 여전히 에뮬레이터 없이 초 단위로 끝난다. Room 자체의 정확성(스키마·마이그레이션·쿼리)은
 * src/androidTest 의 계측 테스트가 따로 검증한다.
 */
interface TaskLocalDataSource {

    val tasks: Flow<List<Task>>

    suspend fun insert(title: String, priority: Priority, dueDate: Long?): Task

    suspend fun updateDone(id: Long, done: Boolean): Boolean

    suspend fun delete(id: Long): Boolean

    suspend fun deleteDone(): Int
}
