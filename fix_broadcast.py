from pathlib import Path

p = Path("app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt")
txt = p.read_text(encoding="utf-8")

old_call = """            channel.broadcast(
                event = "typing",
                message = TypingEventPayload(
                    chatId = chatId,
                    userId = userId,
                    displayName = displayName,
                    isTyping = isTyping
                )
            )"""

new_call = """            channel.broadcast(
                event = "typing",
                message = buildJsonObject {
                    put("chat_id", chatId)
                    put("user_id", userId)
                    put("display_name", displayName)
                    put("is_typing", isTyping)
                }
            )"""

assert old_call in txt, "Target block not found!"
p.write_text(txt.replace(old_call, new_call), encoding="utf-8")
print("[+] Fixed broadcast JsonObject payload in ChatDetailRepository.kt")