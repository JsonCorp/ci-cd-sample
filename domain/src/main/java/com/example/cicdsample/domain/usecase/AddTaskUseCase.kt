package com.example.cicdsample.domain.usecase

import com.example.cicdsample.domain.model.Priority
import com.example.cicdsample.domain.model.Task
import com.example.cicdsample.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** 제목이 거부된 이유. UI 는 이 값을 문구로 바꾸기만 한다. */
enum class TitleError {
    BLANK,
    TOO_SHORT,
    TOO_LONG,
    DUPLICATE,
}

/** [AddTaskUseCase] 가 실패할 때 [Result] 에 담기는 예외. */
class TitleValidationException(val reason: TitleError) : IllegalArgumentException(reason.name)

/**
 * 할 일을 추가한다. 검증 규칙이 전부 여기 모여 있어서 UI 없이 단위 테스트로 확인할 수 있다.
 *
 * - 공백만 있으면 [TitleError.BLANK]
 * - 다듬은 뒤 2자 미만이면 [TitleError.TOO_SHORT]
 * - 다듬은 뒤 [MAX_TITLE_LENGTH]자 초과면 [TitleError.TOO_LONG]
 * - 이미 같은 제목이 있으면 [TitleError.DUPLICATE] (대소문자·연속 공백 무시)
 */
class AddTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(
        rawTitle: String,
        priority: Priority = Priority.NORMAL,
    ): Result<Task> {
        val title = rawTitle.trim()

        if (title.isEmpty()) return failure(TitleError.BLANK)
        if (title.length < MIN_TITLE_LENGTH) return failure(TitleError.TOO_SHORT)
        if (title.length > MAX_TITLE_LENGTH) return failure(TitleError.TOO_LONG)

        val existing = repository.observeTasks().first()
        if (existing.any { it.title.normalize() == title.normalize() }) {
            return failure(TitleError.DUPLICATE)
        }

        return Result.success(repository.addTask(title, priority))
    }

    private fun failure(reason: TitleError): Result<Task> =
        Result.failure(TitleValidationException(reason))

    /** 비교용 정규화 — 대소문자와 연속 공백 차이는 같은 제목으로 본다. */
    private fun String.normalize(): String = trim().lowercase().replace(WHITESPACE, " ")

    companion object {
        const val MIN_TITLE_LENGTH = 2
        const val MAX_TITLE_LENGTH = 40
        private val WHITESPACE = Regex("\\s+")
    }
}
