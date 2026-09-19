package com.frxe.music.updates

data class GitHubReleasePayload(
    val version: String,
    val pageUrl: String,
    val notes: String
)

fun parseGitHubReleasePayload(json: String): GitHubReleasePayload {
    fun field(name: String): String {
        val pattern = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        val encoded = pattern.find(json)?.groupValues?.getOrNull(1).orEmpty()
        return encoded
            .replace("\\\\n", "\n")
            .replace("\\\\r", "\r")
            .replace("\\\\t", "\t")
            .replace("\\\\\"", "\"")
            .replace("\\\\\\\\", "\\")
    }

    return GitHubReleasePayload(
        version = field("tag_name").ifBlank { field("name") },
        pageUrl = field("html_url"),
        notes = field("body")
    )
}

fun parseFirstPublishedGitHubRelease(json: String): GitHubReleasePayload? {
    val source = json.trim()
    if (!source.startsWith('[')) return null

    var index = 1
    while (index < source.length) {
        while (index < source.length && (source[index].isWhitespace() || source[index] == ',')) {
            index += 1
        }
        if (index >= source.length || source[index] == ']') break
        if (source[index] != '{') return null

        val start = index
        var depth = 0
        var inString = false
        var escaped = false

        while (index < source.length) {
            val char = source[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) {
                            index += 1
                            break
                        }
                    }
                }
            }
            index += 1
        }

        if (depth != 0) return null
        val releaseJson = source.substring(start, index)
        val isDraft = Regex("\\\"draft\\\"\\s*:\\s*true", RegexOption.IGNORE_CASE)
            .containsMatchIn(releaseJson)

        if (!isDraft) {
            val release = parseGitHubReleasePayload(releaseJson)
            if (release.version.isNotBlank()) return release
        }
    }

    return null
}

fun parseMavenMetadata(xml: String): String? {
    val release = Regex("<release>\\s*([^<]+?)\\s*</release>", RegexOption.IGNORE_CASE)
        .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (release.isNotEmpty()) return release

    val latest = Regex("<latest>\\s*([^<]+?)\\s*</latest>", RegexOption.IGNORE_CASE)
        .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (latest.isNotEmpty()) return latest

    return Regex("<version>\\s*([^<]+?)\\s*</version>", RegexOption.IGNORE_CASE)
        .findAll(xml)
        .map { it.groupValues[1].trim() }
        .filter(String::isNotBlank)
        .lastOrNull()
}
