package com.sooparo.schedulecatcher

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sooparo.schedulecatcher.core.Recurrence
import com.sooparo.schedulecatcher.databinding.ActivityTasksBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 할 일 체크리스트 + 저장 기록(취소) 화면 */
class TasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTasksBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "할 일 · 저장 기록"

        binding.btnAddTodo.setOnClickListener { startActivity(Intent(this, TodoEditActivity::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        TodoStore.purgeOld(this)
        renderTodos()
        renderHistory()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun renderTodos() {
        val list = TodoStore.sorted(TodoStore.all(this))
        binding.todoList.removeAllViews()
        binding.todoEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        list.forEach { todo ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val check = MaterialCheckBox(this).apply {
                isChecked = todo.done
                text = buildString {
                    append(todo.title)
                    todo.dueDate?.let { d ->
                        append("  · ").append(Formats.shortDue(d))
                        todo.dueTime?.let { append(' ').append(Formats.time(it)) }
                    }
                }
                paintFlags = if (todo.done) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                setOnCheckedChangeListener { _, checked ->
                    TodoStore.setDone(this@TasksActivity, todo.id, checked)
                    render()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val delete = MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                text = "삭제"
                setOnClickListener {
                    TodoStore.remove(this@TasksActivity, todo.id)
                    render()
                }
            }
            row.addView(check)
            row.addView(delete)
            todo.source?.let { src ->
                row.setOnLongClickListener {
                    Toast.makeText(this, "출처: $src", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            binding.todoList.addView(row)
        }
    }

    private fun renderHistory() {
        val list = HistoryStore.all(this)
        binding.historyList.removeAllViews()
        binding.historyEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val savedFmt = DateTimeFormatter.ofPattern("M/d HH:mm")

        list.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val text = TextView(this).apply {
                val whenText = when {
                    !entry.dateKnown -> "마감일 없음"
                    entry.rrule != null -> Recurrence.fromRRule(entry.rrule)?.label().orEmpty() +
                        if (entry.allDay) "" else " " + Formats.time(entry.start.toLocalTime())
                    else -> Formats.shortWhen(entry.start, entry.allDay)
                }
                val where = listOfNotNull(
                    "📅".takeIf { entry.eventUrl != null || entry.openedCalendarApp },
                    "⏰".takeIf { entry.alarmId != null },
                    "✅".takeIf { entry.todoId != null },
                ).joinToString(" ")
                val saved = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.savedAt), ZoneId.systemDefault())
                text = "${entry.title}\n$whenText  $where\n저장: ${savedFmt.format(saved)}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val undo = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                this.text = "취소"
                setOnClickListener { confirmUndo(entry) }
            }
            row.addView(text)
            row.addView(undo)
            binding.historyList.addView(row)
        }
    }

    private fun confirmUndo(entry: HistoryStore.Entry) {
        val parts = listOfNotNull(
            "네이버 캘린더 일정 삭제".takeIf { entry.eventUrl != null },
            "알람 취소".takeIf { entry.alarmId != null },
            "할 일 삭제".takeIf { entry.todoId != null },
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("'${entry.title}' 저장 취소")
            .setMessage(if (parts.isEmpty()) "기록만 지웁니다." else parts.joinToString("\n• ", prefix = "• "))
            .setPositiveButton("취소하기") { _, _ ->
                lifecycleScope.launch {
                    val msg = ScheduleSaver.undo(this@TasksActivity, entry)
                    Toast.makeText(this@TasksActivity, msg, Toast.LENGTH_LONG).show()
                    render()
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }
}
