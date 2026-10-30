package com.example.dialoguenotes

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.AudioManager
import android.widget.EditText
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.DataOutputStream
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val utterances = mutableListOf<String>()
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var cloudSummary: String = ""
    private var isUploading = false
    private var backendUrl = ""
    private var clientToken = ""
    private var startMs = 0L
    private var recording = false
    private var partial = ""
    private var restartQueued = false
    private var lastFinal: String? = null
    private var restartFailures = 0
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var summaryText: TextView
    private val prefs by lazy { getSharedPreferences("nigga_tekst_conversations_v1", MODE_PRIVATE) }
    private var conversationId: String = UUID.randomUUID().toString()
    private var conversationTitle: String = ""
    private var createdAt: Long = System.currentTimeMillis()
    private var updatedAt: Long = createdAt
    private var hasSavedContent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            restoreCurrentConversation()
            buildUi()
            render()
            startButton.setOnClickListener { if (recording) finishRecording() else requestAndStart() }
        } catch (e: Exception) {
            android.util.Log.e("NiggaTekst", "Main screen initialization failed", e)
            val message = TextView(this).apply {
                text = "Ошибка запуска экрана. Перезапустите приложение.\n\n${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(32, 48, 32, 32)
                setBackgroundColor(android.graphics.Color.rgb(11, 11, 12))
            }
            setContentView(message)
        }
    }

    private fun buildUi() {
        val bg = android.graphics.Color.rgb(11, 11, 12)
        val panel = android.graphics.Color.rgb(20, 20, 20)
        val gold = android.graphics.Color.rgb(197, 164, 93)
        val ivory = android.graphics.Color.rgb(244, 240, 231)
        val muted = android.graphics.Color.rgb(155, 151, 142)
        val line = android.graphics.Color.rgb(58, 50, 36)
        fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
        fun shape(color: Int, radius: Int = 18, stroke: Int = 0) = android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun space(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
        fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)); background = shape(panel, 22, line) }

        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(28)); setBackgroundColor(bg) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        brand.addView(label("≋", 34f, bg, true).apply { gravity = android.view.Gravity.CENTER; background = shape(gold, 16) }, LinearLayout.LayoutParams(dp(54),dp(54)))
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,0,0,0) }
        brandText.addView(label("Nigga Tekst", 23f, android.graphics.Color.rgb(229,209,160), true))
        brandText.addView(label("INTELLIGENT VOICE NOTES", 9f, muted, true))
        brand.addView(brandText, LinearLayout.LayoutParams(0,-2,1f))
        brand.addView(label("☰", 24f, gold).apply {
            gravity = android.view.Gravity.CENTER
            contentDescription = "История разговоров"
            background = shape(android.graphics.Color.rgb(28,25,19), 24, line)
            setOnClickListener { showHistory() }
        }, LinearLayout.LayoutParams(dp(44),dp(44)))
        root.addView(brand)
        val historyButton = Button(this).apply {
            text = "ИСТОРИЯ РАЗГОВОРОВ"; isAllCaps = false
            setTextColor(gold); background = shape(panel, 12, line)
            setOnClickListener { showHistory() }
        }
        space(root, 12)
        root.addView(historyButton, LinearLayout.LayoutParams(-1, dp(45)))

        space(root,28)
        root.addView(label("ВАШЕ ПРОСТРАНСТВО",11f,gold,true))
        space(root,8)
        root.addView(label("Важное остаётся.",29f,ivory,true))
        root.addView(label("Лишнее исчезает.",29f,gold,true))
        space(root,8)
        root.addView(label("Каждый разговор — в понятный конспект.",14f,muted))
        space(root,22)

        val record = card().apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        record.addView(label("●", 38f, gold).apply { gravity = android.view.Gravity.CENTER; background = shape(android.graphics.Color.rgb(40,33,21), 70, gold) }, LinearLayout.LayoutParams(dp(112),dp(112)))
        space(record,14)
        record.addView(label("Запишите разговор",20f,ivory,true))
        space(record,7)
        record.addView(label("Получите расшифровку и структурированный\nчерновик конспекта",13f,muted).apply { gravity = android.view.Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER })
        space(record,14)
        statusText = label("● ЗАПИСЬ ОСТАНОВЛЕНА",11f,gold,true).apply { gravity = android.view.Gravity.CENTER }
        record.addView(statusText)
        space(record,14)
        startButton = Button(this).apply { text = "НАЧАТЬ ЗАПИСЬ"; isAllCaps = false; textSize = 13f; setTextColor(bg); background = shape(gold,14) }
        record.addView(startButton, LinearLayout.LayoutParams(-1,dp(52)))
        space(root,22); root.addView(record)

        space(root,26)
        root.addView(label("ПОСЛЕДНИЕ ЗАМЕТКИ",11f,gold,true))
        space(root,12)
        val summaryCard = card()
        summaryCard.addView(label("КОНСПЕКТ",11f,gold,true)); space(summaryCard,10)
        summaryText = label("Конспект появится после первых распознанных фраз.",14f,ivory)
        summaryText.setLineSpacing(4f,1f); summaryCard.addView(summaryText)
        root.addView(summaryCard)
        space(root,12)
        val transcriptCard = card()
        transcriptCard.addView(label("РАСШИФРОВКА РАЗГОВОРА",11f,gold,true)); space(transcriptCard,10)
        transcriptText = label("Пока нет распознанной речи",14f,muted).apply { setLineSpacing(5f,1f); setTextIsSelectable(true) }
        transcriptCard.addView(transcriptText); space(transcriptCard,14)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val share = Button(this).apply { text = "Поделиться"; isAllCaps = false; setTextColor(gold); background = shape(android.graphics.Color.rgb(28,25,19),12,line); setOnClickListener { shareNotes() } }
        val clear = Button(this).apply { text = "Новый разговор"; isAllCaps = false; setTextColor(muted); background = shape(panel,12,line); setOnClickListener { newConversation() } }
        actions.addView(share, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(clear, LinearLayout.LayoutParams(0, dp(48), 1f))
        transcriptCard.addView(actions); root.addView(transcriptCard)
        space(root,18)
        root.addView(label("Запись — только с согласием участников. После остановки аудио отправляется на настроенный сервер.\nСпикеры определяются приблизительно; проверяйте имена и решения.",11f,muted).apply { gravity = android.view.Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER })
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg); addView(root) })
    }

    private fun requestAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else startRecording()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecording()
        else Toast.makeText(this, "Для распознавания нужен доступ к микрофону", Toast.LENGTH_LONG).show()
    }

    private fun showCloudSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 12, 28, 12)
        }
        val url = EditText(this).apply {
            hint = "https://your-server.example"
            setSingleLine(true)
            setText(backendUrl)
        }
        val token = EditText(this).apply {
            hint = "Код доступа к личному серверу"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(url)
        layout.addView(token)
        AlertDialog.Builder(this)
            .setTitle("Облачная обработка")
            .setMessage("Используйте только HTTPS. Токен хранится только до закрытия приложения. Никогда не вводите сюда ключ OpenAI API.")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val v = url.text.toString().trim().trimEnd('/')
                if (!v.startsWith("https://")) {
                    Toast.makeText(this, "Нужен HTTPS адрес", Toast.LENGTH_LONG).show()
                } else {
                    backendUrl = v
                    clientToken = token.text.toString()
                    Toast.makeText(this, "Сервер задан для текущего запуска", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun startRecording() {
        if (backendUrl.isBlank() || clientToken.isBlank()) {
            showCloudSettings()
            Toast.makeText(this, "Сначала укажите сервер и код доступа через ⋯", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Запись разговора")
            .setMessage("Убедитесь, что все участники предупреждены и согласны с записью и отправкой аудио на сервер для обработки.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Начать") { _, _ -> beginAudio() }.show()
    }

    private fun cue(start: Boolean) {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35).apply {
                startTone(if (start) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP2, 110)
                handler.postDelayed({ release() }, 220)
            }
        } catch (_: Exception) { }
    }

    private fun beginAudio() {
        try {
            val f = File(filesDir, "recording_${conversationId}.m4a")
            audioFile = f
            @Suppress("DEPRECATION")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(f.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recording = true
            cue(true)
            startMs = System.currentTimeMillis()
            render()
        } catch (e: Exception) {
            recording = false
            mediaRecorder?.release(); mediaRecorder = null
            Toast.makeText(this, "Не удалось открыть микрофон: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishRecording() {
        if (!recording) return
        recording = false
        cue(false)
        var complete = true
        try { mediaRecorder?.stop() } catch (e: Exception) { complete = false }
        try { mediaRecorder?.release() } catch (_: Exception) { }
        mediaRecorder = null
        render()
        val file = audioFile
        if (!complete || file == null || file.length() < 2048) {
            Toast.makeText(this, "Запись слишком короткая или повреждена", Toast.LENGTH_LONG).show()
            return
        }
        if (System.currentTimeMillis() - startMs > 19 * 60 * 1000) {
            Toast.makeText(this, "Длинная запись: сервер может отклонить файл больше 24 МБ", Toast.LENGTH_LONG).show()
        }
        sendToCloud(file)
    }

    private fun sendToCloud(file: File) {
        if (isUploading) return
        isUploading = true
        render()
        val endpoint = backendUrl
        val token = clientToken
        Thread {
            try {
                if (file.length() > 24 * 1024 * 1024L) error("Запись больше 24 МБ. Разделите её на части.")
                val boundary = "----NiggaTekst${System.currentTimeMillis()}"
                val connection = (URL("$endpoint/v1/analyze").openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connection.connectTimeout = 20000
                    connection.readTimeout = 180000
                    connection.doOutput = true
                    connection.outputStream.use { stream ->
                        val data = DataOutputStream(stream)
                        data.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"meeting.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n")
                        file.inputStream().use { it.copyTo(data) }
                        data.writeBytes("\r\n--$boundary--\r\n")
                        data.flush()
                    }
                    val code = connection.responseCode
                    val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error("Сервер вернул $code: ${body.take(180)}")
                    val result = JSONObject(body)
                    val segments = result.optJSONArray("segments") ?: JSONArray()
                    val lines = mutableListOf<String>()
                    for (i in 0 until segments.length()) {
                        val seg = segments.optJSONObject(i) ?: continue
                        val speaker = seg.optString("speaker", "?")
                        val start = seg.optDouble("start", 0.0).toInt()
                        val mm = start / 60; val ss = start % 60
                        lines.add("Спикер $speaker [%02d:%02d]: %s".format(mm, ss, seg.optString("text")))
                    }
                    val sum = result.optString("summary", "")
                    runOnUiThread {
                        utterances.clear(); utterances.addAll(lines)
                        cloudSummary = sum
                        if (!persistConversation()) Toast.makeText(this, "Ошибка сохранения истории", Toast.LENGTH_LONG).show()
                        isUploading = false
                        render()
                        Toast.makeText(this, "Готово: диалог и конспект сохранены", Toast.LENGTH_LONG).show()
                    }
                    file.delete() // Delete raw audio only after successful processing.
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                runOnUiThread {
                    isUploading = false
                    render()
                    Toast.makeText(this, "Ошибка обработки: ${e.message}. Аудио сохранено локально.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun stopRecording() {
        // Lifecycle stop preserves audio locally; it does not silently upload it.
        if (!recording) return
        recording = false
        try { mediaRecorder?.stop() } catch (_: Exception) { }
        try { mediaRecorder?.release() } catch (_: Exception) { }
        mediaRecorder = null
        render()
    }

    private fun render() {
        if (!::statusText.isInitialized) return
        statusText.text = when { recording -> "● Идёт запись · индикатор микрофона"; isUploading -> "◌ Обработка на сервере..."; else -> "○ Остановлено" }
        startButton.text = if (recording) "Остановить и обработать" else "Начать запись"
        startButton.isEnabled = !isUploading
        transcriptText.text = (utterances + listOfNotNull(partial.takeIf { it.isNotBlank() }?.let { "$it …" }))
            .joinToString("\n\n").ifBlank { "Пока нет распознанной речи" }
        summaryText.text = cloudSummary.ifBlank { NotesSummarizer.create(utterances) }
    }

    private fun shareNotes() {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val body = "Конспект разговора ($date)\n\n${cloudSummary.ifBlank { NotesSummarizer.create(utterances) }}\n\nПОЛНАЯ РАСШИФРОВКА\n${utterances.joinToString("\n")}" 
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Конспект разговора")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, "Сохранить или отправить конспект"))
    }

    // Only confirmed speech is stored: partial recognition is never persisted.
    // Each conversation is saved locally, not sent to a server by this code.
    private fun conversationList(): JSONArray = try {
        JSONArray(prefs.getString("history", "[]") ?: "[]")
    } catch (e: Exception) {
        android.util.Log.e("NiggaTekst", "Could not read history", e)
        JSONArray()
    }

    private fun persistConversation(): Boolean {
        if (utterances.isEmpty()) return true
        return try {
            updatedAt = System.currentTimeMillis()
            val array = conversationList()
            val updated = JSONArray()
            val obj = JSONObject().apply {
                put("id", conversationId)
                put("title", conversationTitle.ifBlank { defaultTitle() })
                put("created", createdAt)
                put("updated", updatedAt)
                put("phrases", JSONArray(utterances))
                put("cloud_summary", cloudSummary)
            }
            updated.put(obj)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("id") != conversationId) updated.put(item)
            }
            val saved = prefs.edit()
                .putString("history", updated.toString())
                .putString("current_id", conversationId)
                .commit()
            hasSavedContent = saved
            if (!saved) android.util.Log.e("NiggaTekst", "Conversation persistence failed")
            saved
        } catch (e: Exception) {
            android.util.Log.e("NiggaTekst", "Could not save conversation", e)
            false
        }
    }

    private fun defaultTitle(): String =
        "Разговор " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(createdAt))

    private fun restoreCurrentConversation() {
        val savedId = prefs.getString("current_id", null) ?: return
        val array = conversationList()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("id") == savedId) {
                loadConversation(obj, false)
                return
            }
        }
    }

    private fun loadConversation(obj: JSONObject, redraw: Boolean = true) {
        conversationId = obj.optString("id", UUID.randomUUID().toString())
        conversationTitle = obj.optString("title", "")
        createdAt = obj.optLong("created", System.currentTimeMillis())
        updatedAt = obj.optLong("updated", createdAt)
        utterances.clear()
        val phrases = obj.optJSONArray("phrases") ?: JSONArray()
        for (j in 0 until phrases.length()) {
            val phrase = phrases.optString(j).trim()
            if (phrase.isNotBlank()) utterances.add(phrase)
        }
        cloudSummary = obj.optString("cloud_summary", "")
        hasSavedContent = utterances.isNotEmpty()
        lastFinal = utterances.lastOrNull()
        partial = ""
        prefs.edit().putString("current_id", conversationId).apply()
        if (redraw) render()
    }

    private fun newConversation() {
        if (recording || isUploading) { Toast.makeText(this, "Сначала завершите запись и обработку", Toast.LENGTH_LONG).show(); return }
        stopRecording()
        if (!persistConversation()) {
            Toast.makeText(this, "Не удалось сохранить разговор. Новый не создан.", Toast.LENGTH_LONG).show()
            return
        }
        conversationId = UUID.randomUUID().toString()
        conversationTitle = ""
        createdAt = System.currentTimeMillis()
        updatedAt = createdAt
        hasSavedContent = false
        utterances.clear()
        cloudSummary = ""
        partial = ""
        lastFinal = null
        prefs.edit().remove("current_id").apply()
        render()
    }

    private fun showHistory() {
        val history = conversationList()
        if (history.length() == 0) {
            AlertDialog.Builder(this).setTitle("История разговоров")
                .setMessage("Пока нет сохранённых разговоров. Распознанные фразы сохраняются автоматически.")
                .setPositiveButton("Понятно", null).show()
            return
        }
        val entries = (0 until history.length()).mapNotNull { history.optJSONObject(it) }
        val labels = entries.map { obj ->
            val count = obj.optJSONArray("phrases")?.length() ?: 0
            "${obj.optString("title", "Разговор")}  ·  $count фраз"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("История разговоров")
            .setItems(labels) { _, which -> showConversationActions(entries[which]) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showConversationActions(obj: JSONObject) {
        val title = obj.optString("title", "Разговор")
        AlertDialog.Builder(this).setTitle(title)
            .setItems(arrayOf("Открыть", "Переименовать", "Удалить")) { _, which ->
                when (which) {
                    0 -> {
                        stopRecording()
                        if (persistConversation()) loadConversation(obj)
                        else Toast.makeText(this, "Не удалось сохранить текущий разговор", Toast.LENGTH_LONG).show()
                    }
                    1 -> renameConversation(obj)
                    2 -> confirmDelete(obj)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun renameConversation(obj: JSONObject) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(obj.optString("title", ""))
            selectAll()
        }
        AlertDialog.Builder(this).setTitle("Название разговора")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    obj.put("title", name)
                    if (obj.optString("id") == conversationId) conversationTitle = name
                    updateStoredItem(obj)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun updateStoredItem(item: JSONObject) {
        val old = conversationList()
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val obj = old.optJSONObject(i) ?: continue
            next.put(if (obj.optString("id") == item.optString("id")) item else obj)
        }
        if (!prefs.edit().putString("history", next.toString()).commit()) {
            Toast.makeText(this, "Не удалось сохранить изменение", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(obj: JSONObject) {
        AlertDialog.Builder(this).setTitle("Удалить разговор?")
            .setMessage("Вся расшифровка этого разговора будет удалена с устройства.")
            .setPositiveButton("Удалить") { _, _ ->
                val old = conversationList()
                val next = JSONArray()
                val id = obj.optString("id")
                for (i in 0 until old.length()) {
                    val item = old.optJSONObject(i) ?: continue
                    if (item.optString("id") != id) next.put(item)
                }
                if (prefs.edit().putString("history", next.toString()).commit()) {
                    if (id == conversationId) {
                        stopRecording()
                        conversationId = UUID.randomUUID().toString()
                        conversationTitle = ""
                        createdAt = System.currentTimeMillis()
                        updatedAt = createdAt
                        utterances.clear()
                        partial = ""
                        lastFinal = null
                        hasSavedContent = false
                        prefs.edit().remove("current_id").apply()
                        render()
                    }
                } else Toast.makeText(this, "Не удалось удалить запись", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    override fun onPause() {
        persistConversation()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // No covert/background microphone capture in this MVP.
        if (recording) stopRecording()
    }
    override fun onDestroy() { stopRecording(); super.onDestroy() }
}
