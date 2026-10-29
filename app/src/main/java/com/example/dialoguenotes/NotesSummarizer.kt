package com.example.dialoguenotes

/** Transparent, offline rules; does not fabricate people, deadlines or decisions. */
object NotesSummarizer {
    fun create(utterances: List<String>): String {
        val lines = utterances.map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }.distinct()
        if (lines.isEmpty()) return "Конспект появится после первых распознанных фраз."
        fun contains(text: String, vararg words: String) = words.any { text.contains(it, ignoreCase = true) }
        val decisions = lines.filter { contains(it,"решили", "договорились", "утвердили", "согласовали", "приняли решение") }
        val tasks = lines.filter { contains(it,"нужно", "надо", "подготовить", "сделать", "проверить", "отправить", "поручили", "задача") }
        val dates = lines.filter { contains(it,"срок", "до пятницы", "до понедельника", "завтра", "следующ", "к пятнице", "к понедельнику") || Regex("\\b\\d{1,2}[./-]\\d{1,2}\\b").containsMatchIn(it) }
        val questions = lines.filter { it.contains('?') || contains(it,"вопрос", "уточнить", "непонятно") }
        val main = lines.filterNot { it in decisions || it in tasks || it in questions }.take(6).ifEmpty { lines.take(6) }
        return buildString {
            fun section(title: String, items: List<String>) {
                append(title).append('\n')
                if (items.isEmpty()) append("• Не выделено явно\n")
                else items.distinct().take(10).forEach { append("• ").append(it).append('\n') }
                append('\n')
            }
            section("ГЛАВНОЕ", main)
            section("РЕШЕНИЯ", decisions)
            section("ЗАДАЧИ", tasks)
            section("СРОКИ И ДАТЫ", dates)
            section("ОТКРЫТЫЕ ВОПРОСЫ", questions)
            append("Черновик по распознанной речи. Ответственные и сроки указываются только если прозвучали в разговоре.")
        }
    }
}
