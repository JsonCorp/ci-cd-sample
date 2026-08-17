package com.example.cicdsample.ui.task

import com.example.cicdsample.domain.model.DueStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 선택지 → epoch milli 변환.
 *
 * ViewModel 은 이 함수에 `LocalDate.now()` 만 넘긴다. 날짜 계산의 정확성은 여기서 전부 검증하므로
 * ViewModel 테스트는 시계에 묶이지 않는다.
 */
class DueOptionTest {

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.of(2026, 8, 17)

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    @Test
    fun `NONE 은 null 을 낸다`() {
        assertNull(DueOption.NONE.toEpochMilli(today, zone))
    }

    @Test
    fun `TODAY 는 오늘의 시작 시각`() {
        val millis = requireNotNull(DueOption.TODAY.toEpochMilli(today, zone))

        assertEquals(today, millis.toLocalDate())
        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli(), millis)
    }

    @Test
    fun `TOMORROW 는 내일의 시작 시각`() {
        val millis = requireNotNull(DueOption.TOMORROW.toEpochMilli(today, zone))

        assertEquals(today.plusDays(1), millis.toLocalDate())
    }

    @Test
    fun `월말에 TOMORROW 를 고르면 다음 달 1일이 된다`() {
        val endOfMonth = LocalDate.of(2026, 8, 31)

        val millis = requireNotNull(DueOption.TOMORROW.toEpochMilli(endOfMonth, zone))

        assertEquals(LocalDate.of(2026, 9, 1), millis.toLocalDate())
    }

    @Test
    fun `DueStatus 문구 매핑`() {
        assertNull(DueStatus.NONE.label())
        assertEquals("지남", DueStatus.OVERDUE.label())
        assertEquals("오늘", DueStatus.TODAY.label())
        assertEquals("내일", DueStatus.TOMORROW.label())
        assertEquals("예정", DueStatus.LATER.label())
    }

    @Test
    fun `모든 DueOption 이 문구를 가진다`() {
        // 선택지를 추가하고 문구를 빠뜨리면 여기서 걸린다.
        DueOption.entries.forEach { option ->
            assertEquals(true, option.label().isNotBlank())
        }
    }
}
