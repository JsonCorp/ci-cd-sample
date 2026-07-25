package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 완료/미완료를 뒤집는다. 현재 상태를 먼저 읽어야 하므로 단순 위임이 아니다.
 *
 * 존재하지 않는 id 는 조용히 무시하지 않고 [Result.failure] 로 알린다 —
 * 목록과 화면 상태가 어긋난 상황을 테스트에서 잡기 위해서다.
 */
class ToggleTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(id: Long): Result<Boolean> {
        val task = repository.observeTasks().first().find { it.id == id }
            ?: return Result.failure(NoSuchElementException("task not found: id=$id"))

        val next = !task.done
        return if (repository.setDone(id, next)) {
            Result.success(next)
        } else {
            Result.failure(IllegalStateException("failed to update task: id=$id"))
        }
    }
}
