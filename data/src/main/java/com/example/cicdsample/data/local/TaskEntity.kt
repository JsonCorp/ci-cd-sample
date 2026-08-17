package com.example.cicdsample.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task

/**
 * 저장 테이블. 도메인 [Task] 와 일부러 분리해 둔다 —
 * 컬럼 이름이나 저장 타입이 바뀌어도 도메인 모델은 건드리지 않는다.
 *
 * `order` 는 SQL 예약어라 컬럼명을 `sort_order` 로 쓴다.
 *
 * [dueDate] 는 스키마 v2 에서 추가됐다 — [AppDatabase.MIGRATION_1_2] 참고.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val priority: Priority,
    val done: Boolean,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    // nullable 로 추가한다 — 기존 행에 채워 넣을 값이 없으므로 NOT NULL 이면 마이그레이션이 불가능하다.
    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,
)

/**
 * [Priority] 를 ordinal 이 아니라 **이름 문자열**로 저장한다.
 *
 * ordinal 로 저장하면 enum 선언 순서를 바꾸는 순간 이미 저장된 데이터의 의미가 조용히 뒤바뀐다.
 * 이름으로 저장하면 순서를 바꿔도 안전하다.
 */
class PriorityConverter {

    @TypeConverter
    fun toRecord(priority: Priority): String = priority.name

    @TypeConverter
    fun fromRecord(value: String): Priority =
        runCatching { Priority.valueOf(value) }.getOrDefault(Priority.NORMAL)
}

internal fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    priority = priority,
    done = done,
    order = sortOrder,
    dueDate = dueDate,
)
