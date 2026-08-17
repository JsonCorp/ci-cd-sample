package com.example.cicdsample.ui.task

import com.example.cicdsample.domain.model.DueStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * 입력 폼이 제공하는 마감일 선택지.
 *
 * 날짜 선택기 대신 세 개의 칩만 둔다. 샘플의 주제는 파이프라인이고,
 * 선택지가 고정돼 있어야 E2E 플로우가 날짜에 흔들리지 않는다.
 */
enum class DueOption {
    NONE,
    TODAY,
    TOMORROW,
}

/**
 * 선택지를 저장할 epoch milli 로 바꾼다. 그날의 **시작 시각**을 쓴다.
 *
 * `today` 를 인자로 받는 이유는 [com.example.cicdsample.domain.model.dueStatus] 와 같다 —
 * 함수 안에서 시계를 읽으면 테스트가 실행 날짜에 따라 흔들린다.
 */
fun DueOption.toEpochMilli(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long? =
    when (this) {
        DueOption.NONE -> null
        DueOption.TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
        DueOption.TOMORROW -> today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

fun DueOption.label(): String = when (this) {
    DueOption.NONE -> "마감 없음"
    DueOption.TODAY -> "오늘"
    DueOption.TOMORROW -> "내일"
}

/** 목록에 표시할 문구. 표시할 게 없으면 null 이다. */
fun DueStatus.label(): String? = when (this) {
    DueStatus.NONE -> null
    DueStatus.OVERDUE -> "지남"
    DueStatus.TODAY -> "오늘"
    DueStatus.TOMORROW -> "내일"
    DueStatus.LATER -> "예정"
}
