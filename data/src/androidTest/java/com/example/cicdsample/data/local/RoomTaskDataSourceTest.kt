package com.example.cicdsample.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cicdsample.domain.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO 의 **실제 SQL** 검증.
 *
 * `DefaultTaskRepositoryTest` 는 인메모리 페이크를 쓰므로 쿼리가 틀려도 통과한다.
 * 여기서는 진짜 SQLite 에 붙여 SQL 자체를 확인한다 —
 * `sort_order` 예약어 회피, `COALESCE(MAX(...), -1) + 1`, `done = 1` 조건,
 * Priority 를 문자열로 저장하는 TypeConverter 가 실제로 동작하는지.
 */
@RunWith(AndroidJUnit4::class)
class RoomTaskDataSourceTest {

    private lateinit var database: AppDatabase
    private lateinit var source: RoomTaskDataSource

    @Before
    fun setUp() {
        // 디스크에 남기지 않는다. 테스트마다 깨끗한 DB 로 시작한다.
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        source = RoomTaskDataSource(database.taskDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 추가하면_id_와_정렬번호가_순서대로_붙는다() = runTest {
        val first = source.insert("첫 번째", Priority.NORMAL, null)
        val second = source.insert("두 번째", Priority.HIGH, null)

        assertEquals(1L, first.id)
        assertEquals(0, first.order)
        assertEquals(2L, second.id)
        assertEquals(1, second.order)
    }

    @Test
    fun 우선순위가_문자열로_저장되고_그대로_복원된다() = runTest {
        // ordinal 이 아니라 이름으로 저장하므로 enum 선언 순서가 바뀌어도 안전하다.
        Priority.entries.forEach { priority ->
            source.insert("작업-$priority", priority, null)
        }

        val stored = source.tasks.first()

        Priority.entries.forEach { priority ->
            assertEquals(priority, stored.single { it.title == "작업-$priority" }.priority)
        }
    }

    @Test
    fun 마감일은_넣은_값이_그대로_나오고_없으면_null() = runTest {
        val due = 1_700_000_000_000L

        val withDue = source.insert("마감 있음", Priority.NORMAL, due)
        val withoutDue = source.insert("마감 없음", Priority.NORMAL, null)

        val stored = source.tasks.first()
        assertEquals(due, stored.single { it.id == withDue.id }.dueDate)
        assertNull(stored.single { it.id == withoutDue.id }.dueDate)
    }

    @Test
    fun updateDone_은_해당_행만_바꾸고_없는_id_는_false() = runTest {
        val target = source.insert("대상", Priority.NORMAL, null)
        source.insert("나머지", Priority.NORMAL, null)

        assertTrue(source.updateDone(target.id, true))
        assertFalse(source.updateDone(999L, true))

        val stored = source.tasks.first()
        assertTrue(stored.single { it.id == target.id }.done)
        assertFalse(stored.single { it.id != target.id }.done)
    }

    @Test
    fun delete_는_해당_행만_지우고_없는_id_는_false() = runTest {
        val target = source.insert("지울 것", Priority.NORMAL, null)
        source.insert("남을 것", Priority.NORMAL, null)

        assertTrue(source.delete(target.id))
        assertFalse(source.delete(999L))

        assertEquals(listOf("남을 것"), source.tasks.first().map { it.title })
    }

    @Test
    fun deleteDone_은_완료된_행만_지우고_개수를_돌려준다() = runTest {
        val a = source.insert("완료 A", Priority.NORMAL, null)
        source.insert("미완료", Priority.NORMAL, null)
        val c = source.insert("완료 C", Priority.NORMAL, null)
        source.updateDone(a.id, true)
        source.updateDone(c.id, true)

        val removed = source.deleteDone()

        assertEquals(2, removed)
        assertEquals(listOf("미완료"), source.tasks.first().map { it.title })
    }

    @Test
    fun 정렬번호는_행을_지워도_재사용되지_않는다() = runTest {
        // COALESCE(MAX(sort_order), -1) + 1 이므로 마지막 값 기준으로 이어진다.
        val first = source.insert("첫 번째", Priority.NORMAL, null)
        val second = source.insert("두 번째", Priority.NORMAL, null)
        source.delete(second.id)

        val third = source.insert("세 번째", Priority.NORMAL, null)

        assertEquals(0, first.order)
        assertEquals(1, third.order)
    }

    @Test
    fun 비어_있을_때_첫_정렬번호는_0() = runTest {
        // COALESCE 가 없으면 MAX() 가 NULL 이라 여기서 터진다.
        val only = source.insert("유일", Priority.NORMAL, null)

        assertEquals(0, only.order)
    }

    @Test
    fun 모두_지운_뒤_다시_넣으면_정렬번호가_0부터_시작한다() = runTest {
        val a = source.insert("A", Priority.NORMAL, null)
        source.updateDone(a.id, true)
        source.deleteDone()

        val fresh = source.insert("B", Priority.NORMAL, null)

        assertEquals(0, fresh.order)
    }
}
