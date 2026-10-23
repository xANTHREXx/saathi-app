package app.saathi.ui.onboarding

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.saathi.core.ClipMode
import app.saathi.core.clipMode

private val NightIndigoGround = Color(0xFF0D1222)

@Composable
fun LoopingClip(
    @RawRes clip: Int,
    @DrawableRes poster: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animatorScale = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        } catch (_: Exception) {
            1f
        }
    }
    val mode = clipMode(animatorScale)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(NightIndigoGround)
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (mode == ClipMode.POSTER) {
            Image(
                painter = painterResource(poster),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            var firstFrameRendered by remember { mutableStateOf(false) }
            var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            try {
                                if (mediaPlayer?.isPlaying == true) {
                                    mediaPlayer?.pause()
                                }
                            } catch (_: Exception) {}
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            try {
                                if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                                    mediaPlayer?.start()
                                }
                            } catch (_: Exception) {}
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                val s = Surface(surface)
                                try {
                                    val mp = MediaPlayer.create(ctx, clip) ?: return
                                    mp.setSurface(s)
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f)
                                    mp.setOnInfoListener { _, what, _ ->
                                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                            firstFrameRendered = true
                                        }
                                        false
                                    }
                                    mp.start()
                                    mediaPlayer = mp
                                } catch (_: Exception) {}
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {}

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                try {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                } catch (_: Exception) {}
                                mediaPlayer = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                if (!firstFrameRendered) {
                                    firstFrameRendered = true
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (!firstFrameRendered) {
                Image(
                    painter = painterResource(poster),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            DisposableEffect(clip) {
                onDispose {
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                    } catch (_: Exception) {}
                    mediaPlayer = null
                }
            }
        }
    }
}
