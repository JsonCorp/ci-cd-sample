package com.example.cicdsample.ui.call

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cicdsample.domain.model.call.CallEndReason
import com.example.cicdsample.domain.model.call.CallStage
import com.example.cicdsample.domain.model.call.RtpStats

/**
 * 상태를 받아 그리기만 하는 통화 화면(stateless).
 *
 * ViewModel·Hilt·권한 API 를 참조하지 않으므로 Compose UI 테스트에서 상태를 직접 넣어
 * 검증할 수 있고, Preview 도 그냥 동작한다. 권한 요청은 [CallRoute] 가 맡는다.
 */
@Composable
fun CallScreen(
    state: CallUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onRemoteSdpChange: (String) -> Unit,
    onApplyRemoteSdp: () -> Unit,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onMicToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // 입력칸과 SDP 텍스트가 길어 작은 화면에서는 넘친다.
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "단말 대 단말 통화",
            style = MaterialTheme.typography.headlineSmall,
        )

        StatusSection(state)

        HorizontalDivider()

        LocalSection(localAddress = state.localAddress, localSdp = state.localSdp)

        HorizontalDivider()

        RemoteSdpSection(
            remoteSdp = state.remoteSdpInput,
            onRemoteSdpChange = onRemoteSdpChange,
            onApplyRemoteSdp = onApplyRemoteSdp,
        )

        PeerSection(
            host = state.hostInput,
            port = state.portInput,
            enabled = !state.inProgress,
            onHostChange = onHostChange,
            onPortChange = onPortChange,
        )

        ActionSection(
            state = state,
            onStartClick = onStartClick,
            onEndClick = onEndClick,
            onMicToggle = onMicToggle,
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                modifier = Modifier.testTag("text_call_error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusSection(state: CallUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = state.stage.label(),
            modifier = Modifier.testTag("text_call_stage"),
            style = MaterialTheme.typography.titleMedium,
        )

        state.peerLabel?.let { peer ->
            Text(
                text = "상대 $peer",
                modifier = Modifier.testTag("text_call_peer"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // 종료 이유는 끝난 뒤에만 뜻이 있다. 통화 중에 남아 있으면 지난 통화의 잔상이다.
        if (state.stage == CallStage.Ended) {
            state.endReason?.let { reason ->
                Text(
                    text = reason.label(),
                    modifier = Modifier.testTag("text_call_end"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // 한 번이라도 패킷을 받았을 때만 보여준다 — 0으로 채워진 표는 정보가 아니다.
        if (state.audioStats.expected > 0) {
            Text(
                text = state.audioStats.summary(),
                modifier = Modifier.testTag("text_call_stats"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LocalSection(localAddress: String?, localSdp: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "내 주소 — 상대에게 알려 주세요",
            style = MaterialTheme.typography.titleSmall,
        )

        if (localAddress == null) {
            Text(
                text = "네트워크에 연결되어 있지 않아 내 주소를 찾지 못했습니다.",
                modifier = Modifier.testTag("text_local_missing"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        Text(
            text = localAddress,
            modifier = Modifier.testTag("text_local_address"),
            style = MaterialTheme.typography.bodyLarge,
        )

        // 길게 눌러 복사할 수 있어야 한다 — 이 텍스트를 옮기는 것이 시그널링 전부다.
        SelectionContainer {
            Text(
                text = localSdp,
                modifier = Modifier.testTag("text_local_sdp"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun RemoteSdpSection(
    remoteSdp: String,
    onRemoteSdpChange: (String) -> Unit,
    onApplyRemoteSdp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = remoteSdp,
            onValueChange = onRemoteSdpChange,
            label = { Text("상대 SDP 붙여넣기") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_remote_sdp"),
        )

        Button(
            onClick = onApplyRemoteSdp,
            enabled = remoteSdp.isNotBlank(),
            modifier = Modifier.testTag("btn_apply_sdp"),
        ) {
            Text("주소 채우기")
        }
    }
}

@Composable
private fun PeerSection(
    host: String,
    port: String,
    enabled: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("상대 IP") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .weight(2f)
                .testTag("input_peer_host"),
        )

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("포트") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1f)
                .testTag("input_peer_port"),
        )
    }
}

@Composable
private fun ActionSection(
    state: CallUiState,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onMicToggle: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 통화 중에는 시작 버튼을 없애지 않고 비활성으로 둔다 — 버튼이 사라지면
        // 끊기 버튼이 그 자리로 밀려와 잘못 누르게 된다.
        Button(
            onClick = onStartClick,
            enabled = state.canStart,
            modifier = Modifier.testTag("btn_call_start"),
        ) {
            Text("통화")
        }

        Button(
            onClick = onEndClick,
            enabled = state.inProgress,
            modifier = Modifier.testTag("btn_call_end"),
        ) {
            Text("끊기")
        }

        FilterChip(
            selected = state.micMuted,
            onClick = { onMicToggle(!state.micMuted) },
            enabled = state.inProgress,
            label = { Text(if (state.micMuted) "음소거 해제" else "음소거") },
            modifier = Modifier.testTag("btn_mic"),
        )
    }
}

private fun CallStage.label(): String = when (this) {
    CallStage.Idle -> "통화 대기"
    // 시그널링이 없으므로 '벨이 울리는' 단계가 없다. 상대도 통화를 시작해야 붙는다.
    CallStage.Connecting -> "연결 중 — 상대의 첫 패킷을 기다립니다"
    CallStage.Active -> "통화 중"
    CallStage.Ended -> "통화 종료"
}

private fun CallEndReason.label(): String = when (this) {
    CallEndReason.LocalHangup -> "통화를 끊었습니다."
    CallEndReason.ReceiveTimeout -> "상대 패킷이 오지 않아 끊겼습니다."
    CallEndReason.TransportError -> "네트워크나 기기 오류로 끊겼습니다."
    CallEndReason.PermissionDenied -> "마이크 권한이 없어 통화하지 못했습니다."
}

/** 수신 품질 한 줄. 숫자를 다 보여주기보다 사용자가 판단할 수 있는 셋만 고른다. */
private fun RtpStats.summary(): String =
    "수신 $received/$expected · 유실 ${"%.1f".format(lossPercent)}% · 지터 ${"%.1f".format(jitterMs)}ms"

@Preview(showBackground = true)
@Composable
private fun CallScreenIdlePreview() {
    MaterialTheme {
        CallScreen(
            state = CallUiState(
                localAddress = "192.168.0.5:5004",
                localSdp = "v=0\nc=IN IP4 192.168.0.5\nm=audio 5004 RTP/AVP 0\n",
                hostInput = "192.168.0.9",
            ),
            onHostChange = {},
            onPortChange = {},
            onRemoteSdpChange = {},
            onApplyRemoteSdp = {},
            onStartClick = {},
            onEndClick = {},
            onMicToggle = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CallScreenActivePreview() {
    MaterialTheme {
        CallScreen(
            state = CallUiState(
                stage = CallStage.Active,
                localAddress = "192.168.0.5:5004",
                localSdp = "v=0\nc=IN IP4 192.168.0.5\nm=audio 5004 RTP/AVP 0\n",
                hostInput = "192.168.0.9",
                peerLabel = "192.168.0.9:5004",
                micMuted = true,
                audioStats = RtpStats(
                    expected = 500,
                    received = 495,
                    lost = 5,
                    outOfOrder = 2,
                    duplicated = 0,
                    jitterMs = 3.4,
                ),
            ),
            onHostChange = {},
            onPortChange = {},
            onRemoteSdpChange = {},
            onApplyRemoteSdp = {},
            onStartClick = {},
            onEndClick = {},
            onMicToggle = {},
        )
    }
}
