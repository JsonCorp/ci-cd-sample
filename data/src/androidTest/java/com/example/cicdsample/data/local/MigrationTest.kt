package com.example.cicdsample.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 마이그레이션 검증. 실제 SQLite 가 필요하므로 계측 테스트로만 돈다.
 *
 * [MigrationTestHelper] 는 커밋해 둔 `data/schemas` 의 버전별 JSON 을 에셋에서 읽어
 * **예전 버전 DB 를 진짜로 만든다.** 그 위에 마이그레이션을 실행하고,
 * 결과 스키마가 최신 버전의 정의와 일치하는지 Room 이 직접 대조한다.
 *
 * 이 테스트가 잡는 것:
 * - 버전만 올리고 마이그레이션을 빠뜨린 경우
 * - 마이그레이션 SQL 이 스키마와 미묘하게 다른 경우
 *   (예: `DEFAULT NULL` 을 붙여 dflt_value 가 생기면 불일치로 잡힌다)
 * - 마이그레이션이 기존 행을 날려먹는 경우
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * v1 → v2 스키마 검증.
     *
     * `validateMigration = true` 로 두면 마이그레이션 후의 실제 테이블 구조를
     * 2.json 과 대조한다. 컬럼 하나, 기본값 하나만 달라도 실패한다.
     */
    @Test
    fun v1에서_v2로_스키마가_일치하게_이전된다() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        ).close()
    }

    /**
     * 스키마만 맞는 것으로는 부족하다. **기존 데이터가 살아남아야** 한다.
     *
     * v1 에 행을 두 개 넣고 마이그레이션한 뒤, 값이 그대로인지와
     * 새 컬럼이 NULL 로 채워졌는지를 확인한다.
     */
    @Test
    fun v1의_기존_데이터가_마이그레이션_후에도_보존된다() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO tasks (title, priority, done, sort_order) VALUES (?, ?, ?, ?)",
                arrayOf("남은 일", "HIGH", 0, 0),
            )
            db.execSQL(
                "INSERT INTO tasks (title, priority, done, sort_order) VALUES (?, ?, ?, ?)",
                arrayOf("끝난 일", "LOW", 1, 1),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT title, priority, done, sort_order, due_date FROM tasks ORDER BY sort_order")
            .use { cursor ->
                assertEquals(2, cursor.count)

                assertTrue(cursor.moveToFirst())
                assertEquals("남은 일", cursor.getString(0))
                assertEquals("HIGH", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                // v2 에서 새로 생긴 컬럼이므로 기존 행에서는 NULL 이어야 한다.
                assertTrue("due_date 는 기존 행에서 NULL 이어야 한다", cursor.isNull(4))

                assertTrue(cursor.moveToNext())
                assertEquals("끝난 일", cursor.getString(0))
                assertEquals(1, cursor.getInt(2))
                assertTrue(cursor.isNull(4))
            }

        db.close()
    }

    /** 마이그레이션 후 새 컬럼에 실제로 값을 쓸 수 있어야 한다. */
    @Test
    fun 마이그레이션_후_새_컬럼에_값을_쓸_수_있다() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        db.execSQL(
            "INSERT INTO tasks (title, priority, done, sort_order, due_date) VALUES (?, ?, ?, ?, ?)",
            arrayOf("마감 있는 일", "NORMAL", 0, 0, 1_700_000_000_000L),
        )

        db.query("SELECT due_date FROM tasks WHERE title = '마감 있는 일'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_700_000_000_000L, cursor.getLong(0))
        }

        db.query("SELECT due_date FROM tasks WHERE title = '없는 일'").use { cursor ->
            assertEquals(0, cursor.count)
        }

        db.close()
    }

    /**
     * 앱이 실제로 쓰는 [AppDatabase.MIGRATIONS] 배열로도 최신 버전까지 도달해야 한다.
     *
     * 위 테스트들은 마이그레이션 객체를 직접 넘기므로, 정작 [AppDatabase.MIGRATIONS] 에
     * 등록을 빠뜨려도 통과한다. 그 구멍을 여기서 막는다 —
     * 등록을 잊으면 앱은 실행 시점에 죽지만 이 테스트는 그 전에 실패한다.
     */
    @Test
    fun 앱이_쓰는_MIGRATIONS_배열로도_최신_버전에_도달한다() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, *AppDatabase.MIGRATIONS).close()

        // 1 부터 최신 버전까지 끊긴 구간이 없는지 확인한다.
        val covered = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        val expected = (1 until AppDatabase.VERSION).map { it to it + 1 }.toSet()
        assertEquals("마이그레이션 경로에 빠진 구간이 있다", expected, covered)
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
