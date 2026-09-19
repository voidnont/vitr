package com.frxe.music.intake

object UrlIntakeParser {
    private val httpUrl =
        Regex("""(?i)https?://\S+""")

    fun extractFirstHttpUrl(
        text: String?
    ): String? {
        val candidate =
            httpUrl.find(text?.trim().orEmpty())
                ?.value
                ?.trimEnd { character ->
                    character in TRAILING_PUNCTUATION
                }
                .orEmpty()

        return candidate.takeIf(String::isNotBlank)
    }

    private val TRAILING_PUNCTUATION =
        setOf(')', ']', '}', ',', '.', '!', ';', ':')
}
