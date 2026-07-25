package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.model.TaskFilter
import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 필터를 적용하고 화면에 뿌릴 순서로 정렬한다.
 *
 * 정렬 기준은 세 단계다.
 * 1. 미완료 먼저 (완료된 항목은 아래로)
 * 2. 우선순위 높은 것 먼저 (HIGH → NORMAL → LOW)
 * 3. 먼저 등록한 것 먼저 (order 오름차순)
 */
class ObserveTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    operator fun invoke(filter: TaskFilter): Flow<List<Task>> =
        repository.observeTasks().map { tasks ->
            tasks
                .filter { task ->
                    when (filter) {
                        TaskFilter.ALL -> true
                        TaskFilter.ACTIVE -> !task.done
                        TaskFilter.DONE -> task.done
                    }
                }
                .sortedWith(
                    compareBy<Task> { it.done }
                        .thenByDescending { it.priority.ordinal }
                        .thenBy { it.order },
                )
        }
}
