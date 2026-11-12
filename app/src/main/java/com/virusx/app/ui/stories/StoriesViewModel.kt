package com.virusx.app.ui.stories

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.repository.StoriesRepository
import com.virusx.app.data.repository.StoryWithDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoriesUiState(
    val stories: List<StoryWithDetails> = emptyList(),
    val selectedIndex: Int = 0,
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class StoriesViewModel @Inject constructor(
    private val storiesRepository: StoriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoriesUiState())
    val uiState: StateFlow<StoriesUiState> = _uiState.asStateFlow()

    init {
        loadStories()
    }

    fun loadStories() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentUserId = storiesRepository.getCurrentUserId()
                )
            }
            storiesRepository.fetchActiveStories().fold(
                onSuccess = { list ->
                    _uiState.update { state ->
                        val safeIdx = if (list.isEmpty()) 0 else state.selectedIndex.coerceIn(0, list.lastIndex)
                        state.copy(
                            stories = list,
                            selectedIndex = safeIdx,
                            isLoading = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.localizedMessage ?: "تعذر تحميل الحالات"
                        )
                    }
                }
            )
        }
    }

    fun selectStoryIndex(index: Int) {
        _uiState.update { it.copy(selectedIndex = index) }
    }

    fun openCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }
    fun closeCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }
    fun clearMessage() = _uiState.update { it.copy(statusMessage = null) }

    fun publishTextStory(text: String, bgHex: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createTextStory(text, bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر الحالة النصية بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishMediaStory(uri: Uri, caption: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createMediaStory(uri, caption).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر الصورة في الحالات بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishPollStory(question: String, option1: String, option2: String, bgHex: String) {
        if (question.isBlank() || option1.isBlank() || option2.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createPollStory(question, listOf(option1, option2), bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر حالة التصويت بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishAskStory(prompt: String, bgHex: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createAskStory(prompt, bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر صندوق الأسئلة المجهولة بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun publishCountdownStory(title: String, targetIso: String, bgHex: String) {
        if (title.isBlank() || targetIso.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showCreateDialog = false) }
            storiesRepository.createCountdownStory(title, targetIso, bgHex).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم نشر العداد التنازلي بنجاح") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun voteOnPoll(pollId: String, optionIndex: Int) {
        viewModelScope.launch {
            storiesRepository.voteOnPoll(pollId, optionIndex).fold(
                onSuccess = { loadStories() },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun submitAnonymousQuestion(storyId: String, question: String) {
        viewModelScope.launch {
            storiesRepository.sendAnonymousQuestion(storyId, question).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "✅ تم إرسال سؤالك بشكل مجهول تماماً!") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            storiesRepository.deleteStory(storyId).fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم حذف الحالة") }
                    loadStories()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(statusMessage = err.localizedMessage) }
                }
            )
        }
    }
}
