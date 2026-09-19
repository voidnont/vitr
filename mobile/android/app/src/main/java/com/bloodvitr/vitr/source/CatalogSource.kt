package com.bloodvitr.vitr.source

import com.bloodvitr.vitr.model.HomeSection
import com.bloodvitr.vitr.model.Track

interface CatalogSource {
    suspend fun home(): List<HomeSection>
    suspend fun search(query: String): List<Track>
    suspend fun track(id: String): Track?
}
