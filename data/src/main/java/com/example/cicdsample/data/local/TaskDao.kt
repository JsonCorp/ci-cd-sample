package com.example.cicdsample.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 할 일 테이블 접근. 정렬은 여기서 하지 않는다 —
 * 화면에 뿌릴 순서는 도메인의 ObserveTasksUseCase 가 정한다.
 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): TaskEntity?

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun updateDone(id: Long, done: Boolean): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun deleteDone(): Int

    /** 다음 정렬 번호. 비어 있으면 0 부터 시작한다. */
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM tasks")
    suspend fun nextSortOrder(): Int
}
