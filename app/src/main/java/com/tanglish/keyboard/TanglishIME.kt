package com.tanglish.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class TanglishIME : InputMethodService() {

    private var shift = false
    private var lastOriginal = ""
    private var lastResultLen = 0
    private lateinit var status: TextView
    private val letterKeys = mutableListOf<Button>()
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: String) = GradientDrawable().apply {
        cornerRadius = dp(6).toFloat()
        setColor(Color.parseColor(color))
    }

    private fun makeKey(
        label: String,
        weight: Float = 1f,
        color: String = "#455A64",
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 17f
        background = bg(color)
        setPadding(0, 0, 0, 0)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
        setOnClickListener { onClick() }
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    private fun commit(s: String) { currentInputConnection?.commitText(s, 1) }

    private fun typeChar(c: Char) {
        commit(if (shift) c.uppercaseChar().toString() else c.toString())
        if (shift) { shift = false; refreshLabels() }
    }

    private fun refreshLabels() {
        letterKeys.forEach {
            val t = it.text.toString()
            it.text = if (shift) t.uppercase() else t.lowercase()
        }
    }

    private fun letterRow(chars: String) = chars.map { c ->
        makeKey(c.toString()) { typeChar(c) }.also { letterKeys.add(it) }
    }

    override fun onCreateInputView(): View {
        letterKeys.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#263238"))
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }

        // Top bar: status + Undo + Translate
        status = TextView(this).apply {
            text = "Tanglish type panni Translate press pannunga"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 2f).apply { setMargins(dp(4), 0, dp(4), 0) }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val undo = makeKey("Undo", 1.1f, "#6D4C41") { undo() }
        val translate = makeKey("Translate ✨", 1.9f, "#2E7D32") { translate() }
        root.addView(row(status, undo, translate))

        // Number row
        root.addView(row(*"1234567890".map { c -> makeKey(c.toString()) { commit(c.toString()) } }.toTypedArray()))
        // Letters
        root.addView(row(*letterRow("qwertyuiop").toTypedArray()))
        root.addView(row(*letterRow("asdfghjkl").toTypedArray()))
        val shiftKey = makeKey("⇧", 1.5f, "#37474F") { shift = !shift; refreshLabels() }
        val del = makeKey("⌫", 1.5f, "#37474F") { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        root.addView(row(shiftKey, *letterRow("zxcvbnm").toTypedArray(), del))
        // Bottom row
        root.addView(row(
            makeKey(",", 1f, "#37474F") { commit(",") },
            makeKey("space", 5f, "#546E7A") { commit(" ") },
            makeKey(".", 1f, "#37474F") { commit(".") },
            makeKey("?", 1f, "#37474F") { commit("?") },
            makeKey("⏎", 1.5f, "#37474F") { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        ))
        return root
    }

    private fun translate() {
        val ic = currentInputConnection ?: return
        val apiKey = getSharedPreferences("cfg", MODE_PRIVATE).getString("key", "") ?: ""
        if (apiKey.isBlank()) { status.text = "App open panni API key save pannunga"; return }
        val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        if (before.isBlank()) { status.text = "Munnadi edhavadhu type pannunga"; return }
        status.text = "Translating..."
        thread {
            try {
                val result = callClaude(apiKey, before)
                ui.post {
                    val c = currentInputConnection ?: return@post
                    c.beginBatchEdit()
                    c.deleteSurroundingText(before.length, 0)
                    c.commitText(result, 1)
                    c.endBatchEdit()
                    lastOriginal = before
                    lastResultLen = result.length
                    status.text = "Done ✓"
                }
            } catch (e: Exception) {
                ui.post { status.text = "Error: ${e.message}" }
            }
        }
    }

    private fun undo() {
        val ic = currentInputConnection ?: return
        if (lastOriginal.isEmpty()) return
        ic.deleteSurroundingText(lastResultLen, 0)
        ic.commitText(lastOriginal, 1)
        lastOriginal = ""
        status.text = "Undo ✓"
    }

    private fun callClaude(apiKey: String, text: String): String {
        val body = JSONObject().apply {
            put("model", "claude-haiku-5-5")
            put("max_tokens", 500)
            put(
                "system",
                "You convert Tanglish (Tamil written in English letters) or Tamil text into natural, " +
                    "correct English. Reply with ONLY the English text, no quotes, no explanation. " +
                    "If the text is already English, just fix its grammar."
            )
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
        }
        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream.bufferedReader(Charsets.UTF_8).readText()
        if (code !in 200..299) throw Exception("HTTP $code")
        return JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text").trim()
    }
}
