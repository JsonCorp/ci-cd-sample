package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject

/** 할 일 하나를 지운다. 없는 id 는 실패로 알린다. */
class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> =
        if (repository.deleteTask(id)) {
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("task not found: id=$id"))
        }
}
