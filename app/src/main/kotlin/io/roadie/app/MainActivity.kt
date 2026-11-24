package io.roadie.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.roadie.carui.RoadieApp
import io.roadie.carui.RoadieViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: RoadieViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            RoadieApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val raw = intent?.dataString
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: return
        val videoId = CarCompanionServer.extractYouTubeVideoId(raw) ?: return
        viewModel.queueOrPlayVideoId(videoId, autoPlayIfIdle = true)
    }
}
