package com.example.cicdsample.domain

import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallSnapshot
import com.example.cicdsample.domain.model.call.CallStage
import com.example.cicdsample.domain.model.call.MediaKind
import com.example.cicdsample.domain.model.call.MediaSession
import com.example.cicdsample.domain.model.call.Payload
import com.example.cicdsample.domain.model.call.PeerAddress
import com.example.cicdsample.domain.repository.CallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 통화 테스트용 페이크 저장소.
 *
 * 통화 계약이 도메인에 인터페이스로 있어서, RTP·소켓·마이크 없이 이 클래스 하나로
 * 유스케이스와 ViewModel 을 전부 테스트할 수 있다. 실제 RTP 동작은 :data 가 따로 검증한다.
 */
class FakeCallRepository(
    private val sessions: List<MediaSession> = listOf(AUDIO_SESSION),
) : CallRepository {

    private val state = MutableStateFlow(CallSnapshot())

    /** [start] 가 받은 주소들. 유스케이스의 검증을 통과한 값만 여기 남는다. */
    val startedPeers = mutableListOf<PeerAddress>()

    var startedWithVideo: Boolean? = null
        private set

    val endReasons = mutableListOf<CallEndReason>()

    /**
     * [parseRemoteSdp] 가 돌려줄 값.
     *
     * 실제 SDP 해석은 :data 의 `SdpCodec` 이 21건으로 검증돼 있다 — 여기서 확인할 것은
     * "해석에 성공했을 때 화면이 무엇을 하는가" 뿐이다.
     */
    var sdpResult: Result<PeerAddress> = Result.failure(IllegalArgumentException("NO_CONNECTION"))

    override fun observeCall(): Flow<CallSnapshot> = state.asStateFlow()

    override fun localSessions(): List<MediaSession> = sessions

    override suspend fun start(peer: PeerAddress, withVideo: Boolean) {
        startedPeers += peer
        startedWithVideo = withVideo
        state.value = CallSnapshot(
            stage = CallStage.Connecting,
            peer = peer,
            videoEnabled = withVideo,
        )
    }

    override suspend fun end(reason: CallEndReason) {
        endReasons += reason
        state.update { it.copy(stage = CallStage.Ended, endReason = reason) }
    }

    override suspend fun setMicMuted(muted: Boolean) {
        state.update { it.copy(micMuted = muted) }
    }

    override suspend fun setVideoEnabled(enabled: Boolean) {
        state.update { it.copy(videoEnabled = enabled) }
    }

    override fun localSdp(localHost: String): String {
        val audio = sessions.firstOrNull { it.kind == MediaKind.AUDIO } ?: return ""
        return "v=0\nc=IN IP4 $localHost\nm=audio ${audio.localPort} RTP/AVP ${audio.payload.payloadType}\n"
    }

    override fun parseRemoteSdp(text: String): Result<PeerAddress> =
        if (text.isBlank()) Result.failure(IllegalArgumentException("EMPTY")) else sdpResult

    /** 통화 진행 상황을 테스트가 직접 밀어 넣는다 — Active 전이나 통계 갱신을 흉내낼 때 쓴다. */
    fun emit(snapshot: CallSnapshot) {
        state.value = snapshot
    }

    fun current(): CallSnapshot = state.value

    companion object {
        val AUDIO_SESSION = MediaSession(
            kind = MediaKind.AUDIO,
            payload = Payload.PCMU,
            localPort = 5_004,
            ssrc = 0x1234_5678L,
        )
    }
}
