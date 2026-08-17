package com.example.cicdsample.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * ViewModel 과 마이크 권한을 화면에 붙이는 얇은 래퍼.
 *
 * 권한 API 가 여기 있는 이유는 그것이 Android 것이기 때문이다 — 이 한 겹 덕분에
 * [CallScreen] 은 Hilt 도 권한도 모른 채 남고, [CallViewModel] 은 JVM 에서 테스트된다.
 */
@Composable
fun CallRoute(
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /**
     * 허용 결과가 오면 곧바로 통화를 시작한다 — 사용자는 이미 '통화'를 눌렀으므로
     * 허용한 뒤 한 번 더 누르게 만들 이유가 없다.
     */
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onStartClick() else viewModel.onMicPermissionDenied()
    }

    CallScreen(
        state = state,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onRemoteSdpChange = viewModel::onRemoteSdpChange,
        onApplyRemoteSdp = viewModel::onApplyRemoteSdp,
        onStartClick = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

            // 권한을 미리 묻지 않고 '통화'를 누른 순간에 묻는다 — 무엇에 쓰는지가 분명할 때
            // 사용자가 허용할 확률이 높고, 화면을 열자마자 뜨는 대화상자는 대개 거부된다.
            if (granted) viewModel.onStartClick() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        },
        onEndClick = viewModel::onEndClick,
        onMicToggle = viewModel::onMicToggle,
        modifier = modifier,
    )
}
