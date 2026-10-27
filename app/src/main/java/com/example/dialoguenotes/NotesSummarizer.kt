package com.example.dialoguenotes

/** Rule-based draft of meeting notes; no AI or external API is used. */
object NotesSummarizer {
    fun create(utterances: List<String>): String {
        if (utterances.isEmpty()) return "Конспект появится после первых распознанных фраз."
        val lines = utterances.map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
            .distinct()
        val decisions = lines.filter { it.containsAny("решили", "договорились", "утвердили", "приняли решение", "согласовали") }
        val tasks = lines.filter { it.containsAny("нужно", "надо", "сделать", "подготовить", "отправить", "проверить", "до пятницы", "к сроку", "задача") }
        val questions = lines.filter { it.contains('?') || it.containsAny("вопрос", "уточнить", "непонятно") }
        val highlights = lines.filterNot { it in decisions || it in tasks || it in questions }
            .sortedByDescending { score(it) }
            .take(5)
            .sortedBy { lines.indexOf(it) }
        return buildString {
            append("ОСНОВНЫЕ МОМЕНТЫ\n")
            section(highlights.ifEmpty { lines.take(5) })
            if (decisions.isNotEmpty()) { append("\n\nРЕШЕНИЯ\n"); section(decisions.take(8)) }
            if (tasks.isNotEmpty()) { append("\n\nЗАДАЧИ / ДОГОВОРЁННОСТИ\n"); section(tasks.take(8)) }
            if (questions.isNotEmpty()) { append("\n\nВОПРОСЫ\n"); section(questions.take(6)) }
            append("\n\n(Автоматический черновик: проверьте формулировки.)")
        }
    }

    private fun String.containsAny(vararg tokens: String) =
        tokens.any { contains(it, ignoreCase = true) }

    private fun score(line: String): Int {
        val words = line.split(' ').size
        return minOf(words, 24) + if (line.containsAny("важно", "срок", "проект", "план", "бюджет")) 10 else 0
    }

    private fun StringBuilder.section(items: List<String>) {
        items.forEach { append("• ").append(it).append('\n') }
    }
}
