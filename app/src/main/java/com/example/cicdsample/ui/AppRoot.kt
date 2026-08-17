package com.example.cicdsample.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.cicdsample.ui.call.CallRoute
import com.example.cicdsample.ui.task.TaskRoute

/** 앱의 화면 둘. 화면이 늘어나면 그때 네비게이션 라이브러리를 붙인다. */
enum class AppTab(val label: String) {
    TASK("할 일"),
    CALL("통화"),
}

/**
 * 두 화면을 탭으로 바꾼다.
 *
 * 네비게이션 라이브러리를 쓰지 않는 이유는 화면이 둘뿐이고 딥링크도 백스택 규칙도 없기
 * 때문이다. 의존성 하나를 아끼는 것보다, 이 파일만 읽으면 화면 구성이 전부 보이는 것이 크다.
 *
 * 탭을 바꿔도 통화는 끊기지 않는다 — 통화 루프는 화면이 아니라 :data 의 싱글턴이 들고 있다.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(AppTab.TASK) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // targetSdk 35 부터 Android 는 앱을 edge-to-edge 로 그린다 — 인셋을 비우지 않으면
            // 탭이 상태바 아래에 깔린다. 그림만 가려지는 것이 아니라 **터치가 상태바에 먹혀**
            // 탭이 눌리지 않는다. API 30 에뮬레이터에서는 재현되지 않아 실단말 E2E 가 잡아낸 자리다.
            //
            // IME 인셋은 넣지 않는다. 창이 adjustResize 로 이미 줄어들므로 겹쳐서 빼면
            // 키보드 높이만큼 두 번 밀린다.
            .systemBarsPadding()
            .displayCutoutPadding(),
    ) {
        TabRow(selectedTabIndex = selected.ordinal) {
            AppTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selected,
                    onClick = { selected = tab },
                    text = { Text(tab.label) },
                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                )
            }
        }

        when (selected) {
            AppTab.TASK -> TaskRoute()
            AppTab.CALL -> CallRoute()
        }
    }
}
