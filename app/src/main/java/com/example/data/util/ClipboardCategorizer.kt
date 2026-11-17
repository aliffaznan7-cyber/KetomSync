package com.example.data.util

import com.example.data.model.ClipboardCategory

object ClipboardCategorizer {
    private val urlRegex = Regex("""(?i)\b((https?://|www\.)[^\s/$.?#].[^\s]*)""")
    private val codeKeywordsRegex = Regex(
        """(\b(fun|val|var|class|interface|object|import|package|return|const|let|async|await|def|SELECT|INSERT|UPDATE|DELETE|CREATE|gradle|adb)\b|=>|::|\{\s*\n|\};\s*$)""",
        RegexOption.MULTILINE
    )
    private val addressRegex = Regex(
        """(?i)(\b\d{1,5}\s+[A-Za-z0-9.\s]{2,35}\s+(St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Ln|Lane|Dr|Drive|Way|Ct|Court|Pl|Place)\b|\b(Jalan|Jln|Lorong|Taman|Persiaran)\s+[A-Za-z0-9.\s]{2,}|\b\d{5}(-\d{4})?\b)"""
    )
    private val variableRegex = Regex("""\{([a-zA-Z0-9_ ]{1,32})\}""")

    fun categorize(rawText: String): ClipboardCategory {
        val text = rawText.trim()
        if (text.isEmpty()) return ClipboardCategory.TEXT

        if (urlRegex.containsMatchIn(text) && !text.contains('\n')) {
            return ClipboardCategory.LINK
        }

        val hasCodeBraces = (text.contains('{') && text.contains('}')) ||
            (text.contains("```")) ||
            text.lines().count { it.trimEnd().endsWith(";") } >= 1
        if (hasCodeBraces || codeKeywordsRegex.containsMatchIn(text)) {
            return ClipboardCategory.CODE
        }

        if (addressRegex.containsMatchIn(text)) {
            return ClipboardCategory.ADDRESS
        }

        return ClipboardCategory.TEXT
    }

    fun extractTemplateVariables(template: String): List<String> {
        return variableRegex.findAll(template)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun expandTemplate(template: String, values: Map<String, String>): String {
        return variableRegex.replace(template) { match ->
            val key = match.groupValues[1].trim()
            values[key]?.takeIf { it.isNotBlank() } ?: match.value
        }
    }
}
