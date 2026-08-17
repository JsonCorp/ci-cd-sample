package com.example.cicdsample.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 앱 데이터베이스.
 *
 * `exportSchema = true` 로 두고 스키마 JSON 을 저장소에 커밋한다(`data/schemas/`).
 * 그래야 [com.example.cicdsample.data.local.migration] 계측 테스트가 예전 버전 DB 를
 * 실제로 만들어 마이그레이션을 검증할 수 있고, 스키마 변경이 리뷰에서 diff 로 보인다.
 *
 * **버전을 올릴 때는 [AppDatabase.MIGRATIONS] 에 마이그레이션을 반드시 추가한다.**
 * 빠뜨리면 CI 의 스키마 가드가 잡는다.
 */
@Database(
    entities = [TaskEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(PriorityConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "cicd-sample.db"

        /** @Database(version=...) 과 반드시 같아야 한다. 테스트가 이 값으로 경로를 검사한다. */
        const val VERSION = 2

        /**
         * v1 → v2: 마감일(`due_date`) 컬럼 추가.
         *
         * nullable 로 넣는다. 기존 행에 채워 넣을 마감일이 없으므로 NOT NULL 은 불가능하고,
         * 억지로 0 같은 기본값을 넣으면 "1970년 마감"이라는 거짓 데이터가 생긴다.
         *
         * `DEFAULT NULL` 을 붙이지 않는 것이 중요하다. 붙이면 SQLite 가 dflt_value 에 'NULL' 을
         * 기록하는데, 내보낸 스키마의 due_date 에는 defaultValue 가 없어서 Room 의 스키마 검증이
         * 불일치로 판정한다. 컬럼을 그냥 추가해도 기존 행의 값은 어차피 NULL 이다.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN due_date INTEGER")
            }
        }

        /**
         * 적용할 마이그레이션 전부. 버전을 올리면 여기에 추가한다.
         *
         * 여기 빠뜨린 채 [Database] 의 version 만 올리면 앱은 실행 시점에 죽는다.
         * CI 의 마이그레이션 테스트가 그 상황을 릴리스 전에 재현한다.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
