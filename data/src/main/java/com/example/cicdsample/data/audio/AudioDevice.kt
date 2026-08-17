package com.example.cicdsample.data.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.cicdsample.data.rtp.G711Codec
import java.io.Closeable
import javax.inject.Inject

/**
 * 마이크. 8kHz 모노 16비트 PCM 을 준다 — G.711 이 그 형식을 그대로 받는다.
 *
 * 인터페이스로 떼어 둔 이유는 [com.example.cicdsample.data.call.DefaultCallRepository] 의
 * 송수신 루프와 상태 전이를 **마이크 없이 단위 테스트**하기 위해서다.
 */
interface AudioCapture : Closeable {

    /** @return 실제로 열렸으면 true. 권한이 없거나 기기가 거부하면 false. */
    fun start(): Boolean

    /**
     * PCM 을 읽는다. 블로킹이다.
     *
     * @return 읽은 바이트 수. 오류면 -1.
     */
    fun read(buffer: ByteArray): Int
}

/** 스피커. 8kHz 모노 16비트 PCM 을 받는다. */
interface AudioPlayback : Closeable {

    fun start(): Boolean

    fun write(pcm: ByteArray, length: Int)
}

/** 오디오 장치를 만든다. 테스트에서는 페이크 팩토리를 꽂는다. */
interface AudioDeviceFactory {
    fun createCapture(): AudioCapture
    fun createPlayback(): AudioPlayback

    /**
     * 통화용 오디오 모드로 들어간다. 장치 생성과 따로 둔 이유는 **되돌려야 하기 때문**이다 —
     * 모드는 기기 전역 설정이라 통화가 끝나면 원래대로 돌려놓아야 한다.
     */
    fun enterCallMode()

    /** 통화 전 오디오 모드로 돌아간다. 빠뜨리면 통화가 끝난 뒤에도 볼륨 키가 통화 채널을 만진다. */
    fun exitCallMode()
}

/** 8kHz 모노 16비트 — G.711 이 요구하는 형식이자 RTP 타임스탬프 기준이다. */
internal object AudioConfig {
    const val SAMPLE_RATE = 8_000
    const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    /**
     * 20ms 프레임 여러 개를 담을 버퍼.
     *
     * 최소 버퍼만 쓰면 스케줄링이 조금만 늦어도 언더런이 난다.
     * 프레임 4개(80ms)를 확보해 두면 GC 한 번 정도는 넘긴다.
     */
    const val FRAMES_IN_BUFFER = 4
}

/**
 * [AudioRecord] 구현.
 *
 * `VOICE_COMMUNICATION` 을 쓴다 — `MIC` 보다 기기 내장 에코 억제·노이즈 억제가 붙을
 * 가능성이 높다. 이 앱은 소프트웨어 에코 제거를 구현하지 않으므로 그 차이가 크다.
 */
class AndroidAudioCapture : AudioCapture {

    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // 호출 전에 RECORD_AUDIO 를 확인하는 것은 :app 의 책임이다.
    override fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_IN,
            AudioConfig.ENCODING,
        )
        if (minBuffer <= 0) return false

        val bufferSize = maxOf(
            minBuffer,
            G711Codec.PCM_BYTES_PER_FRAME * AudioConfig.FRAMES_IN_BUFFER,
        )

        val created = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_IN,
                AudioConfig.ENCODING,
                bufferSize,
            )
        }.getOrNull() ?: return false

        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return false
        }

        return runCatching {
            created.startRecording()
            record = created
            true
        }.getOrElse {
            created.release()
            false
        }
    }

    override fun read(buffer: ByteArray): Int {
        val active = record ?: return -1
        val read = active.read(buffer, 0, buffer.size)
        // AudioRecord 는 오류를 음수 상수로 돌려준다.
        return if (read < 0) -1 else read
    }

    override fun close() {
        record?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        record = null
    }
}

/**
 * [AudioTrack] 구현.
 *
 * `USAGE_VOICE_COMMUNICATION` 을 쓰면 통화 음량 채널을 타므로,
 * 사용자가 통화 중 볼륨 키로 조절하는 대상이 미디어가 아니라 통화가 된다.
 */
class AndroidAudioPlayback : AudioPlayback {

    private var track: AudioTrack? = null

    override fun start(): Boolean {
        val minBuffer = AudioTrack.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_OUT,
            AudioConfig.ENCODING,
        )
        if (minBuffer <= 0) return false

        val bufferSize = maxOf(
            minBuffer,
            G711Codec.PCM_BYTES_PER_FRAME * AudioConfig.FRAMES_IN_BUFFER,
        )

        val created = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioConfig.ENCODING)
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setChannelMask(AudioConfig.CHANNEL_OUT)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return false

        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            return false
        }

        return runCatching {
            created.play()
            track = created
            true
        }.getOrElse {
            created.release()
            false
        }
    }

    override fun write(pcm: ByteArray, length: Int) {
        track?.write(pcm, 0, length)
    }

    override fun close() {
        track?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        track = null
    }
}

class AndroidAudioDeviceFactory @Inject constructor(
    private val audioManager: AudioManager,
) : AudioDeviceFactory {

    override fun createCapture(): AudioCapture = AndroidAudioCapture()

    override fun createPlayback(): AudioPlayback = AndroidAudioPlayback()

    /**
     * 통화 모드로 바꾸면 기기 내장 에코 억제가 동작할 여지가 생기고, 볼륨 키가 통화 채널을 만진다.
     *
     * 실패해도 통화를 막지 않는다 — 모드 변경은 음질 개선이지 통화의 전제가 아니다.
     */
    override fun enterCallMode() {
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
    }

    override fun exitCallMode() {
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
    }
}
