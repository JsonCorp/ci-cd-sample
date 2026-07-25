package com.example.cicdsample.ui.task

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.model.TaskFilter
import com.example.cicdsample.domain.model.TaskStats

/**
 * 화면이 그리는 데 필요한 전부. 이 하나만 만들면 화면을 어떤 상태로든 렌더링할 수 있어서
 * Compose UI 테스트가 ViewModel 도 Hilt 도 없이 돌아간다.
 */
data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val stats: TaskStats = TaskStats.EMPTY,
    val inputTitle: String = "",
    val inputPriority: Priority = Priority.NORMAL,
    val errorMessage: String? = null,
)
