from pathlib import Path
import re

root = Path.cwd()
LT = chr(60)
GT = chr(62)

def g(outer, inner):
    return f"{outer}{LT}{inner}{GT}"

def patch_file_lines(rel_path, rules):
    p = root / rel_path
    if not p.exists():
        return
    lines = p.read_text(encoding="utf-8").splitlines()
    changed = 0
    for i, line in enumerate(lines):
        for predicate, replacement in rules:
            if predicate(line) and line != replacement:
                lines[i] = replacement
                changed += 1
                break
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[+] {rel_path}: updated {changed} line(s)")

# 1. ChatDetailRepository.kt
patch_file_lines("app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt", [
    (lambda l: "private val _typingUsers =" in l,
     f"    private val _typingUsers = {g('MutableStateFlow', g('Map', 'String, String'))}(emptyMap())"),
    (lambda l: "val typingUsers" in l and "_typingUsers.asStateFlow()" in l,
     f"    val typingUsers: {g('StateFlow', g('Map', 'String, String'))} = _typingUsers.asStateFlow()"),
    (lambda l: "fun observeLocalMessages(" in l,
     f"    fun observeLocalMessages(chatId: String): {g('Flow', g('List', 'MessageEntity'))} {{"),
    (lambda l: "suspend fun fetchChatDetailsAndMembers(" in l,
     f"    suspend fun fetchChatDetailsAndMembers(chatId: String): {g('Pair', 'ChatDto?, ' + g('List', 'ProfileDto'))} {{"),
])

# 2. ChatViewModel.kt
patch_file_lines("app/src/main/java/com/virusx/app/ui/chat/ChatViewModel.kt", [
    (lambda l: "private val _messages =" in l,
     f"    private val _messages = {g('MutableStateFlow', g('List', 'MessageEntity'))}(emptyList())"),
    (lambda l: "val messages" in l and "_messages.asStateFlow()" in l,
     f"    val messages: {g('StateFlow', g('List', 'MessageEntity'))} = _messages.asStateFlow()"),
    (lambda l: "val typingUsers" in l and "chatDetailRepository.typingUsers" in l,
     f"    val typingUsers: {g('StateFlow', g('Map', 'String, String'))} = chatDetailRepository.typingUsers"),
    (lambda l: "val liveWaveform" in l and "voiceNoteManager.liveWaveform" in l,
     f"    val liveWaveform: {g('StateFlow', g('List', 'Float'))} = voiceNoteManager.liveWaveform"),
])

# 3. AdminRepository.kt
patch_file_lines("app/src/main/java/com/virusx/app/data/repository/AdminRepository.kt", [
    (lambda l: "suspend fun fetchAllUsers()" in l,
     f"    suspend fun fetchAllUsers(): {g('Result', g('List', 'ProfileDto'))} = runCatching {{"),
    (lambda l: "suspend fun fetchAllChatsForInspector()" in l,
     f"    suspend fun fetchAllChatsForInspector(): {g('Result', g('List', 'AdminChatOverviewDto'))} = runCatching {{"),
    (lambda l: "suspend fun fetchChatMessagesForInspector(" in l,
     f"    suspend fun fetchChatMessagesForInspector(chatId: String): {g('Result', g('List', 'MessageDto'))} = runCatching {{"),
    (lambda l: "suspend fun fetchDeletedMessages()" in l,
     f"    suspend fun fetchDeletedMessages(): {g('Result', g('List', 'MessageDto'))} = runCatching {{"),
    (lambda l: "suspend fun fetchAllAnonymousQuestions()" in l,
     f"    suspend fun fetchAllAnonymousQuestions(): {g('Result', g('List', 'StoryAskQuestionDto'))} = runCatching {{"),
])

# 4. MessageDto.kt
patch_file_lines("app/src/main/java/com/virusx/app/data/remote/dto/MessageDto.kt", [
    (lambda l: '@SerialName("reactions") val reactions:' in l,
     f'    @SerialName("reactions") val reactions: {g("Map", g("String, List", "String"))} = emptyMap(),'),
])

# 5. Global audit across ALL .kt files for any unbalanced '>' after an identifier
broken = []
rx = re.compile(r"[A-Za-z0-9_?]" + re.escape(GT))
for kt in (root / "app/src/main/java").rglob("*.kt"):
    for idx, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
        if rx.search(line) and line.count(LT) < line.count(GT):
            broken.append(f"{kt.name}:{idx}: {line.strip()}")

if broken:
    print("[!] Remaining broken lines:")
    for b in broken:
        print("   ", b)
    raise SystemExit(1)
else:
    print("[✓] FULL AUDIT PASSED: 0 broken generic types in the entire project!")