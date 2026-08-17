package com.example.cicdsample.domain.repository

import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallSnapshot
import com.example.cicdsample.domain.model.call.MediaSession
import com.example.cicdsample.domain.model.call.PeerAddress
import kotlinx.coroutines.flow.Flow

/**
 * 통화 계약. **도메인이 소유**한다.
 *
 * 이 인터페이스에 RTP·UDP·MediaCodec 이 한 번도 등장하지 않는 것이 핵심이다.
 * 도메인은 "통화를 시작한다 / 끊는다 / 상태를 본다" 만 알고,
 * 그것이 RTP 로 나가는지 다른 무엇으로 나가는지는 :data 의 사정이다.
 *
 * 그래서 유스케이스 테스트는 소켓도 코덱도 없이 페이크 하나로 끝난다.
 */
interface CallRepository {

    /** 통화 상태 스트림. 구독하는 동안 계속 갱신된다. */
    fun observeCall(): Flow<CallSnapshot>

    /**
     * 내가 열어 둔 미디어 세션. 통화 전에도 알 수 있어야 한다 —
     * SDP 오퍼를 만들어 상대에게 보여줘야 하기 때문이다.
     */
    fun localSessions(): List<MediaSession>

    /**
     * 통화를 시작한다. 상대 주소로 패킷을 보내기 시작하고 수신 대기에 들어간다.
     *
     * @param withVideo 영상을 함께 보낼지. false 면 오디오만 흐른다.
     */
    suspend fun start(peer: PeerAddress, withVideo: Boolean)

    /** 통화를 끝낸다. 소켓과 코덱을 정리한다. */
    suspend fun end(reason: CallEndReason)

    /** 마이크 음소거를 켜고 끈다. */
    suspend fun setMicMuted(muted: Boolean)

    /** 통화 중 영상 송신을 켜고 끈다. */
    suspend fun setVideoEnabled(enabled: Boolean)

    /**
     * 상대에게 건네줄 세션 기술서(SDP) 텍스트.
     *
     * SDP 는 저장 방식이나 소켓 같은 구현 detail 이 아니라 **통화 도메인의 어휘**다.
     * 무엇을 어느 포트로 어떤 코덱으로 주고받을지를 적는 형식이고,
     * 이 앱에서는 사람이 그 텍스트를 복사해 옮기는 것이 시그널링 전부다.
     * 그래서 계약에 남긴다 — 형식을 실제로 만들고 읽는 코드는 :data 에 있다.
     */
    fun localSdp(localHost: String): String

    /** 상대 SDP 를 읽어 주소를 뽑는다. 형식이 어긋나면 실패를 담아 돌려준다. */
    fun parseRemoteSdp(text: String): Result<PeerAddress>
}
