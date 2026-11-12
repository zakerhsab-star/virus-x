from pathlib import Path

root = Path.cwd()
lt, gt = chr(60), chr(62)
m_flow = f"MutableStateFlow{lt}Map{lt}String, String{gt}{gt}"
s_flow = f"StateFlow{lt}Map{lt}String, String{gt}{gt}"

for rel in [
    "app/src/main/java/com/virusx/app/data/repository/ChatDetailRepository.kt",
    "app/src/main/java/com/virusx/app/ui/chat/ChatViewModel.kt",
]:
    p = root / rel
    if p.exists():
        txt = p.read_text(encoding="utf-8")
        txt = txt.replace("MutableStateFlow>", m_flow)
        txt = txt.replace("StateFlow>", s_flow)
        p.write_text(txt, encoding="utf-8")
        print(f"[+] Patched: {rel}")