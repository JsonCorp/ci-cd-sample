package com.example.cicdsample.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cicdsample.domain.model.DueStatus
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.model.TaskFilter
import com.example.cicdsample.domain.model.TaskStats
import com.example.cicdsample.domain.model.dueStatus
import java.time.LocalDate

/**
 * 상태를 받아 그리기만 하는 화면(stateless).
 *
 * ViewModel 이나 Hilt 를 참조하지 않으므로 Compose UI 테스트에서 상태를 직접 넣어 검증할 수 있고,
 * Preview 도 그냥 동작한다.
 */
@Composable
fun TaskScreen(
    state: TaskUiState,
    onTitleChange: (String) -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onDueChange: (DueOption) -> Unit,
    onAddClick: () -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onFilterChange: (TaskFilter) -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "CI/CD 샘플 — 할 일",
            style = MaterialTheme.typography.headlineSmall,
        )

        StatsSection(state.stats)

        InputSection(
            title = state.inputTitle,
            priority = state.inputPriority,
            due = state.inputDue,
            errorMessage = state.errorMessage,
            onTitleChange = onTitleChange,
            onPriorityChange = onPriorityChange,
            onDueChange = onDueChange,
            onAddClick = onAddClick,
        )

        // 필터와 정리 버튼은 화면 위쪽에 둔다. 아래쪽에 두면 소프트 키보드가 올라왔을 때 가려져서
        // E2E 에서 키보드를 내리는 동작(기기에 따라 back 키로 동작해 앱이 종료된다)이 필요해진다.
        FilterSection(
            selected = state.filter,
            onFilterChange = onFilterChange,
            clearEnabled = state.stats.done > 0,
            onClearCompleted = onClearCompleted,
        )

        HorizontalDivider()

        if (state.tasks.isEmpty()) {
            Text(
                text = "표시할 할 일이 없습니다.",
                modifier = Modifier.testTag("text_empty"),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("list_tasks"),
            ) {
                itemsIndexed(state.tasks, key = { _, task -> task.id }) { index, task ->
                    TaskRow(
                        index = index,
                        task = task,
                        onToggle = onToggle,
                        onDelete = onDelete,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StatsSection(stats: TaskStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${stats.done} / ${stats.total} 완료 (${stats.completionRate}%)",
            modifier = Modifier.testTag("text_stats"),
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            progress = { stats.completionRate / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InputSection(
    title: String,
    priority: Priority,
    due: DueOption,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onDueChange: (DueOption) -> Unit,
    onAddClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("할 일") },
            singleLine = true,
            isError = errorMessage != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_title"),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Priority.entries.reversed().forEach { option ->
                FilterChip(
                    selected = option == priority,
                    onClick = { onPriorityChange(option) },
                    label = { Text(option.label()) },
                    modifier = Modifier.testTag("btn_priority_${option.name.lowercase()}"),
                )
            }

            Button(
                onClick = onAddClick,
                modifier = Modifier.testTag("btn_add"),
            ) {
                Text("추가")
            }
        }

        // 마감일. 날짜 선택기 대신 고정 선택지 세 개만 둔다 —
        // E2E 플로우가 실행 날짜에 흔들리지 않는다.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DueOption.entries.forEach { option ->
                FilterChip(
                    selected = option == due,
                    onClick = { onDueChange(option) },
                    label = { Text(option.label()) },
                    modifier = Modifier.testTag("btn_due_${option.name.lowercase()}"),
                )
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                modifier = Modifier.testTag("text_error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FilterSection(
    selected: TaskFilter,
    onFilterChange: (TaskFilter) -> Unit,
    clearEnabled: Boolean,
    onClearCompleted: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label()) },
                modifier = Modifier.testTag("btn_filter_${filter.name.lowercase()}"),
            )
        }

        TextButton(
            onClick = onClearCompleted,
            enabled = clearEnabled,
            modifier = Modifier.testTag("btn_clear_completed"),
        ) {
            Text("완료 지우기")
        }
    }
}

@Composable
private fun TaskRow(
    index: Int,
    task: Task,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_task_$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = { onToggle(task.id) },
            modifier = Modifier.testTag("checkbox_task_$index"),
        )
        Text(
            text = task.title,
            modifier = Modifier.weight(1f),
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.bodyLarge,
        )
        // 완료된 항목은 dueStatus 가 NONE 을 돌려주므로 자연히 사라진다.
        task.dueStatus(LocalDate.now()).label()?.let { dueLabel ->
            Text(
                text = dueLabel,
                modifier = Modifier.testTag("text_due_$index"),
                style = MaterialTheme.typography.labelMedium,
                color = if (task.dueStatus(LocalDate.now()) == DueStatus.OVERDUE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = task.priority.label(),
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(
            onClick = { onDelete(task.id) },
            modifier = Modifier.testTag("btn_delete_$index"),
        ) {
            Text("삭제")
        }
    }
}

private fun Priority.label(): String = when (this) {
    Priority.HIGH -> "높음"
    Priority.NORMAL -> "보통"
    Priority.LOW -> "낮음"
}

private fun TaskFilter.label(): String = when (this) {
    TaskFilter.ALL -> "전체"
    TaskFilter.ACTIVE -> "미완료"
    TaskFilter.DONE -> "완료"
}

@Preview(showBackground = true)
@Composable
private fun TaskScreenPreview() {
    MaterialTheme {
        TaskScreen(
            state = TaskUiState(
                tasks = listOf(
                    Task(id = 1, title = "장보기", priority = Priority.HIGH, done = false, order = 0),
                    Task(id = 2, title = "청소하기", priority = Priority.NORMAL, done = true, order = 1),
                ),
                stats = TaskStats(total = 2, done = 1, completionRate = 50),
            ),
            onTitleChange = {},
            onPriorityChange = {},
            onDueChange = {},
            onAddClick = {},
            onToggle = {},
            onDelete = {},
            onFilterChange = {},
            onClearCompleted = {},
        )
    }
}
