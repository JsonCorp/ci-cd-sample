package com.example.cicdsample.data.local

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 프로덕션 로컬 저장소. 실제 SQLite 에 쓴다.
 *
 * id 는 Room 의 autoGenerate 가 발급하지만 `sort_order` 는 MAX+1 로 직접 계산하므로,
 * 동시 삽입이 겹치면 같은 번호가 두 번 나올 수 있다. 쓰기를 [Mutex] 로 직렬화해 막는다.
 */
@Singleton
class RoomTaskDataSource @Inject constructor(
    private val dao: TaskDao,
) : TaskLocalDataSource {

    private val writeLock = Mutex()

    override val tasks: Flow<List<Task>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun insert(title: String, priority: Priority, dueDate: Long?): Task =
        writeLock.withLock {
            val entity = TaskEntity(
                title = title,
                priority = priority,
                done = false,
                sortOrder = dao.nextSortOrder(),
                dueDate = dueDate,
            )
            val id = dao.insert(entity)
            entity.copy(id = id).toDomain()
        }

    override suspend fun updateDone(id: Long, done: Boolean): Boolean =
        dao.updateDone(id, done) > 0

    override suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    override suspend fun deleteDone(): Int = dao.deleteDone()
}
