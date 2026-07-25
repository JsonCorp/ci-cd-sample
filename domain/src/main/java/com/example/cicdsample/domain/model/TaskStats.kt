package com.example.cicdsample.domain.model

/**
 * 진행률 요약.
 *
 * @param completionRate 0~100 사이 정수 백분율. 전체가 0건이면 0.
 */
data class TaskStats(
    val total: Int,
    val done: Int,
    val completionRate: Int,
) {
    companion object {
        val EMPTY = TaskStats(total = 0, done = 0, completionRate = 0)
    }
}
