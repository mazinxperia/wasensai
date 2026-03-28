package com.mazin.wasensai.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazin.wasensai.data.model.CallLog
import com.mazin.wasensai.data.model.Chat
import com.mazin.wasensai.data.model.Contact
import com.mazin.wasensai.data.model.ExportInfo
import com.mazin.wasensai.data.model.Group
import com.mazin.wasensai.data.model.MediaEntry
import com.mazin.wasensai.data.model.Message
import com.mazin.wasensai.data.repository.MediaFailureReason
import com.mazin.wasensai.data.repository.SyncState
import com.mazin.wasensai.data.repository.ViewerRepository
import com.mazin.wasensai.ui.screens.viewer.ChatListUiModel
import com.mazin.wasensai.ui.screens.viewer.ChatRenderData
import com.mazin.wasensai.ui.screens.viewer.ViewerMediaAvailability
import com.mazin.wasensai.ui.screens.viewer.buildChatRenderData
import com.mazin.wasensai.ui.screens.viewer.buildViewerMediaAvailabilityMap
import com.mazin.wasensai.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed class ViewerLoadState {
    object Idle    : ViewerLoadState()
    object Loading : ViewerLoadState()
    object Success : ViewerLoadState()
    data class Error(val message: String) : ViewerLoadState()
}

sealed class ChatLoadState {
    object Idle    : ChatLoadState()
    object Loading : ChatLoadState()
    object Ready : ChatLoadState()
}

data class SearchResults(
    val messages: List<Message> = emptyList(),
    val chats: List<Chat> = emptyList()
)

data class MediaState(
    val file: File? = null,
    val isReady: Boolean = false,
    val failureReason: MediaFailureReason? = null
)

private fun MediaState.sameAs(other: MediaState): Boolean =
    file?.absolutePath == other.file?.absolutePath &&
        isReady == other.isReady &&
        failureReason == other.failureReason

private fun normalizeContactKey(jid: String): String =
    jid.trim().substringBefore("@").lowercase()

private data class PreparedChatWindow(
    val allMessages: List<Message>,
    val visibleMessages: List<Message>,
    val keyedMessages: Map<String, Message>
)

private data class PreloadedChatPayload(
    val initialPageSize: Int,
    val visibleMessages: List<Message>,
    val renderData: ChatRenderData,
    val keyedMessages: Map<String, Message>,
    val availabilityByMessageId: Map<Long, ViewerMediaAvailability>
)

internal data class CurrentChatUiState(
    val chatId: Long? = null,
    val loadState: ChatLoadState = ChatLoadState.Idle,
    val messages: List<Message> = emptyList(),
    val renderData: ChatRenderData? = null,
    val availabilityByMessageId: Map<Long, ViewerMediaAvailability> = emptyMap()
)

data class ViewerLoadingUiState(
    val overallPercent: Int = 0,
    val currentPhase: String = "Opening archive...",
    val currentLogLine: String = ""
)

private data class ChatPreloadState(
    val isRunning: Boolean = false,
    val completedChats: Int = 0,
    val totalChats: Int = 0,
    val currentChatName: String = "",
    val currentLogLine: String = ""
)

private const val HOT_CHAT_PRELOAD_COUNT = 30
private const val INITIAL_PAGE_SIZE = 96
private const val HOT_CHAT_PAGE_SIZE = 160
private const val PAGE_INCREMENT    = 60
@OptIn(FlowPreview::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val viewerRepository: ViewerRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<ViewerLoadState>(ViewerLoadState.Idle)
    val loadState: StateFlow<ViewerLoadState> = _loadState.asStateFlow()

    val syncState: StateFlow<SyncState> = viewerRepository.syncState

    private val _chatLoadState = MutableStateFlow<ChatLoadState>(ChatLoadState.Idle)
    val chatLoadState: StateFlow<ChatLoadState> = _chatLoadState.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentMessages: StateFlow<List<Message>> = _currentMessages.asStateFlow()

    private val _currentChatId = MutableStateFlow<Long?>(null)
    val currentChatId: StateFlow<Long?> = _currentChatId.asStateFlow()

    private val _contacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val contacts: StateFlow<Map<String, Contact>> = _contacts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    val callLogs: StateFlow<List<CallLog>> = _callLogs.asStateFlow()

    private val _starredMessages = MutableStateFlow<List<Message>>(emptyList())
    val starredMessages: StateFlow<List<Message>> = _starredMessages.asStateFlow()

    private val _exportInfo = MutableStateFlow<ExportInfo?>(null)
    val exportInfo: StateFlow<ExportInfo?> = _exportInfo.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow(SearchResults())
    val searchResults: StateFlow<SearchResults> = _searchResults.asStateFlow()

    private val _mediaStateVersion = MutableStateFlow(0)
    val mediaStateVersion: StateFlow<Int> = _mediaStateVersion.asStateFlow()
    private val _currentChatRenderData = MutableStateFlow<ChatRenderData?>(null)
    internal val currentChatRenderData: StateFlow<ChatRenderData?> = _currentChatRenderData.asStateFlow()
    private val _currentChatAvailability = MutableStateFlow<Map<Long, ViewerMediaAvailability>>(emptyMap())
    internal val currentChatAvailability: StateFlow<Map<Long, ViewerMediaAvailability>> = _currentChatAvailability.asStateFlow()
    private val _currentChatUiState = MutableStateFlow(CurrentChatUiState())
    internal val currentChatUiState: StateFlow<CurrentChatUiState> = _currentChatUiState.asStateFlow()
    private val _chatListUiModels = MutableStateFlow<Map<Long, ChatListUiModel>>(emptyMap())
    internal val chatListUiModels: StateFlow<Map<Long, ChatListUiModel>> = _chatListUiModels.asStateFlow()
    private val _chatPreloadState = MutableStateFlow(ChatPreloadState())
    private val _loadingUiState = MutableStateFlow(ViewerLoadingUiState())
    val loadingUiState: StateFlow<ViewerLoadingUiState> = _loadingUiState.asStateFlow()

    private val _chatFilter = MutableStateFlow(0)
    val chatFilter: StateFlow<Int> = _chatFilter.asStateFlow()

    // â”€â”€â”€ IN-CHAT SEARCH (separate from global chat-list search) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val _inChatSearchActive = MutableStateFlow(false)
    val inChatSearchActive: StateFlow<Boolean> = _inChatSearchActive.asStateFlow()

    private val _inChatSearchQuery = MutableStateFlow("")
    val inChatSearchQuery: StateFlow<String> = _inChatSearchQuery.asStateFlow()

    private val _inChatMatchIds = MutableStateFlow<List<Long>>(emptyList())
    val inChatMatchIds: StateFlow<List<Long>> = _inChatMatchIds.asStateFlow()

    private val _inChatMatchIndex = MutableStateFlow(0)
    val inChatMatchIndex: StateFlow<Int> = _inChatMatchIndex.asStateFlow()

    val filteredChats: StateFlow<List<Chat>> = combine(_chats, _chatFilter) { chats, filter ->
        when (filter) {
            1    -> chats.filter { !it.isGroup }
            2    -> chats.filter { it.isGroup }
            else -> chats
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentChat: StateFlow<Chat?> = combine(_currentChatId, _chats) { chatId, chats ->
        chatId?.let { id -> chats.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // UI Load Rule: true only after chats + avatars are both fully ready
    private val _isInitialReady = MutableStateFlow(false)
    val isInitialReady: StateFlow<Boolean> = _isInitialReady.asStateFlow()

    private val contactNameMap = HashMap<String, String>(1024)
    private val chatByIdMap    = HashMap<Long, Chat>(512)
    private val avatarAliasMap = HashMap<String, String>(1024)
    private val mediaStateCache = ConcurrentHashMap<Long, MutableStateFlow<MediaState>>(1024)
    private val preloadedChats = ConcurrentHashMap<Long, PreloadedChatPayload>(256)
    // BUG 14: keyId â†’ Message map for the current open chat (for reply-scroll + sender lookup)
    private var currentChatKeyMap: Map<String, Message> = emptyMap()
    private val refreshMutex   = Mutex()

    private var openedChatId    = -1L
    private var currentPageSize = INITIAL_PAGE_SIZE
    private var refreshJob: Job? = null
    private var searchJob: Job? = null

    init {
        _searchQuery
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query ->
                searchJob?.cancel()
                searchJob = viewModelScope.launch(Dispatchers.Default) {
                    val nextResults = if (query.isNotBlank()) {
                        SearchResults(
                            messages = viewerRepository.searchMessages(query),
                            chats = viewerRepository.searchChats(query)
                        )
                    } else {
                        SearchResults()
                    }
                    withContext(Dispatchers.Main.immediate) {
                        _searchResults.value = nextResults
                    }
                }
            }
            .launchIn(viewModelScope)

        syncState
            .onEach(::updateLoadingUiFromSync)
            .launchIn(viewModelScope)
    }

    private fun syncPercent(sync: SyncState): Int {
        val step = sync.currentStep.lowercase()
        return when {
            sync.isComplete -> 70
            "opening file" in step -> 5
            "building index" in step -> 10
            "indexing archive files" in step -> {
                val total = sync.total.coerceAtLeast(1)
                10 + ((sync.progress.coerceIn(0, total) * 15) / total)
            }
            "indexed " in step -> 25
            "parsing archive data" in step -> 30
            "preparing viewer data" in step -> 35
            "syncing profile photos" in step -> {
                val total = sync.total.coerceAtLeast(1)
                35 + ((sync.progress.coerceIn(0, total) * 10) / total)
            }
            "resolving media" in step || "syncing media" in step -> {
                val total = sync.total.coerceAtLeast(1)
                45 + ((sync.progress.coerceIn(0, total) * 25) / total)
            }
            else -> 0
        }.coerceIn(0, 70)
    }

    private fun updateLoadingUiFromSync(sync: SyncState) {
        if (_chatPreloadState.value.isRunning) return
        _loadingUiState.value = ViewerLoadingUiState(
            overallPercent = syncPercent(sync),
            currentPhase = sync.currentStep.ifEmpty { "Opening archive..." },
            currentLogLine = sync.logs.lastOrNull().orEmpty()
        )
    }

    private fun updateLoadingUiForPreload(state: ChatPreloadState) {
        val total = state.totalChats.coerceAtLeast(1)
        val percent = 70 + ((state.completedChats.coerceIn(0, total) * 30) / total)
        _loadingUiState.value = ViewerLoadingUiState(
            overallPercent = percent.coerceIn(70, 100),
            currentPhase = if (state.totalChats > 0) {
                "Preloading chats ${state.completedChats}/${state.totalChats}"
            } else {
                "Preloading chats..."
            },
            currentLogLine = state.currentLogLine.ifEmpty {
                state.currentChatName.ifEmpty { "Preparing chats for instant open..." }
            }
        )
    }

    private fun buildChatListUiModel(
        chat: Chat,
        lastMessage: Message?
    ): ChatListUiModel {
        val displayName = if (chat.isGroup) {
            chat.subject.ifEmpty { "Group" }
        } else {
            getContactName(chat.jid)
        }
        val avatarPath = getAvatar(chat.jid)?.absolutePath
        val isLastFromMe = lastMessage?.fromMe == 1
        val rawPreview = when (chat.lastMessageType) {
            1 -> "\uD83D\uDCF7 Photo"
            2 -> "\uD83C\uDFB5 Audio"
            3 -> "\uD83D\uDCF9 Video"
            9 -> "\uD83D\uDCC4 Document"
            13, 20 -> "Sticker"
            else -> chat.lastMessage.ifEmpty { "No messages" }
        }
        val previewText = if (isLastFromMe && chat.lastMessage.isNotEmpty()) {
            "You: $rawPreview"
        } else {
            rawPreview
        }
        return ChatListUiModel(
            chatId = chat.id,
            displayName = displayName,
            avatarPath = avatarPath,
            previewText = previewText,
            timestampText = DateUtils.formatChatTimestamp(chat.sortTimestamp)
        )
    }

    private fun clearSessionCaches() {
        refreshJob?.cancel()
        refreshJob = null
        searchJob?.cancel()
        searchJob = null
        mediaStateCache.clear()
        preloadedChats.clear()
        currentChatKeyMap = emptyMap()
        _mediaStateVersion.value = 0
        publishCurrentChatState(
            chatId = null,
            loadState = ChatLoadState.Idle,
            messages = emptyList(),
            renderData = null,
            availabilityByMessageId = emptyMap()
        )
        _chatListUiModels.value = emptyMap()
        _chatPreloadState.value = ChatPreloadState()
        _loadingUiState.value = ViewerLoadingUiState()
    }

    // â”€â”€â”€ LOAD FILE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun loadWaView(uri: Uri) {
        viewModelScope.launch {
            clearSessionCaches()
            _loadState.value = ViewerLoadState.Loading
            _loadingUiState.value = ViewerLoadingUiState(
                overallPercent = 0,
                currentPhase = "Opening archive...",
                currentLogLine = ""
            )
            viewerRepository.loadWaView(uri)
                .onSuccess { waView ->
                    withContext(Dispatchers.Default) {
                        contactNameMap.clear()
                        avatarAliasMap.clear()
                        waView.contacts.forEach { contact ->
                            val name = contact.displayName.takeIf { it.isNotEmpty() }
                                ?: contact.waName.takeIf { it.isNotEmpty() }
                                ?: contact.jid.substringBefore("@")
                            val exactJid = contact.jid.trim().lowercase()
                            val normalizedKey = normalizeContactKey(contact.jid)
                            contactNameMap[exactJid] = name
                            contactNameMap[normalizedKey] = name
                            contact.avatarFile
                                .takeIf { it.isNotEmpty() }
                                ?.lowercase()
                                ?.let { avatarAlias ->
                                    avatarAliasMap[exactJid] = avatarAlias
                                    avatarAliasMap.putIfAbsent(normalizedKey, avatarAlias)
                                }
                        }
                        chatByIdMap.clear()
                        waView.chats.forEach { chat ->
                            chatByIdMap[chat.id] = chat
                            chat.avatarFile
                                .takeIf { it.isNotEmpty() }
                                ?.lowercase()
                                ?.let { avatarAlias ->
                                    val exactJid = chat.jid.trim().lowercase()
                                    val normalizedKey = normalizeContactKey(chat.jid)
                                    avatarAliasMap[exactJid] = avatarAlias
                                    avatarAliasMap.putIfAbsent(normalizedKey, avatarAlias)
                                }
                        }
                    }
                    val visibleChats = ArrayList<Chat>(waView.chats.size)
                    val chatListModels = HashMap<Long, ChatListUiModel>(waView.chats.size)
                    withContext(Dispatchers.Default) {
                        waView.chats.forEach { chat ->
                            val messages = viewerRepository.getMessagesByChatId(chat.id)
                            val hasVisibleMessage = messages.any { msg ->
                                msg.messageType != 11 &&
                                    !(msg.isSystem && msg.textData.isEmpty() && msg.mediaFilePath.isEmpty())
                            }
                            if (!hasVisibleMessage) return@forEach
                            visibleChats += chat
                            chatListModels[chat.id] = buildChatListUiModel(chat, messages.lastOrNull())
                        }
                    }
                    _chats.value = visibleChats
                    _chatListUiModels.value = chatListModels
                    _contacts.value        = waView.contacts.associateBy { it.jid }
                    _callLogs.value        = waView.callLogs
                    _exportInfo.value      = waView.exportInfo
                    _starredMessages.value = viewerRepository.getStarredMessages()
                    viewerRepository.awaitSyncComplete()
                    preloadAllChats()
                    _loadingUiState.value = ViewerLoadingUiState(
                        overallPercent = 100,
                        currentPhase = "Ready",
                        currentLogLine = "Viewer is ready."
                    )
                    _loadState.value       = ViewerLoadState.Success
                    // UI Load Rule: chats + avatars are now fully ready â€” gate opens
                    _isInitialReady.value  = true
                }
                .onFailure { e ->
                    _loadState.value = ViewerLoadState.Error(e.message ?: "Failed to load file")
                }
        }
    }

    // â”€â”€â”€ CHAT OPEN â€” Phase 3 priority extraction â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun openChat(chatId: Long) {
        refreshJob?.cancel()
        openedChatId = chatId
        // Reset in-chat search when navigating to a new chat
        deactivateInChatSearch()

        preloadedChats[chatId]?.let { payload ->
            applyPreloadedChatPayload(chatId, payload)
            return
        }

        currentPageSize = INITIAL_PAGE_SIZE
        viewModelScope.launch {
            // Hard barrier: clear stale state, block UI immediately
            publishCurrentChatState(
                chatId = chatId,
                loadState = ChatLoadState.Loading,
                messages = emptyList(),
                renderData = null,
                availabilityByMessageId = emptyMap()
            )

            val preparedWindow = withContext(Dispatchers.Default) {
                val allMessages = viewerRepository.getMessagesByChatId(chatId)
                val visibleMessages = if (allMessages.size <= currentPageSize) {
                    allMessages
                } else {
                    allMessages.subList(allMessages.size - currentPageSize, allMessages.size)
                }
                PreparedChatWindow(
                    allMessages = allMessages,
                    visibleMessages = visibleMessages,
                    keyedMessages = allMessages.asSequence()
                        .filter { it.keyId.isNotEmpty() }
                        .associateBy({ it.keyId }, { it })
                )
            }
            // BUG 14: Build keyId map for the entire chat (all pages, not just visible page)
            // This allows reply-scroll to work even for messages outside the current page
            currentChatKeyMap = preparedWindow.keyedMessages
            val initialRenderData = buildChatRenderDataFor(chatId, preparedWindow.visibleMessages)
            val initialAvailability = buildAvailabilityMap(preparedWindow.visibleMessages)

            // Await full media extraction BEFORE populating messages or setting Ready
            if (viewerRepository.isChatReady(chatId)) {
                // Messages and Ready set atomically â€” UI unblocks with all data present
                publishCurrentChatState(
                    chatId = chatId,
                    loadState = ChatLoadState.Ready,
                    messages = preparedWindow.visibleMessages,
                    renderData = initialRenderData,
                    availabilityByMessageId = initialAvailability
                )
                startRefresh(preparedWindow.visibleMessages, reason = "openChat:cached:$chatId", await = false)
            } else {
                viewerRepository.loadChatMedia(chatId)
                // Media fully extracted â€” now safe to populate messages and unblock UI
                publishCurrentChatState(
                    chatId = chatId,
                    loadState = ChatLoadState.Ready,
                    messages = preparedWindow.visibleMessages,
                    renderData = initialRenderData,
                    availabilityByMessageId = initialAvailability
                )
                startRefresh(preparedWindow.visibleMessages, reason = "openChat:extracted:$chatId", await = false)
            }
        }
    }

    fun showPreloadedChatIfAvailable(chatId: Long): Boolean {
        refreshJob?.cancel()
        openedChatId = chatId
        deactivateInChatSearch()
        val payload = preloadedChats[chatId] ?: return false
        applyPreloadedChatPayload(chatId, payload)
        return true
    }

    private suspend fun buildChatRenderDataFor(
        chatId: Long,
        messages: List<Message>
    ): ChatRenderData = buildChatRenderDataFor(
        chatId = chatId,
        messages = messages,
        resolveMessageByKeyId = { keyId -> currentChatKeyMap[keyId] }
    )

    private suspend fun buildChatRenderDataFor(
        chatId: Long,
        messages: List<Message>,
        resolveMessageByKeyId: (String) -> Message?
    ): ChatRenderData = withContext(Dispatchers.Default) {
        val mediaIndexByMessageId = viewerRepository.getMediaMessages(chatId)
            .withIndex()
            .associate { (index, message) -> message.id to index }
        buildChatRenderData(
            messages = messages,
            chatIsGroup = chatByIdMap[chatId]?.isGroup == true,
            searchQuery = _inChatSearchQuery.value,
            inChatMatchIdSet = _inChatMatchIds.value.toHashSet(),
            mediaIndexByMessageId = mediaIndexByMessageId,
            resolveContactName = ::getContactName,
            resolveAvatarPath = { jid -> getAvatar(jid)?.absolutePath },
            resolveMessageByKeyId = resolveMessageByKeyId
        )
    }

    private suspend fun buildAvailabilityMap(
        messages: List<Message>
    ): Map<Long, ViewerMediaAvailability> = withContext(Dispatchers.Default) {
        buildViewerMediaAvailabilityMap(
            viewModel = this@ViewerViewModel,
            messages = messages.filter { it.mediaFilePath.isNotEmpty() }
        )
    }

    private suspend fun buildPreloadedChatPayload(
        chatId: Long,
        initialPageSize: Int
    ): PreloadedChatPayload {
        val allMessages = viewerRepository.getMessagesByChatId(chatId)
        val windowSize = initialPageSize.coerceAtLeast(1)
        val visibleMessages = if (allMessages.size <= windowSize) {
            allMessages
        } else {
            allMessages.subList(allMessages.size - windowSize, allMessages.size)
        }
        val keyedMessages = allMessages.asSequence()
            .filter { it.keyId.isNotEmpty() }
            .associateBy({ it.keyId }, { it })
        viewerRepository.loadChatMedia(chatId)
        val renderData = buildChatRenderDataFor(
            chatId = chatId,
            messages = visibleMessages,
            resolveMessageByKeyId = keyedMessages::get
        )
        val availabilityByMessageId = buildAvailabilityMap(visibleMessages)
        return PreloadedChatPayload(
            initialPageSize = visibleMessages.size,
            visibleMessages = visibleMessages,
            renderData = renderData,
            keyedMessages = keyedMessages,
            availabilityByMessageId = availabilityByMessageId
        )
    }

    private suspend fun preloadAllChats() {
        val chatsToPreload = _chats.value
        val totalChats = chatsToPreload.size
        _chatPreloadState.value = ChatPreloadState(
            isRunning = true,
            completedChats = 0,
            totalChats = totalChats,
            currentLogLine = if (totalChats > 0) "Preloading chats for instant open..." else "No chats to preload"
        )
        updateLoadingUiForPreload(_chatPreloadState.value)
        preloadedChats.clear()
        chatsToPreload.forEachIndexed { index, chat ->
            val initialPageSize = if (index < HOT_CHAT_PRELOAD_COUNT) {
                HOT_CHAT_PAGE_SIZE
            } else {
                INITIAL_PAGE_SIZE
            }
            val chatName = if (chat.isGroup) {
                chat.subject.ifEmpty { "Group" }
            } else {
                getContactName(chat.jid).ifEmpty { chat.jid.substringBefore("@") }
            }
            val nextState = ChatPreloadState(
                isRunning = true,
                completedChats = index,
                totalChats = totalChats,
                currentChatName = chatName,
                currentLogLine = "Preloading $chatName (${index + 1}/$totalChats)"
            )
            _chatPreloadState.value = nextState
            updateLoadingUiForPreload(nextState)
            val payload = buildPreloadedChatPayload(chat.id, initialPageSize)
            preloadedChats[chat.id] = payload
            val completedState = nextState.copy(completedChats = index + 1)
            _chatPreloadState.value = completedState
            updateLoadingUiForPreload(completedState)
        }
        _chatPreloadState.value = ChatPreloadState(
            isRunning = false,
            completedChats = totalChats,
            totalChats = totalChats,
            currentLogLine = "All chats are ready."
        )
    }

    private fun isValidCachedFile(file: File?): Boolean =
        file != null && file.exists() && file.length() > 0L

    private fun buildMediaState(messageId: Long): MediaState =
        viewerRepository.mediaCache[messageId]
            ?.takeIf(::isValidCachedFile)
            ?.let { MediaState(file = it, isReady = true) }
            ?: MediaState(failureReason = viewerRepository.getMediaFailureReason(messageId))

    fun getMediaState(messageId: Long): StateFlow<MediaState> {
        mediaStateCache[messageId]?.let { return it.asStateFlow() }
        val createdFlow = MutableStateFlow(buildMediaState(messageId))
        return (mediaStateCache.putIfAbsent(messageId, createdFlow) ?: createdFlow).asStateFlow()
    }

    fun getMediaStateSnapshot(messageId: Long): MediaState =
        mediaStateCache[messageId]?.value ?: buildMediaState(messageId)

    private fun updateMediaState(messageId: Long, nextState: MediaState): Boolean {
        val createdFlow = MutableStateFlow(nextState)
        val flow = mediaStateCache.putIfAbsent(messageId, createdFlow) ?: createdFlow
        var changed = false
        flow.update { current ->
            if (!current.sameAs(nextState)) {
                changed = true
                nextState
            } else {
                current
            }
        }
        return changed || flow === createdFlow
    }

    private suspend fun refreshMediaStatesWindow(messages: List<Message>, reason: String) {
        val mediaIds = messages
            .asSequence()
            .filter { it.messageType in listOf(1, 2, 3, 9, 20) && it.mediaFilePath.isNotEmpty() }
            .map { it.id }
            .distinct()
            .toList()
        if (mediaIds.isEmpty()) return
        val nextStates = withContext(Dispatchers.Default) {
            mediaIds.associateWith(::buildMediaState)
        }
        withContext(Dispatchers.Main.immediate) {
            var changed = false
            mediaIds.forEach { messageId ->
                val nextState = nextStates[messageId] ?: MediaState()
                if (updateMediaState(messageId, nextState)) {
                    changed = true
                }
            }
            if (changed) {
                _mediaStateVersion.update { it + 1 }
            }
        }
    }

    private suspend fun refreshMediaStatesSafely(messages: List<Message>, reason: String) {
        refreshMutex.withLock {
            currentCoroutineContext().ensureActive()
            refreshMediaStatesWindow(messages, reason)
        }
    }

    private suspend fun startRefresh(messages: List<Message>, reason: String, await: Boolean) {
        refreshJob?.cancel()
        val job = viewModelScope.launch {
            refreshMediaStatesSafely(messages, reason)
        }
        refreshJob = job
        if (await) {
            job.join()
        }
    }

    fun getMediaFileForMessage(messageId: Long): File? =
        getMediaState(messageId).value.file

    fun getMediaEntry(messageId: Long): MediaEntry? = viewerRepository.getMediaEntry(messageId)

    fun getMediaFailureReason(messageId: Long): MediaFailureReason? =
        viewerRepository.getMediaFailureReason(messageId)

    // â”€â”€â”€ MESSAGES â€” paged â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun loadMessages(chatId: Long) {
        openedChatId    = chatId
        currentPageSize = INITIAL_PAGE_SIZE
        val all = viewerRepository.getMessagesByChatId(chatId)
        publishCurrentChatState(
            chatId = chatId,
            loadState = _chatLoadState.value,
            messages = if (all.size <= currentPageSize) all else all.subList(all.size - currentPageSize, all.size),
            renderData = _currentChatRenderData.value,
            availabilityByMessageId = _currentChatAvailability.value
        )
    }

    fun loadMoreMessages() {
        // Only allowed after chat is fully Ready â€” never during Loading
        if (openedChatId < 0 || _chatLoadState.value !is ChatLoadState.Ready) return
        viewModelScope.launch {
            val all = viewerRepository.getMessagesByChatId(openedChatId)
            if (_currentMessages.value.size >= all.size) return@launch
            currentPageSize += PAGE_INCREMENT
            val nextMessages = if (all.size <= currentPageSize) all
            else all.subList(all.size - currentPageSize, all.size)
            val nextRenderData = buildChatRenderDataFor(openedChatId, nextMessages)
            val nextAvailability = buildAvailabilityMap(nextMessages)
            publishCurrentChatState(
                chatId = openedChatId,
                loadState = ChatLoadState.Ready,
                messages = nextMessages,
                renderData = nextRenderData,
                availabilityByMessageId = nextAvailability
            )
            startRefresh(nextMessages, reason = "loadMore:$openedChatId", await = false)
        }
    }

    fun clearCurrentChat() {
        refreshJob?.cancel()
        refreshJob = null
        publishCurrentChatState(
            chatId = null,
            loadState = ChatLoadState.Idle,
            messages = emptyList(),
            renderData = null,
            availabilityByMessageId = emptyMap()
        )
        currentChatKeyMap = emptyMap()
        openedChatId = -1L
    }

    // BUG 14: Look up a message by its keyId â€” O(1), used for reply-preview sender name
    fun getMessageByKeyId(keyId: String): Message? = currentChatKeyMap[keyId]

    // BUG 14: Ensure all messages in current chat are loaded into _currentMessages.
    // Called before animating to a reply that may be outside the current page.
    fun ensureAllMessagesLoaded() {
        if (openedChatId < 0) return
        viewModelScope.launch {
            val all = viewerRepository.getMessagesByChatId(openedChatId)
            if (_currentMessages.value.size < all.size) {
                currentPageSize = all.size
                val nextRenderData = buildChatRenderDataFor(openedChatId, all)
                val nextAvailability = buildAvailabilityMap(all)
                publishCurrentChatState(
                    chatId = openedChatId,
                    loadState = ChatLoadState.Ready,
                    messages = all,
                    renderData = nextRenderData,
                    availabilityByMessageId = nextAvailability
                )
            }
        }
    }

    // â”€â”€â”€ AVATAR â€” instant, cache only, no IO, no suspend â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getAvatar(jid: String): File? {
        val trimmed = jid.trim()
        if (trimmed.isEmpty()) return null
        val exactKey = trimmed.lowercase()
        val normalizedKey = normalizeContactKey(trimmed)
        val avatarAlias = avatarAliasMap[exactKey] ?: avatarAliasMap[normalizedKey]
        return viewerRepository.getAvatarFile(avatarAlias ?: trimmed)
    }

    // â”€â”€â”€ LOOKUPS â€” O(1) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getContactName(jid: String): String {
        val normalizedJid = jid.trim().lowercase()
        if (normalizedJid == "lid_me" || normalizedJid == "me") return "You"
        val phone = normalizeContactKey(normalizedJid)
        if (phone == "0") return "WhatsApp Business"
        return contactNameMap[normalizedJid] ?: contactNameMap[phone] ?: phone
    }
    fun getChatById(chatId: Long): Chat?     = chatByIdMap[chatId]
    fun getMediaMessages(chatId: Long): List<Message> = viewerRepository.getMediaMessages(chatId)

    fun prefetchAround(index: Int) {
        val messages = _currentMessages.value
        if (messages.isEmpty()) return
        val start = (index - 10).coerceAtLeast(0)
        val end = (index + 20).coerceAtMost(messages.lastIndex)
        val prefetchWindow = messages.subList(start, end + 1)
        viewModelScope.launch(Dispatchers.Default) {
            for (message in prefetchWindow) {
                val avatarJid = when {
                    message.fromMe == 1 -> "me"
                    message.senderJid.isNotEmpty() -> message.senderJid
                    else -> null
                }
                avatarJid?.let(::getAvatar)
            }
        }
    }

    // BUG 1: last message "You:" prefix
    fun isLastMessageFromMe(chatId: Long): Boolean =
        viewerRepository.getMessagesByChatId(chatId).lastOrNull()?.fromMe == 1

    // BUG 5: group participants
    fun getGroupForChat(chatId: Long): Group? =
        viewerRepository.currentWaViewFile?.groups?.firstOrNull { it.chatId == chatId }

    // â”€â”€â”€ IN-CHAT SEARCH â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun activateInChatSearch() { _inChatSearchActive.value = true }

    fun deactivateInChatSearch() {
        _inChatSearchActive.value = false
        _inChatSearchQuery.value  = ""
        _inChatMatchIds.value     = emptyList()
        _inChatMatchIndex.value   = 0
    }

    fun updateInChatSearchQuery(query: String) {
        _inChatSearchQuery.value = query
        if (query.isBlank()) {
            _inChatMatchIds.value   = emptyList()
            _inChatMatchIndex.value = 0
        } else {
            val lower = query.lowercase()
            val matches = _currentMessages.value.filter { msg ->
                msg.textData.lowercase().contains(lower) ||
                msg.mediaCaption.lowercase().contains(lower) ||
                msg.mediaName.lowercase().contains(lower) ||
                msg.senderName.lowercase().contains(lower)
            }.map { it.id }
            _inChatMatchIds.value   = matches
            _inChatMatchIndex.value = if (matches.isNotEmpty()) 0 else -1
        }
        val chatId = openedChatId
        if (chatId >= 0 && _currentMessages.value.isNotEmpty()) {
            viewModelScope.launch {
                publishCurrentChatState(
                    chatId = chatId,
                    loadState = _chatLoadState.value,
                    messages = _currentMessages.value,
                    renderData = buildChatRenderDataFor(chatId, _currentMessages.value),
                    availabilityByMessageId = _currentChatAvailability.value
                )
            }
        }
    }

    fun navigateInChatSearch(forward: Boolean) {
        val matches = _inChatMatchIds.value
        if (matches.isEmpty()) return
        val cur = _inChatMatchIndex.value
        _inChatMatchIndex.value = if (forward) (cur + 1) % matches.size
                                  else (cur - 1 + matches.size) % matches.size
    }

    fun currentInChatMatchId(): Long? {
        val matches = _inChatMatchIds.value
        val index   = _inChatMatchIndex.value
        return if (matches.isNotEmpty() && index >= 0) matches[index] else null
    }

    // â”€â”€â”€ SEARCH / FILTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setChatFilter(filter: Int)    { _chatFilter.value = filter }

    // â”€â”€â”€ CLEAR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) { viewerRepository.clear() }
        clearSessionCaches()
        contactNameMap.clear(); chatByIdMap.clear(); avatarAliasMap.clear()
        _loadState.value       = ViewerLoadState.Idle
        _chats.value           = emptyList()
        publishCurrentChatState(
            chatId = null,
            loadState = ChatLoadState.Idle,
            messages = emptyList(),
            renderData = null,
            availabilityByMessageId = emptyMap()
        )
        _isInitialReady.value  = false
    }

    private fun applyPreloadedChatPayload(chatId: Long, payload: PreloadedChatPayload) {
        currentPageSize = payload.initialPageSize.coerceAtLeast(INITIAL_PAGE_SIZE)
        currentChatKeyMap = payload.keyedMessages
        publishCurrentChatState(
            chatId = chatId,
            loadState = ChatLoadState.Ready,
            messages = payload.visibleMessages,
            renderData = payload.renderData,
            availabilityByMessageId = payload.availabilityByMessageId
        )
    }

    private fun publishCurrentChatState(
        chatId: Long?,
        loadState: ChatLoadState,
        messages: List<Message>,
        renderData: ChatRenderData?,
        availabilityByMessageId: Map<Long, ViewerMediaAvailability>
    ) {
        _currentChatId.value = chatId
        _currentMessages.value = messages
        _currentChatRenderData.value = renderData
        _currentChatAvailability.value = availabilityByMessageId
        _chatLoadState.value = loadState
        _currentChatUiState.value = CurrentChatUiState(
            chatId = chatId,
            loadState = loadState,
            messages = messages,
            renderData = renderData,
            availabilityByMessageId = availabilityByMessageId
        )
    }
}
