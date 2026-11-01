package com.sooparo.schedulecatcher

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 복사해 둔 메시지로 일정 확인 화면을 연다 (빠른 설정 타일 · 위젯 📋 · 앱 아이콘 길게 누르기).
 * 안드로이드는 화면에 떠 있는 앱만 클립보드를 읽을 수 있어서, 투명한 화면을 잠깐 띄운 뒤 읽는다.
 */
class PasteActivity : AppCompatActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (text.isEmpty()) {
            Toast.makeText(this, "복사된 메시지가 없어요. 메시지를 길게 눌러 '복사'한 뒤 다시 눌러 주세요.", Toast.LENGTH_LONG).show()
        } else {
            startActivity(
                Intent(this, ConfirmActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(ConfirmActivity.EXTRA_SOURCE_LABEL, "붙여넣기"),
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
