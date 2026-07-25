package com.example.cicdsample.domain.model

/**
 * 할 일 하나. 도메인 계층의 유일한 엔티티다.
 *
 * @param order 등록 순서. 같은 우선순위 안에서 정렬 기준이 된다.
 */
data class Task(
    val id: Long,
    val title: String,
    val priority: Priority,
    val done: Boolean,
    val order: Int,
)

/**
 * 우선순위. `ordinal` 이 클수록 급한 일이다 — 정렬이 enum 선언 순서에 묶이므로
 * 순서를 바꾸면 [com.example.cicdsample.domain.usecase.ObserveTasksUseCase] 테스트가 깨진다.
 */
enum class Priority {
    LOW,
    NORMAL,
    HIGH,
}
