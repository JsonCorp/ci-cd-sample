package com.example.cicdsample.ui.task

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * ViewModel 을 화면에 붙이는 얇은 래퍼.
 *
 * 상태 수집과 이벤트 연결만 담당한다. 이 한 겹 덕분에 [TaskScreen] 은 Hilt 를 모른 채로 남는다.
 */
@Composable
fun TaskRoute(
    modifier: Modifier = Modifier,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TaskScreen(
        state = state,
        onTitleChange = viewModel::onTitleChange,
        onPriorityChange = viewModel::onPriorityChange,
        onAddClick = viewModel::onAddClick,
        onToggle = viewModel::onToggle,
        onDelete = viewModel::onDelete,
        onFilterChange = viewModel::onFilterChange,
        onClearCompleted = viewModel::onClearCompleted,
        modifier = modifier,
    )
}
