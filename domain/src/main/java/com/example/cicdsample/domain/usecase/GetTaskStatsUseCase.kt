package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.model.TaskStats
import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 진행률을 집계한다.
 *
 * 전체가 0건일 때 0으로 나누지 않도록 막는 것이 이 유스케이스의 존재 이유다 —
 * 화면에서 계산했다면 목록이 빈 첫 실행에서 크래시가 났을 자리다.
 */
class GetTaskStatsUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    operator fun invoke(): Flow<TaskStats> =
        repository.observeTasks().map { tasks ->
            val total = tasks.size
            if (total == 0) return@map TaskStats.EMPTY

            val done = tasks.count { it.done }
            TaskStats(
                total = total,
                done = done,
                completionRate = (done * 100.0 / total).roundToInt(),
            )
        }
}
