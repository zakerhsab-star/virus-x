from pathlib import Path

root = Path.cwd()

# 1. Fix StoriesScreen.kt squished button & status bar padding
stories_ui = root / "app/src/main/java/com/virusx/app/ui/stories/StoriesScreen.kt"
if stories_ui.exists():
    txt = stories_ui.read_text(encoding="utf-8")
    txt = txt.replace(
        'Text("الحالات التفاعلية (24 ساعة)", style = MaterialTheme.typography.titleLarge, color = TextPrimary)',
        'Text("الحالات التفاعلية (24 ساعة)", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))'
    )
    txt = txt.replace(
        'Row(verticalAlignment = Alignment.CenterVertically) {\n                    IconButton(onClick = onBack) {\n                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)\n                    }\n                    Text("الحالات التفاعلية (24 ساعة)", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))\n                }',
        'Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {\n                    IconButton(onClick = onBack) {\n                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)\n                    }\n                    Text("الحالات التفاعلية", style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1)\n                }'
    )
    txt = txt.replace(
        'Text("حالة جديدة")',
        'Text("حالة جديدة", maxLines = 1, softWrap = false)'
    )
    stories_ui.write_text(txt, encoding="utf-8")
    print("[+] Fixed StoriesScreen.kt top bar & button layout")

# 2. Export Chat files for UI overhaul & Group Member Addition
dump = []
for rel in [
    "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt",
    "app/src/main/java/com/virusx/app/ui/chat/ChatViewModel.kt",
    "app/src/main/java/com/virusx/app/ui/chat/ChatScreen.kt",
]:
    p = root / rel
    if p.exists():
        dump.append(f"=== FILE: {rel} ===")
        dump.append(p.read_text(encoding="utf-8"))

out = root / "chat_inspect.txt"
out.write_text("\n".join(dump), encoding="utf-8")
print(f"[✓] Exported chat files to {out}")