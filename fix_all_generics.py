from pathlib import Path
import re

root = Path.cwd()
LT = chr(60)
GT = chr(62)

def g(outer, inner):
    return f"{outer}{LT}{inner}{GT}"

# 1. Fix ChatDetailRepository.kt
repo = root / "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt"
if repo.exists():
    lines = repo.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if "private val _typingUsers =" in line:
            lines[i] = f"    private val _typingUsers = {g('MutableStateFlow', g('Map', 'String, String'))}(emptyMap())"
        elif "val typingUsers" in line and "_typingUsers.asStateFlow()" in line:
            lines[i] = f"    val typingUsers: {g('StateFlow', g('Map', 'String, String'))} = _typingUsers.asStateFlow()"
    repo.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("[+] Fixed nested generics in ChatDetailRepository.kt")

# 2. Fix ChatViewModel.kt
vm = root / "app/src/main/java/com/virusx/app/ui/chat/ChatViewModel.kt"
if vm.exists():
    lines = vm.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if "private val _messages =" in line:
            lines[i] = f"    private val _messages = {g('MutableStateFlow', g('List', 'MessageEntity'))}(emptyList())"
        elif "val typingUsers" in line and "chatDetailRepository.typingUsers" in line:
            lines[i] = f"    val typingUsers: {g('StateFlow', g('Map', 'String, String'))} = chatDetailRepository.typingUsers"
        elif "val liveWaveform" in line and "voiceNoteManager.liveWaveform" in line:
            lines[i] = f"    val liveWaveform: {g('StateFlow', g('List', 'Float'))} = voiceNoteManager.liveWaveform"
    vm.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("[+] Fixed nested generics in ChatViewModel.kt")

# 3. Fix AdminRepository.kt
admin_repo = root / "app/src/main/java/com/virusx/app/data/repository/AdminRepository.kt"
if admin_repo.exists():
    lines = admin_repo.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if "suspend fun fetchAllUsers()" in line:
            lines[i] = f"    suspend fun fetchAllUsers(): {g('Result', g('List', 'ProfileDto'))} = runCatching {{"
        elif "suspend fun fetchAllChatsForInspector()" in line:
            lines[i] = f"    suspend fun fetchAllChatsForInspector(): {g('Result', g('List', 'AdminChatOverviewDto'))} = runCatching {{"
        elif "suspend fun fetchChatMessagesForInspector(" in line:
            lines[i] = f"    suspend fun fetchChatMessagesForInspector(chatId: String): {g('Result', g('List', 'MessageDto'))} = runCatching {{"
        elif "suspend fun fetchDeletedMessages()" in line:
            lines[i] = f"    suspend fun fetchDeletedMessages(): {g('Result', g('List', 'MessageDto'))} = runCatching {{"
        elif "suspend fun fetchAllAnonymousQuestions()" in line:
            lines[i] = f"    suspend fun fetchAllAnonymousQuestions(): {g('Result', g('List', 'StoryAskQuestionDto'))} = runCatching {{"
    admin_repo.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("[+] Fixed nested generics in AdminRepository.kt")

# 4. Fix MessageDto.kt
dto = root / "app/src/main/java/com/virusx/app/data/remote/dto/MessageDto.kt"
if dto.exists():
    lines = dto.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if '@SerialName("reactions") val reactions:' in line:
            lines[i] = f'    @SerialName("reactions") val reactions: {g("Map", g("String, List", "String"))} = emptyMap(),'
    dto.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("[+] Fixed nested generics in MessageDto.kt")

# 5. Fix ChatScreen.kt ArrayList types if stripped
screen = root / "app/src/main/java/com/virusx/app/ui/chat/ChatScreen.kt"
if screen.exists():
    s_txt = screen.read_text(encoding="utf-8")
    s_txt = s_txt.replace("ArrayList" + GT + "()", g("ArrayList", "String") + "()")
    s_txt = s_txt.replace("val counts = " + g("ArrayList", "String") + "()", "val counts = " + g("ArrayList", "Int") + "()")
    s_txt = s_txt.replace("val mineFlags = " + g("ArrayList", "String") + "()", "val mineFlags = " + g("ArrayList", "Boolean") + "()")
    screen.write_text(s_txt, encoding="utf-8")
    print("[+] Verified ChatScreen.kt reactions list types")

# 6. Audit all Kotlin files to verify zero broken generics remain
broken = []
pattern = re.compile(r"\b(StateFlow|MutableStateFlow|Result|Map|List|Set|Flow|Pair)" + re.escape(GT))
for kt in (root / "app/src/main/java").rglob("*.kt"):
    for idx, l in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
        if pattern.search(l) and LT not in l:
            broken.append(f"{kt.name}:{idx}: {l.strip()}")

if broken:
    print("[!] Found remaining broken lines:", broken)
else:
    print("[✓] Audit passed: 0 broken generic types across all Kotlin files!")