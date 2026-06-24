package com.robsartin.contactotomy.core.rules

/** Shell-style glob matching: `*` = any run, `?` = one char; everything else literal. */
internal object Glob {
    fun matches(
        glob: String,
        value: String,
    ): Boolean = toRegex(glob).matches(value)

    private fun toRegex(glob: String): Regex {
        val sb = StringBuilder()
        for (ch in glob) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}
