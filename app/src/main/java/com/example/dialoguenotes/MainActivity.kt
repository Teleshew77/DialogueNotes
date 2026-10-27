package com.example.dialoguenotes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val utterances = mutableListOf<String>()
    private var recognizer: SpeechRecognizer? = null
    private var recording = false
    private var partial = ""
    private var restartQueued = false
    private var lastFinal: String? = null
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var summaryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        render()
        startButton.setOnClickListener { if (recording) stopRecording() else requestAndStart() }
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
        }
        val heading = TextView(this).apply { text = "Конспект диалога"; textSize = 26f }
        statusText = TextView(this).apply { textSize = 15f; setPadding(0, 12, 0, 12) }
        startButton = Button(this).apply { text = "Начать запись" }
        val shareButton = Button(this).apply { text = "Поделиться конспектом"; setOnClickListener { shareNotes() } }
        val clearButton = Button(this).apply {
            text = "Новый разговор"
            setOnClickListener { stopRecording(); utterances.clear(); partial = ""; lastFinal = null; render() }
        }
        transcriptText = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
        summaryText = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
        content.addView(heading)
        content.addView(statusText)
        content.addView(startButton)
        content.addView(shareButton)
        content.addView(clearButton)
        content.addView(TextView(this).apply { text = "КОНСПЕКТ"; textSize = 20f; setPadding(0, 20, 0, 8) })
        content.addView(summaryText)
        content.addView(TextView(this).apply { text = "\nРАСШИФРОВКА"; textSize = 20f; setPadding(0, 16, 0, 8) })
        content.addView(transcriptText)
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        setContentView(scroll)
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

    private fun startRecording() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "На устройстве нет службы распознавания речи", Toast.LENGTH_LONG).show()
            return
        }
        recording = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(listener) }
        render()
        listen()
    }

    private fun listen() {
        if (!recording) return
        partial = ""
        render()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { recognizer?.startListening(intent) }
        catch (e: Exception) {
            stopRecording()
            Toast.makeText(this, "Ошибка запуска микрофона: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onPartialResults(results: Bundle?) {
            partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            render()
        }
        override fun onResults(results: Bundle?) {
            val final = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if (final.isNotBlank() && final != lastFinal) {
                utterances.add(final)
                lastFinal = final
            }
            partial = ""
            render()
            scheduleRestart(300)
        }
        override fun onError(error: Int) {
            partial = ""
            if (recording) {
                // Permission, unavailable service, and other non-recoverable errors.
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                    stopRecording()
                    Toast.makeText(this@MainActivity, "Ошибка распознавания: $error", Toast.LENGTH_LONG).show()
                } else scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1100 else 550)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!recording || restartQueued) return
        restartQueued = true
        handler.postDelayed({ restartQueued = false; if (recording) listen() }, delayMs)
    }

    private fun stopRecording() {
        recording = false
        restartQueued = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        partial = ""
        render()
    }

    private fun render() {
        if (!::statusText.isInitialized) return
        statusText.text = if (recording) "● Идёт распознавание. Держите приложение открытым." else "○ Остановлено"
        startButton.text = if (recording) "Остановить" else "Начать запись"
        transcriptText.text = (utterances + listOfNotNull(partial.takeIf { it.isNotBlank() }?.let { "$it …" }))
            .joinToString("\n\n").ifBlank { "Пока нет распознанной речи" }
        summaryText.text = NotesSummarizer.create(utterances)
    }

    private fun shareNotes() {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val body = "Конспект разговора ($date)\n\n${NotesSummarizer.create(utterances)}\n\nПОЛНАЯ РАСШИФРОВКА\n${utterances.joinToString("\n")}" 
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Конспект разговора")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, "Сохранить или отправить конспект"))
    }

    override fun onStop() {
        super.onStop()
        // No covert/background microphone capture in this MVP.
        if (recording) stopRecording()
    }
    override fun onDestroy() { stopRecording(); super.onDestroy() }
}
