from pathlib import Path

root = Path.cwd()

# 1. Sanitize raw Ktor HTTP/JWT error messages in SettingsViewModel.kt
svm = root / "app/src/main/java/com/virusx/app/ui/settings/SettingsViewModel.kt"
if svm.exists():
    txt = svm.read_text(encoding="utf-8")
    helper = """    private fun cleanError(err: Throwable, fallback: String): String {
        val raw = err.localizedMessage ?: return fallback
        return raw.substringBefore("URL:").substringBefore("Headers:").trim().ifBlank { fallback }
    }

    fun signOut"""
    if "private fun cleanError" not in txt:
        txt = txt.replace("    fun signOut", helper)
        txt = txt.replace("err.localizedMessage ?: \"تعذر التحقق من كود الإشراف\"", "cleanError(err, \"تعذر التحقق من كود الإشراف\")")
        txt = txt.replace("err.localizedMessage ?: \"فشل تحديث البيانات\"", "cleanError(err, \"فشل تحديث البيانات\")")
        txt = txt.replace("err.localizedMessage ?: \"فشل رفع الصورة الشخصية\"", "cleanError(err, \"فشل رفع الصورة الشخصية\")")
        svm.write_text(txt, encoding="utf-8")
        print("[+] Sanitized error messages in SettingsViewModel.kt")

# 2. Export Home files so we can patch +, Group Creation, and In-App OTA Updater cleanly
dump_lines = []
for rel in [
    "app/src/main/java/com/virusx/app/data/repository/ChatRepository.kt",
    "app/src/main/java/com/virusx/app/ui/home/HomeViewModel.kt",
    "app/src/main/java/com/virusx/app/ui/home/HomeScreen.kt",
]:
    p = root / rel
    if p.exists():
        dump_lines.append(f"=== FILE: {rel} ===")
        dump_lines.append(p.read_text(encoding="utf-8"))

out_file = root / "home_inspect.txt"
out_file.write_text("\n".join(dump_lines), encoding="utf-8")
print(f"[✓] Exported home files to {out_file}")