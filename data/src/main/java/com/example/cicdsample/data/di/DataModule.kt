package com.example.cicdsample.data.di

import android.content.Context
import androidx.room.Room
import com.example.cicdsample.data.local.AppDatabase
import com.example.cicdsample.data.local.RoomTaskDataSource
import com.example.cicdsample.data.local.TaskDao
import com.example.cicdsample.data.local.TaskLocalDataSource
import com.example.cicdsample.data.repository.DefaultTaskRepository
import com.example.cicdsample.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 인터페이스(:domain) ↔ 구현(:data) 을 잇는 유일한 지점.
 *
 * UI 계층은 [TaskRepository] 타입만 알고, 어떤 구현이 꽂히는지는 이 모듈이 결정한다.
 * 테스트에서는 이 모듈을 대체하거나(계측 테스트), 아예 거치지 않고 페이크를 직접 넣는다(단위 테스트).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: DefaultTaskRepository): TaskRepository

    /** 프로덕션에는 Room 구현을 꽂는다. 단위 테스트는 이 바인딩을 거치지 않는다. */
    @Binds
    @Singleton
    abstract fun bindTaskLocalDataSource(impl: RoomTaskDataSource): TaskLocalDataSource
}

/**
 * 생성자 주입이 불가능한 Room 객체들을 만든다.
 *
 * `fallbackToDestructiveMigration()` 은 **쓰지 않는다.** 편하지만 마이그레이션을 빠뜨렸을 때
 * 사용자 데이터를 조용히 날려버린다. 마이그레이션이 없으면 차라리 즉시 죽는 편이 낫다 —
 * 그런 실수는 CI 의 마이그레이션 테스트가 릴리스 전에 잡는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()
}
