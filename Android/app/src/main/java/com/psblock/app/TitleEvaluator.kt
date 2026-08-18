package com.psblock.app

/**
 * Decides whether a browser window/page title should be blocked.
 * Direct port of evaluateTitle() / titleMatches() from the Windows main.js.
 */
object TitleEvaluator {

    private fun titleMatches(kw: String, title: String, lo: String): Boolean {
        // Word-boundary match for short keywords avoids false positives (e.g. "Essex" → "sex").
        if (kw.length <= 4) {
            return Regex("\\b" + Regex.escape(kw) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)
        }
        return lo.contains(kw)
    }

    /** Returns the matched reason if the title should be blocked, otherwise null. */
    fun evaluate(title: String, custom: List<String>): String? {
        if (title.isBlank()) return null
        val lo = title.lowercase()

        // Never block mainstream AI assistants.
        for (w in Blocklist.WHITELIST_AI_KEYWORDS) if (lo.contains(w)) return null

        // User-defined custom sites are treated as strong (single match blocks).
        val strong = Blocklist.STRONG_KEYWORDS + custom.map { it.lowercase() }
        for (kw in strong) if (titleMatches(kw, title, lo)) return kw

        // Ambiguous keywords only block when at least two appear together.
        val weakHits = Blocklist.WEAK_KEYWORDS.filter { titleMatches(it, title, lo) }
        if (weakHits.size >= 2) return weakHits.joinToString(" + ")

        return null
    }
}
