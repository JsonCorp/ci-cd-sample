package com.example.cicdsample.di

import com.example.cicdsample.ui.call.LocalHostProvider
import com.example.cicdsample.ui.call.NetworkLocalHostProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * :app 안에서만 쓰이는 바인딩.
 *
 * 대부분의 구현은 :data 가 꽂아 주고, 여기 남는 것은 화면이 직접 필요로 하는 것뿐이다 —
 * 내 IP 조회는 SDP 를 만들 때 화면이 넘겨야 하는 값이라 :app 에 있다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindLocalHostProvider(impl: NetworkLocalHostProvider): LocalHostProvider
}
