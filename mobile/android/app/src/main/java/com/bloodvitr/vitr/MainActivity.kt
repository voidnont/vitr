package com.bloodvitr.vitr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.bloodvitr.vitr.island.VitrAppVisibility
import com.bloodvitr.vitr.island.IslandHubPreferences
import com.bloodvitr.vitr.save.VitrDownloadService
import com.bloodvitr.vitr.ui.VitrApp
import com.bloodvitr.vitr.ui.VitrViewModel
import com.bloodvitr.vitr.ui.acceptSharedText
import com.bloodvitr.vitr.ui.theme.VitrTheme

class MainActivity : ComponentActivity() {
    private val viewModel: VitrViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)

        setContent {
            VitrTheme {
                VitrApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        VitrAppVisibility.isForeground = true
        IslandHubPreferences.refreshFloatingOverlay(this)
        VitrDownloadService.kick(this)
    }

    override fun onPause() {
        VitrAppVisibility.isForeground = false
        IslandHubPreferences.refreshFloatingOverlay(this)
        super.onPause()
    }

    private fun handleShareIntent(candidate: Intent?) {
        if (
            candidate?.action != Intent.ACTION_SEND ||
            candidate.type != "text/plain"
        ) {
            return
        }

        viewModel.acceptSharedText(
            candidate.getStringExtra(Intent.EXTRA_TEXT)
        )
    }
}
