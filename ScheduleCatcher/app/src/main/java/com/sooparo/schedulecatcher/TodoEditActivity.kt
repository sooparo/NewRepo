package com.sooparo.schedulecatcher

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

/** 위젯의 [+] 또는 할 일 화면에서 할 일을 직접 추가하는 대화상자 */
class TodoEditActivity : AppCompatActivity() {

    private var due: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = "할 일 (예: IMRT QA 결과 제출)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("할 일 추가")
            .setView(container)
            .setPositiveButton("추가", null)
            .setNeutralButton("마감일", null)
            .setNegativeButton("취소") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { button ->
                val d = due ?: LocalDate.now()
                DatePickerDialog(this, { _, y, m, day ->
                    due = LocalDate.of(y, m + 1, day)
                    (button as android.widget.Button).text = "마감 ${Formats.date(due!!)}"
                }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = input.text.toString().trim()
                if (title.isEmpty()) {
                    input.error = "내용을 입력하세요"
                    return@setOnClickListener
                }
                TodoStore.add(this, title, due, null, source = "직접 입력")
                Toast.makeText(this, "✅ 할 일을 추가했어요.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                finish()
            }
        }
        dialog.show()
    }
}
