package com.bloodvitr.vitr.ui

object FavoriteIdPolicy {
    fun snapshot(ids: Set<String>?): Set<String> =
        ids.orEmpty()
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    fun toggle(
        current: Set<String>,
        trackId: String
    ): Set<String> {
        val id = trackId.trim()
        if (id.isEmpty()) return snapshot(current)

        return current
            .toMutableSet()
            .apply {
                if (!add(id)) {
                    remove(id)
                }
            }
            .toSet()
    }

    fun toggled(
        current: Set<String>,
        trackId: String
    ): Set<String> =
        toggle(
            current = current,
            trackId = trackId
        )
}
