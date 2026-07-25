package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * 완료된 항목을 한 번에 지우고 지운 개수를 돌려준다.
 * 지울 게 없으면 0 — 실패가 아니다.
 */
class ClearCompletedUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(): Int = repository.deleteCompleted()
}
