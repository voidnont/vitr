package com.bloodvitr.vitr.ui

enum class VitrTab { Home, Search, Save, Library, Settings }

fun primaryNavigationTabs(): List<VitrTab> = listOf(
    VitrTab.Home,
    VitrTab.Library,
    VitrTab.Settings,
    VitrTab.Search
)

fun primaryNavigationLabel(tab: VitrTab): String? = when (tab) {
    VitrTab.Home -> "Home"
    VitrTab.Library -> "Library"
    VitrTab.Settings -> "Settings"
    VitrTab.Search -> "Search"
    VitrTab.Save -> null
}
