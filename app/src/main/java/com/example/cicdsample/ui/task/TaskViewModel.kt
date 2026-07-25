package com.example.cicdsample.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.TaskFilter
import com.example.cicdsample.domain.usecase.AddTaskUseCase
import com.example.cicdsample.domain.usecase.ClearCompletedUseCase
import com.example.cicdsample.domain.usecase.DeleteTaskUseCase
import com.example.cicdsample.domain.usecase.GetTaskStatsUseCase
import com.example.cicdsample.domain.usecase.ObserveTasksUseCase
import com.example.cicdsample.domain.usecase.TitleError
import com.example.cicdsample.domain.usecase.TitleValidationException
import com.example.cicdsample.domain.usecase.ToggleTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 화면 상태를 만들고 사용자의 의도를 유스케이스로 넘긴다.
 *
 * 검증·정렬·집계 규칙은 전부 :domain 에 있으므로 여기에는 조립과 문구 변환만 남는다.
 * 그래서 ViewModel 테스트는 "상태가 제대로 합쳐지는가"만 확인하면 된다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskViewModel @Inject constructor(
    observeTasks: ObserveTasksUseCase,
    getTaskStats: GetTaskStatsUseCase,
    private val addTask: AddTaskUseCase,
    private val toggleTask: ToggleTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val clearCompleted: ClearCompletedUseCase,
) : ViewModel() {

    private data class FormState(
        val title: String = "",
        val priority: Priority = Priority.NORMAL,
        val errorMessage: String? = null,
    )

    private val filter = MutableStateFlow(TaskFilter.ALL)
    private val form = MutableStateFlow(FormState())

    val uiState: StateFlow<TaskUiState> = combine(
        filter,
        filter.flatMapLatest { observeTasks(it) },
        getTaskStats(),
        form,
    ) { currentFilter, tasks, stats, formState ->
        TaskUiState(
            tasks = tasks,
            filter = currentFilter,
            stats = stats,
            inputTitle = formState.title,
            inputPriority = formState.priority,
            errorMessage = formState.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TaskUiState(),
    )

    fun onTitleChange(value: String) {
        // 사용자가 다시 입력하기 시작하면 이전 에러는 치운다.
        form.value = form.value.copy(title = value, errorMessage = null)
    }

    fun onPriorityChange(priority: Priority) {
        form.value = form.value.copy(priority = priority)
    }

    fun onAddClick() {
        val current = form.value
        viewModelScope.launch {
            addTask(current.title, current.priority)
                .onSuccess { form.value = FormState(priority = current.priority) }
                .onFailure { error ->
                    form.value = current.copy(errorMessage = error.toMessage())
                }
        }
    }

    fun onToggle(id: Long) {
        viewModelScope.launch { toggleTask(id) }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { deleteTask(id) }
    }

    fun onFilterChange(next: TaskFilter) {
        filter.value = next
    }

    fun onClearCompleted() {
        viewModelScope.launch { clearCompleted() }
    }

    private fun Throwable.toMessage(): String = when ((this as? TitleValidationException)?.reason) {
        TitleError.BLANK -> "할 일을 입력해 주세요."
        TitleError.TOO_SHORT -> "${AddTaskUseCase.MIN_TITLE_LENGTH}자 이상 입력해 주세요."
        TitleError.TOO_LONG -> "${AddTaskUseCase.MAX_TITLE_LENGTH}자까지 입력할 수 있습니다."
        TitleError.DUPLICATE -> "이미 같은 할 일이 있습니다."
        null -> "할 일을 추가하지 못했습니다."
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
