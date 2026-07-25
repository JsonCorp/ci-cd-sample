package com.example.cicdsample.ui.task

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.model.TaskStats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI 테스트.
 *
 * [TaskScreen] 이 stateless 이므로 Activity 도, Hilt 도 띄우지 않는다.
 * `createAndroidComposeRule` 대신 [createComposeRule] 을 쓸 수 있는 이유이고,
 * 그래서 HiltTestRunner·@HiltAndroidTest 같은 장치가 전혀 필요 없다.
 */
@RunWith(AndroidJUnit4::class)
class TaskScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTasks = listOf(
        Task(id = 1, title = "장보기", priority = Priority.HIGH, done = false, order = 0),
        Task(id = 2, title = "청소하기", priority = Priority.NORMAL, done = true, order = 1),
    )

    @Test
    fun 목록과_통계가_상태대로_그려진다() {
        setContent(
            TaskUiState(
                tasks = sampleTasks,
                stats = TaskStats(total = 2, done = 1, completionRate = 50),
            ),
        )

        composeRule.onNodeWithTag("text_stats").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 2 완료 (50%)").assertIsDisplayed()
        composeRule.onNodeWithText("장보기").assertIsDisplayed()
        composeRule.onNodeWithText("청소하기").assertIsDisplayed()
    }

    @Test
    fun 목록이_비면_안내_문구가_뜬다() {
        setContent(TaskUiState())

        composeRule.onNodeWithTag("text_empty").assertIsDisplayed()
    }

    @Test
    fun 에러가_있으면_에러_문구를_보여준다() {
        setContent(TaskUiState(errorMessage = "이미 같은 할 일이 있습니다."))

        composeRule.onNodeWithTag("text_error").assertIsDisplayed()
        composeRule.onNodeWithText("이미 같은 할 일이 있습니다.").assertIsDisplayed()
    }

    @Test
    fun 입력하면_onTitleChange_로_전달된다() {
        var typed = ""
        setContent(TaskUiState(), onTitleChange = { typed = it })

        composeRule.onNodeWithTag("input_title").performTextInput("운동하기")

        assertEquals("운동하기", typed)
    }

    @Test
    fun 추가_버튼을_누르면_onAddClick_이_호출된다() {
        var clicks = 0
        setContent(TaskUiState(inputTitle = "운동하기"), onAddClick = { clicks++ })

        composeRule.onNodeWithTag("btn_add").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun 체크박스를_누르면_해당_id_로_onToggle_이_호출된다() {
        var toggledId = -1L
        setContent(
            TaskUiState(tasks = sampleTasks, stats = TaskStats(2, 1, 50)),
            onToggle = { toggledId = it },
        )

        composeRule.onNodeWithTag("checkbox_task_0").performClick()

        assertEquals(1L, toggledId)
    }

    @Test
    fun 삭제_버튼을_누르면_해당_id_로_onDelete_가_호출된다() {
        var deletedId = -1L
        setContent(
            TaskUiState(tasks = sampleTasks, stats = TaskStats(2, 1, 50)),
            onDelete = { deletedId = it },
        )

        composeRule.onNodeWithTag("btn_delete_1").performClick()

        assertEquals(2L, deletedId)
    }

    @Test
    fun 완료한_항목이_없으면_지우기_버튼이_비활성이다() {
        setContent(TaskUiState(tasks = sampleTasks, stats = TaskStats(2, 0, 0)))

        composeRule.onNodeWithTag("btn_clear_completed").assertIsNotEnabled()
    }

    private fun setContent(
        state: TaskUiState,
        onTitleChange: (String) -> Unit = {},
        onAddClick: () -> Unit = {},
        onToggle: (Long) -> Unit = {},
        onDelete: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            TaskScreen(
                state = state,
                onTitleChange = onTitleChange,
                onPriorityChange = {},
                onAddClick = onAddClick,
                onToggle = onToggle,
                onDelete = onDelete,
                onFilterChange = {},
                onClearCompleted = {},
            )
        }
    }
}
