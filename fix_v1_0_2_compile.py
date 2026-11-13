from pathlib import Path

root = Path.cwd()

# 1. Fix nullable smart-cast of message.mediaUrl inside lambdas in ChatScreen.kt
chat_ui = root / "app/src/main/java/com/virusx/app/ui/chat/ChatScreen.kt"
if chat_ui.exists():
    txt = chat_ui.read_text(encoding="utf-8")
    txt = txt.replace(
        'if (message.messageType == "image" && !message.mediaUrl.isNullOrBlank() && !message.isDeleted && canReadWhisper) {\n                            onOpenFullImage(message.mediaUrl)\n                        }',
        'val urlToOpen = message.mediaUrl\n                        if (message.messageType == "image" && !urlToOpen.isNullOrBlank() && !message.isDeleted && canReadWhisper) {\n                            onOpenFullImage(urlToOpen)\n                        }'
    )
    txt = txt.replace(
        ".clickable { onOpenFullImage(message.mediaUrl) }",
        ".clickable { message.mediaUrl?.let(onOpenFullImage) }"
    )
    chat_ui.write_text(txt, encoding="utf-8")
    print("[+] Fixed nullable mediaUrl smart-cast in ChatScreen.kt")

# 2. Replace Icons.Default.Radar with standard Icons.Default.WifiTethering in SuperAdminScreen.kt
sa_ui = root / "app/src/main/java/com/virusx/app/ui/admin/SuperAdminScreen.kt"
if sa_ui.exists():
    sa_txt = sa_ui.read_text(encoding="utf-8")
    sa_txt = sa_txt.replace("Icons.Default.Radar", "Icons.Default.WifiTethering")
    sa_txt = sa_txt.replace("import androidx.compose.material.icons.filled.Radar", "import androidx.compose.material.icons.filled.WifiTethering")
    sa_ui.write_text(sa_txt, encoding="utf-8")
    print("[+] Replaced Radar icon with WifiTethering in SuperAdminScreen.kt")

# 3. Ensure MessageDto.kt and MessageEntity.kt contain reactions, destructSeconds, editedAt
dto_path = root / "app/src/main/java/com/virusx/app/data/remote/dto/MessageDto.kt"
ent_path = root / "app/src/main/java/com/virusx/app/data/local/entity/MessageEntity.kt"
if dto_path.exists() and ent_path.exists():
    dto_txt = dto_path.read_text(encoding="utf-8")
    ent_txt = ent_path.read_text(encoding="utf-8")
    if "destructSeconds" not in dto_txt:
        idx = dto_txt.rfind(")")
        if idx != -1:
            addition = ',\n    @SerialName("reactions") val reactions: Map<String, List<String>> = emptyMap(),\n    @SerialName("destruct_seconds") val destructSeconds: Int? = null,\n    @SerialName("edited_at") val editedAt: String? = null\n'
            dto_txt = dto_txt[:idx].rstrip() + addition + dto_txt[idx:]
            dto_path.write_text(dto_txt, encoding="utf-8")
            print("[+] Patched missing fields into MessageDto.kt")
    if "destructSeconds" not in ent_txt:
        if "import kotlinx.serialization.encodeToString" not in ent_txt:
            ent_txt = ent_txt.replace(
                "package com.virusx.app.data.local.entity",
                "package com.virusx.app.data.local.entity\n\nimport kotlinx.serialization.encodeToString\nimport kotlinx.serialization.json.Json"
            )
        class_end = ent_txt.find(")\n\nfun MessageDto.toEntity")
        if class_end == -1:
            class_end = ent_txt.find(")")
        if class_end != -1:
            ent_fields = ',\n    val reactionsJson: String? = null,\n    val destructSeconds: Int? = null,\n    val editedAt: String? = null\n'
            ent_txt = ent_txt[:class_end].rstrip() + ent_fields + ent_txt[class_end:]
        map_end = ent_txt.rfind(")")
        if map_end != -1:
            map_fields = ',\n    reactionsJson = runCatching { Json.encodeToString(reactions) }.getOrNull(),\n    destructSeconds = destructSeconds,\n    editedAt = editedAt\n'
            ent_txt = ent_txt[:map_end].rstrip() + map_fields + ent_txt[map_end:]
        ent_path.write_text(ent_txt, encoding="utf-8")
        print("[+] Patched missing fields into MessageEntity.kt")

# 4. Ensure statusBarsPadding import exists in StoriesScreen.kt
stories_ui = root / "app/src/main/java/com/virusx/app/ui/stories/StoriesScreen.kt"
if stories_ui.exists():
    st_txt = stories_ui.read_text(encoding="utf-8")
    if "statusBarsPadding()" in st_txt and "import androidx.compose.foundation.layout.statusBarsPadding" not in st_txt:
        st_txt = st_txt.replace(
            "package com.virusx.app.ui.stories",
            "package com.virusx.app.ui.stories\n\nimport androidx.compose.foundation.layout.statusBarsPadding"
        )
        stories_ui.write_text(st_txt, encoding="utf-8")
        print("[+] Fixed statusBarsPadding import in StoriesScreen.kt")