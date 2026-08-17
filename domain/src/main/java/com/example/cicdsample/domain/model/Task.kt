package com.example.cicdsample.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 할 일 하나. 도메인 계층의 유일한 엔티티다.
 *
 * @param order 등록 순서. 같은 우선순위 안에서 정렬 기준이 된다.
 * @param dueDate 마감일. epoch milli, 없으면 null. DB 스키마 v2 에서 추가됐다.
 */
data class Task(
    val id: Long,
    val title: String,
    val priority: Priority,
    val done: Boolean,
    val order: Int,
    val dueDate: Long? = null,
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

/** 마감일이 오늘 기준 어디에 있는지. 문구로 바꾸는 건 UI 의 몫이다. */
enum class DueStatus {
    NONE,
    OVERDUE,
    TODAY,
    TOMORROW,
    LATER,
}

/**
 * 마감 상태를 계산한다.
 *
 * `today` 를 인자로 받는 이유: 도메인이 시계를 직접 읽으면 테스트가 실행 날짜에 따라 흔들린다.
 * 호출부(UI)가 오늘 날짜를 넘기므로 이 함수는 어떤 날짜로도 결정적으로 검증된다.
 *
 * 완료된 항목은 마감을 따지지 않는다 — 이미 끝난 일이 빨갛게 뜨는 건 소음이다.
 */
fun Task.dueStatus(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DueStatus {
    val due = dueDate ?: return DueStatus.NONE
    if (done) return DueStatus.NONE

    val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
    return when {
        dueDay.isBefore(today) -> DueStatus.OVERDUE
        dueDay == today -> DueStatus.TODAY
        dueDay == today.plusDays(1) -> DueStatus.TOMORROW
        else -> DueStatus.LATER
    }
}
