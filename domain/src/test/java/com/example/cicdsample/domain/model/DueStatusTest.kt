package com.example.cicdsample.domain.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 마감 상태 계산.
 *
 * `today` 를 인자로 받는 설계라 실행 날짜와 무관하게 결정적으로 검증된다 —
 * 자정 근처에 CI 가 돌아도 결과가 바뀌지 않는다.
 */
class DueStatusTest {

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.of(2026, 8, 17)

    private fun taskDue(date: LocalDate?, done: Boolean = false): Task = Task(
        id = 1,
        title = "할 일",
        priority = Priority.NORMAL,
        done = done,
        order = 0,
        dueDate = date?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
    )

    @Test
    fun `마감일이 없으면 NONE`() {
        assertEquals(DueStatus.NONE, taskDue(null).dueStatus(today, zone))
    }

    @Test
    fun `어제가 마감이면 OVERDUE`() {
        assertEquals(DueStatus.OVERDUE, taskDue(today.minusDays(1)).dueStatus(today, zone))
    }

    @Test
    fun `오늘이 마감이면 TODAY`() {
        assertEquals(DueStatus.TODAY, taskDue(today).dueStatus(today, zone))
    }

    @Test
    fun `내일이 마감이면 TOMORROW`() {
        assertEquals(DueStatus.TOMORROW, taskDue(today.plusDays(1)).dueStatus(today, zone))
    }

    @Test
    fun `모레 이후는 LATER`() {
        assertEquals(DueStatus.LATER, taskDue(today.plusDays(2)).dueStatus(today, zone))
        assertEquals(DueStatus.LATER, taskDue(today.plusMonths(1)).dueStatus(today, zone))
    }

    @Test
    fun `완료한 항목은 마감일이 지났어도 NONE`() {
        // 이미 끝난 일이 빨갛게 뜨는 건 소음이다.
        val overdueButDone = taskDue(today.minusDays(3), done = true)

        assertEquals(DueStatus.NONE, overdueButDone.dueStatus(today, zone))
    }

    @Test
    fun `마감 당일 23시 59분도 오늘로 본다`() {
        // 날짜 단위로 비교하므로 같은 날 안의 시각 차이는 결과를 바꾸지 않는다.
        val lateToday = Task(
            id = 2,
            title = "늦은 마감",
            priority = Priority.NORMAL,
            done = false,
            order = 0,
            dueDate = today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli(),
        )

        assertEquals(DueStatus.TODAY, lateToday.dueStatus(today, zone))
    }

    @Test
    fun `해가 바뀌는 경계에서도 어긋나지 않는다`() {
        val newYearsEve = LocalDate.of(2026, 12, 31)

        assertEquals(
            DueStatus.TOMORROW,
            taskDue(LocalDate.of(2027, 1, 1)).dueStatus(newYearsEve, zone),
        )
        assertEquals(
            DueStatus.OVERDUE,
            taskDue(LocalDate.of(2026, 12, 30)).dueStatus(newYearsEve, zone),
        )
    }
}
