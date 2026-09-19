package com.bloodvitr.vitr.ui

internal object LikedTrackPersistencePolicy {
    fun encode(ids: Set<String>): String =
        ids.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .sorted()
            .joinToString("\n")

    fun decode(raw: String?): Set<String> =
        raw.orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
}
