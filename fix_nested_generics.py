from pathlib import Path

root = Path.cwd()

# 1. Fix ChatDetailRepository.kt line 80 (inferred type)
repo = root / "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt"
if repo.exists():
    lines = repo.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if "val typingUsers" in line and "_typingUsers.asStateFlow()" in line:
            lines[i] = "    val typingUsers = _typingUsers.asStateFlow()"
    repo.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("[+] Fixed typingUsers in ChatDetailRepository.kt")

# 2. Fix ChatViewModel.kt line 63 (inferred type)
vm = root / "app/src/main/java/com/virusx/app/ui/chat/ChatViewModel.kt"
if vm.exists():
    lines = vm.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if "val typingUsers" in line and "chatDetailRepository.typingUsers" in line:
            lines[i] = "    val typingUsers = chatDetailRepository.typingUsers"
    vm.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("[+] Fixed typingUsers in ChatViewModel.kt")

# 3. Fix ChatScreen.kt line 848 using org.json.JSONObject without nested generics
screen = root / "app/src/main/java/com/virusx/app/ui/chat/ChatScreen.kt"
if screen.exists():
    lines = screen.read_text(encoding="utf-8").splitlines()
    out = []
    mode = "normal"
    for line in lines:
        if "val reactionsMap = remember(message.reactionsJson)" in line:
            mode = "skip_map"
            out.extend([
                "    val reactionEmojis = remember(message.reactionsJson) {",
                "        val emojis = ArrayList<String>()",
                "        val counts = ArrayList<Int>()",
                "        val mineFlags = ArrayList<Boolean>()",
                "        runCatching {",
                "            val raw = message.reactionsJson",
                "            if (!raw.isNullOrBlank()) {",
                "                val obj = org.json.JSONObject(raw)",
                "                val keys = obj.keys()",
                "                while (keys.hasNext()) {",
                "                    val k = keys.next()",
                "                    val arr = obj.optJSONArray(k)",
                "                    if (arr != null && arr.length() > 0) {",
                "                        emojis.add(k)",
                "                        counts.add(arr.length())",
                "                        val hasMe = (0 until arr.length()).any { idx -> arr.getString(idx) == currentUserId }",
                "                        mineFlags.add(hasMe)",
                "                    }",
                "                }",
                "            }",
                "        }",
                "        Triple(emojis, counts, mineFlags)",
                "    }",
            ])
            continue
        if mode == "skip_map":
            if line.strip() == "}":
                mode = "normal"
            continue

        if "if (reactionsMap.isNotEmpty() && !message.isDeleted)" in line:
            mode = "skip_ui"
            out.extend([
                "        val (rEmojis, rCounts, rMine) = reactionEmojis",
                "        if (rEmojis.isNotEmpty() && !message.isDeleted) {",
                "            FlowRow(",
                "                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),",
                "                horizontalArrangement = Arrangement.spacedBy(4.dp)",
                "            ) {",
                "                rEmojis.indices.forEach { idx ->",
                "                    val emoji = rEmojis[idx]",
                "                    val count = rCounts[idx]",
                "                    val reactedByMe = rMine[idx]",
                "                    Row(",
                "                        modifier = Modifier",
                "                            .clip(CircleShape)",
                "                            .background(if (reactedByMe) VirusXPrimary.copy(alpha = 0.35f) else VirusXSurfaceElevated)",
                "                            .border(1.dp, if (reactedByMe) VirusXSecondaryWhisper else VirusXGlassBorder, CircleShape)",
                "                            .clickable { onToggleReaction(emoji) }",
                "                            .padding(horizontal = 7.dp, vertical = 2.dp),",
                "                        verticalAlignment = Alignment.CenterVertically",
                "                    ) {",
                "                        Text(emoji, fontSize = 12.sp)",
                "                        if (count > 1) {",
                "                            Spacer(modifier = Modifier.width(3.dp))",
                "                            Text(\"$count\", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)",
                "                        }",
                "                    }",
                "                }",
                "            }",
                "        }",
            ])
            continue
        if mode == "skip_ui":
            if line == "        }":
                mode = "normal"
            continue

        out.append(line)

    screen.write_text("\n".join(out) + "\n", encoding="utf-8")
    print("[+] Fixed reactions UI and JSON parser in ChatScreen.kt")

# 4. Fix MessageDto.kt if nested Map generic was stripped
dto = root / "app/src/main/java/com/virusx/app/data/remote/dto/MessageDto.kt"
if dto.exists():
    d_txt = dto.read_text(encoding="utf-8")
    full_map_type = "Map<" + "String, List<" + "String>>"
    if "val reactions:" in d_txt and "Map>" in d_txt:
        d_txt = d_txt.replace("Map>", full_map_type)
        dto.write_text(d_txt, encoding="utf-8")
        print("[+] Fixed reactions field type in MessageDto.kt")