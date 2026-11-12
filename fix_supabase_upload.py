from pathlib import Path

root = Path.cwd()
targets = [
    "app/src/main/java/com/virusx/app/data/repository/AuthRepository.kt",
    "app/src/main/java/com/virusx/app/data/repository/StoriesRepository.kt",
    "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt",
]

for rel in targets:
    p = root / rel
    if p.exists():
        txt = p.read_text(encoding="utf-8")
        new_txt = txt.replace(", upsert = false)", ") { upsert = false }")
        if new_txt != txt:
            p.write_text(new_txt, encoding="utf-8")
            print(f"[+] Fixed bucket.upload syntax in: {rel}")