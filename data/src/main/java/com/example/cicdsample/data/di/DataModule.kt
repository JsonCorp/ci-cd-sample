package com.example.cicdsample.data.di

import com.example.cicdsample.data.repository.DefaultTaskRepository
import com.example.cicdsample.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
}
