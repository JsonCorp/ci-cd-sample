package com.example.cicdsample.data.di

import android.content.Context
import android.media.AudioManager
import com.example.cicdsample.data.audio.AndroidAudioDeviceFactory
import com.example.cicdsample.data.audio.AudioDeviceFactory
import com.example.cicdsample.data.call.CallScope
import com.example.cicdsample.data.call.DefaultCallRepository
import com.example.cicdsample.data.net.RtpTransportFactory
import com.example.cicdsample.data.net.UdpRtpTransportFactory
import com.example.cicdsample.domain.repository.CallRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 통화 계약(:domain) ↔ RTP 구현(:data) 을 잇는 지점.
 *
 * 여기서 꽂는 세 가지 — 저장소·전송 통로·오디오 장치 — 가 전부 인터페이스인 덕에
 * 단위 테스트는 이 모듈을 거치지 않고 페이크를 직접 넣는다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CallModule {

    @Binds
    @Singleton
    abstract fun bindCallRepository(impl: DefaultCallRepository): CallRepository

    @Binds
    abstract fun bindRtpTransportFactory(impl: UdpRtpTransportFactory): RtpTransportFactory

    @Binds
    abstract fun bindAudioDeviceFactory(impl: AndroidAudioDeviceFactory): AudioDeviceFactory
}

/** 생성자 주입이 불가능한 것들 — 시스템 서비스와 코루틴 스코프. */
@Module
@InstallIn(SingletonComponent::class)
object CallProvidesModule {

    @Provides
    fun provideAudioManager(@ApplicationContext context: Context): AudioManager =
        context.getSystemService(AudioManager::class.java)

    /**
     * 통화 루프가 사는 스코프.
     *
     * - [Dispatchers.IO] 인 이유: 마이크 읽기와 소켓 수신이 블로킹이다. Default 에 올리면
     *   CPU 코어 수만큼뿐인 워커 하나를 통화 내내 붙잡는다.
     * - [SupervisorJob] 인 이유: 통화 루프가 실패해도 이 스코프를 죽이지 않아야
     *   다음 통화를 다시 시작할 수 있다.
     */
    @Provides
    @Singleton
    @CallScope
    fun provideCallScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
