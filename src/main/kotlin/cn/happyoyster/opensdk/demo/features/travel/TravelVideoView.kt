package cn.happyoyster.opensdk.demo.features.travel

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.happyoyster.opensdk.demo.ui.DemoCard

@Composable
internal fun TravelVideoView(videoView: SurfaceView, aspectRatio: String?) {
    val ratio = when (aspectRatio) {
        "9:16" -> 9f / 16f
        "16:9" -> 16f / 9f
        else -> 16f / 9f
    }
    val maxStageHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxStageHeight),
    ) {
        DemoCard(
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = {
                        videoView.apply {
                            (parent as? ViewGroup)?.removeView(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
