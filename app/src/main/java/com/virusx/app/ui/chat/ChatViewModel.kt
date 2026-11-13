package com.virusx.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.remote.dto.ChatSyncSessionDto
import com.virusx.app.data.remote.dto.ProfileDto
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.data.repository.ChatDetailRepository
import com.virusx.app.data.repository.ChatRepository
import com.virusx.app.util.VoiceNoteManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChatUiState(
    val chatId: String = "",
    val chatTitle: String = "محادثة VirusX",
    val isGroup: Boolean = false,
    val chatCreatorId: String? = null,
    val peerOnline: Boolean = false,
    val members: List<ProfileDto> = emptyList(),
    val memberSearchQuery: String = "",
    val memberSearchResults: List<ProfileDto> = emptyList(),
    val currentUserId: String? = null,
    val currentUserName: String = "عضو",
    val inputText: String = "",
    val replyToMessage: MessageEntity? = null,
    val editingMessage: MessageEntity? = null,
    val whisperTargetUser: ProfileDto? = null,
    val destructTimerSeconds: Int? = null,
    val selectedPitchFilter: String = "normal",
    val voiceViewOnce: Boolean = false,
    val isSending: Boolean = false,
    val statusBanner: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDetailRepository: ChatDetailRepository,
    private val chatRepository: ChatRepository,
    private val voiceNoteManager: VoiceNoteManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    val activeSyncSession: StateFlow<ChatSyncSessionDto?> = chatDetailRepository.activeSyncSession
    val typingUsers: StateFlow<Map<String, String>> = chatDetailRepository.typingUsers

    val isRecording: StateFlow<Boolean> = voiceNoteManager.isRecording
    val liveWaveform: StateFlow<List<Float>> = voiceNoteManager.liveWaveform
    val recordingDurationSeconds: StateFlow<Int> = voiceNoteManager.recordingDurationSeconds
    val currentlyPlayingMessageId: StateFlow<String?> = voiceNoteManager.currentlyPlayingMessageId
    val isTranscribing: StateFlow<Boolean> = voiceNoteManager.isTranscribing

    private var messagesObserveJob: Job? = null
    private var typingResetJob: Job? = null
    private var memberSearchJob: Job? = null
    private var initializedChatId: String? = null
    private var lastScreenshotAlertMs: Long = 0L

    fun initChat(chatId: String) {
        if (initializedChatId == chatId) return
        initializedChatId = chatId

        val myId = chatDetailRepository.getCurrentUserId()
        _uiState.update { it.copy(chatId = chatId, currentUserId = myId) }

        messagesObserveJob?.cancel()
        messagesObserveJob = viewModelScope.launch {
            chatDetailRepository.observeLocalMessages(chatId).collect { list ->
                _messages.value = list
            }
        }

        viewModelScope.launch {
            val myProfile = authRepository.refreshCurrentUserProfile()
            refreshChatMembers(chatId, myId, myProfile?.displayName ?: "عضو")
            chatDetailRepository.fetchInitialMessagesAndSyncSession(chatId)
            chatDetailRepository.subscribeToChatRealtime(chatId, viewModelScope)
        }
    }

    private suspend fun refreshChatMembers(chatId: String, myId: String?, fallbackName: String) {
        val (chatDto, memberProfiles) = chatDetailRepository.fetchChatDetailsAndMembers(chatId)
        val isGroupChat = chatDto?.type == "group"
        val peer = if (!isGroupChat) memberProfiles.firstOrNull { it.id != myId } else null
        val title = if (isGroupChat) {
            chatDto?.name ?: "مجموعة VirusX"
        } else {
            peer?.displayName ?: chatDto?.name ?: "محادثة خاصة"
        }

        _uiState.update {
            it.copy(
                chatTitle = title,
                isGroup = isGroupChat,
                chatCreatorId = chatDto?.createdBy,
                peerOnline = peer?.isOnline ?: false,
                members = memberProfiles,
                currentUserName = fallbackName
            )
        }
    }

    fun onMemberSearchChange(query: String) {
        _uiState.update { it.copy(memberSearchQuery = query) }
        memberSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(memberSearchResults = emptyList()) }
            return
        }
        memberSearchJob = viewModelScope.launch {
            delay(250L)
            val existingIds = _uiState.value.members.map { it.id }.toSet()
            val found = chatRepository.searchUsersByNameUsernameOrPhone(query)
                .filter { it.id !in existingIds }
            _uiState.update { it.copy(memberSearchResults = found) }
        }
    }

    fun addMemberToGroup(user: ProfileDto) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.addMemberToGroup(chatId, user.id).fold(
                onSuccess = {
                    refreshChatMembers(chatId, _uiState.value.currentUserId, _uiState.value.currentUserName)
                    _uiState.update {
                        it.copy(
                            memberSearchQuery = "",
                            memberSearchResults = emptyList(),
                            statusBanner = "✅ تمت إضافة ${user.displayName} إلى المجموعة"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = cleanError(err, "تعذر إضافة العضو")) }
                }
            )
        }
    }

    fun removeMemberFromGroup(user: ProfileDto) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.removeMemberFromGroup(chatId, user.id).fold(
                onSuccess = {
                    refreshChatMembers(chatId, _uiState.value.currentUserId, _uiState.value.currentUserName)
                    _uiState.update { it.copy(statusBanner = "تمت إزالة ${user.displayName} من المجموعة") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = cleanError(err, "تعذر إزالة العضو")) }
                }
            )
        }
    }

    fun onInputTextChange(newText: String) {
        _uiState.update { it.copy(inputText = newText, errorMessage = null) }
        val state = _uiState.value
        if (state.chatId.isBlank()) return

        typingResetJob?.cancel()
        typingResetJob = viewModelScope.launch {
            chatDetailRepository.sendTypingIndicator(state.chatId, state.currentUserName, newText.isNotBlank())
            if (newText.isNotBlank()) {
                delay(2500L)
                chatDetailRepository.sendTypingIndicator(state.chatId, state.currentUserName, false)
            }
        }
    }

    fun setReplyToMessage(message: MessageEntity?) {
        _uiState.update { it.copy(replyToMessage = message, editingMessage = null) }
    }

    fun startEditingMessage(message: MessageEntity) {
        _uiState.update {
            it.copy(
                editingMessage = message,
                replyToMessage = null,
                inputText = message.content.orEmpty()
            )
        }
    }

    fun cancelEditingMessage() {
        _uiState.update { it.copy(editingMessage = null, inputText = "") }
    }

    fun cycleDestructTimer() {
        val next = when (_uiState.value.destructTimerSeconds) {
            null -> 10
            10 -> 30
            30 -> 60
            else -> null
        }
        _uiState.update { it.copy(destructTimerSeconds = next) }
    }

    fun setWhisperTarget(profile: ProfileDto?) {
        _uiState.update { it.copy(whisperTargetUser = profile) }
    }

    fun setPitchFilter(filter: String) {
        _uiState.update { it.copy(selectedPitchFilter = filter) }
    }

    fun toggleVoiceViewOnce() {
        _uiState.update { it.copy(voiceViewOnce = !it.voiceViewOnce) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, statusBanner = null) }
    }

    fun sendTextMessage() {
        val state = _uiState.value
        val content = state.inputText.trim()
        if (content.isEmpty() || state.isSending || state.chatId.isBlank()) return

        val editingTarget = state.editingMessage
        if (editingTarget != null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSending = true, inputText = "", editingMessage = null) }
                chatDetailRepository.editMessage(editingTarget.id, content).fold(
                    onSuccess = {
                        chatDetailRepository.fetchInitialMessagesAndSyncSession(state.chatId)
                        _uiState.update { it.copy(isSending = false) }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                inputText = content,
                                errorMessage = cleanError(err, "فشل تعديل الرسالة")
                            )
                        }
                    }
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, inputText = "") }
            chatDetailRepository.sendTypingIndicator(state.chatId, state.currentUserName, false)

            chatDetailRepository.sendTextMessage(
                chatId = state.chatId,
                content = content,
                replyToMessageId = state.replyToMessage?.id,
                whisperTargetId = state.whisperTargetUser?.id,
                destructSeconds = state.destructTimerSeconds
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            replyToMessage = null,
                            whisperTargetUser = null,
                            destructTimerSeconds = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = content,
                            errorMessage = cleanError(err, "فشل إرسال الرسالة")
                        )
                    }
                }
            )
        }
    }

    fun toggleMessageReaction(messageId: String, emoji: String) {
        val chatId = _uiState.value.chatId
        viewModelScope.launch {
            chatDetailRepository.toggleReaction(messageId, emoji).onSuccess {
                if (chatId.isNotBlank()) {
                    chatDetailRepository.fetchInitialMessagesAndSyncSession(chatId)
                }
            }
        }
    }

    fun expireSelfDestructMessage(messageId: String) {
        viewModelScope.launch {
            chatDetailRepository.expireSelfDestructMessage(messageId)
        }
    }

    fun reportScreenshotTaken() {
        val now = System.currentTimeMillis()
        if (now - lastScreenshotAlertMs < 5000L) return
        lastScreenshotAlertMs = now
        val state = _uiState.value
        if (state.chatId.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.sendTextMessage(
                chatId = state.chatId,
                content = "[SCREENSHOT_ALERT] ⚠️ قام ${state.currentUserName} بأخذ لقطة شاشة لهذه المحادثة!"
            )
        }
    }

    fun sendImageMessage(uri: Uri) {
        val state = _uiState.value
        if (state.isSending || state.chatId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            chatDetailRepository.sendImageMessage(
                chatId = state.chatId,
                imageUri = uri,
                caption = state.inputText.takeIf { it.isNotBlank() },
                replyToMessageId = state.replyToMessage?.id,
                whisperTargetId = state.whisperTargetUser?.id,
                destructSeconds = state.destructTimerSeconds
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = "",
                            replyToMessage = null,
                            whisperTargetUser = null,
                            destructTimerSeconds = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = cleanError(err, "فشل رفع الصورة")
                        )
                    }
                }
            )
        }
    }

    fun startVoiceRecording() {
        voiceNoteManager.startRecording(viewModelScope).onFailure { err ->
            _uiState.update {
                it.copy(errorMessage = cleanError(err, "تعذر بدء التسجيل الصوتى"))
            }
        }
    }

    fun cancelVoiceRecording() {
        val recorded = voiceNoteManager.stopRecording()
        recorded?.file?.delete()
    }

    fun stopAndSendVoiceNote() {
        val state = _uiState.value
        val recorded = voiceNoteManager.stopRecording() ?: return
        if (state.chatId.isBlank()) {
            recorded.file.delete()
            return
        }

        val attachedTranscript = state.inputText.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            chatDetailRepository.sendVoiceNoteMessage(
                chatId = state.chatId,
                audioFile = recorded.file,
                durationSeconds = recorded.durationSeconds,
                waveform = recorded.waveform,
                viewOnce = state.voiceViewOnce,
                pitchFilter = state.selectedPitchFilter,
                transcript = attachedTranscript,
                replyToMessageId = state.replyToMessage?.id,
                whisperTargetId = state.whisperTargetUser?.id
            ).fold(
                onSuccess = {
                    recorded.file.delete()
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = "",
                            replyToMessage = null,
                            whisperTargetUser = null,
                            voiceViewOnce = false,
                            selectedPitchFilter = "normal"
                        )
                    }
                },
                onFailure = { err ->
                    recorded.file.delete()
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = cleanError(err, "فشل إرسال الرسالة الصوتية")
                        )
                    }
                }
            )
        }
    }

    fun transcribeSpeechIntoInput() {
        voiceNoteManager.recognizeSpeechToText(
            onResult = { recognized ->
                _uiState.update { current ->
                    val combined = if (current.inputText.isBlank()) recognized else "${current.inputText} $recognized"
                    current.copy(inputText = combined)
                }
            },
            onError = { errMsg ->
                _uiState.update { it.copy(errorMessage = errMsg) }
            }
        )
    }

    fun togglePlayVoiceNote(message: MessageEntity) {
        val myId = _uiState.value.currentUserId
        if (message.voiceViewOnce && message.voicePlayed && message.senderId != myId) {
            _uiState.update {
                it.copy(errorMessage = "🔒 هذه الرسالة الصوتية للاستماع مرة واحدة فقط وقد تم تشغيلها بالفعل")
            }
            return
        }

        if (currentlyPlayingMessageId.value == message.id) {
            voiceNoteManager.stopPlayback()
            return
        }

        val url = message.mediaUrl
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "رابط الملف الصوتى غير متوفر") }
            return
        }

        voiceNoteManager.playVoiceNote(
            messageId = message.id,
            mediaUrl = url,
            pitchFilter = message.voicePitchFilter
        ) { finishedMsgId ->
            if (message.voiceViewOnce && message.senderId != myId) {
                viewModelScope.launch {
                    chatDetailRepository.markVoiceNotePlayed(finishedMsgId)
                }
            }
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            chatDetailRepository.deleteMessageForEveryone(messageId).onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = cleanError(err, "تعذر حذف الرسالة"))
                }
            }
        }
    }

    fun startSyncSession(mediaUrl: String, mediaType: String) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank() || mediaUrl.isBlank()) return
        viewModelScope.launch {
            chatDetailRepository.startSyncMediaSession(chatId, mediaUrl, mediaType).onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = cleanError(err, "تعذر بدء جلسة المشغل المتزامن"))
                }
            }
        }
    }

    fun toggleSyncSessionPlayPause() {
        val session = activeSyncSession.value ?: return
        viewModelScope.launch {
            chatDetailRepository.updateSyncSessionPlayback(
                sessionId = session.id,
                positionSeconds = session.positionSeconds,
                isPlaying = !session.isPlaying
            )
        }
    }

    fun seekSyncSessionBy(deltaSeconds: Double) {
        val session = activeSyncSession.value ?: return
        val nextPos = (session.positionSeconds + deltaSeconds).coerceAtLeast(0.0)
        viewModelScope.launch {
            chatDetailRepository.updateSyncSessionPlayback(
                sessionId = session.id,
                positionSeconds = nextPos,
                isPlaying = session.isPlaying
            )
        }
    }

    private fun cleanError(err: Throwable, fallback: String): String {
        val raw = err.localizedMessage ?: return fallback
        return raw.substringBefore("URL:").substringBefore("Headers:").trim().ifBlank { fallback }
    }

    override fun onCleared() {
        voiceNoteManager.stopPlayback()
        voiceNoteManager.stopRecording()
        viewModelScope.launch {
            withContext(NonCancellable) {
                chatDetailRepository.unsubscribeFromChatRealtime()
            }
        }
        super.onCleared()
    }
}
