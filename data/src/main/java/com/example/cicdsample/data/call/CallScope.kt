package com.example.cicdsample.data.call

import javax.inject.Qualifier

/**
 * 통화 송수신 루프가 사는 코루틴 스코프.
 *
 * 화면(ViewModel) 스코프에 두지 않는다 — 회전이나 화면 전환으로 ViewModel 이 사라질 때
 * 통화까지 끊기면 안 된다. 실제 인스턴스는 `di` 가 만들어 꽂는다.
 */
@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class CallScope
