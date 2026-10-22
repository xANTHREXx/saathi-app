package app.saathi.ui

import android.os.SystemClock
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.saathi.R
import app.saathi.core.PlateGuide
import app.saathi.core.PlateLock
import app.saathi.data.PlateReader
import java.util.concurrent.Executors

/**
 * The live camera with a plate-shaped guide. Every frame is read on the phone and thrown away, and only text inside
 * the guide counts; when the same plate reads in two frames close together, [onLocked] is called once with it.
 * [onFrame] hears each frame's time, for counting the scan. The camera follows this screen's lifecycle and is
 * released when the scanner leaves the screen. [onUnavailable] means the camera could not start.
 */
@Composable
fun PlateScanner(
    onFrame: (nowMs: Long) -> Unit,
    onLocked: (plate: String) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val frameSeen by rememberUpdatedState(onFrame)
    val locked by rememberUpdatedState(onLocked)
    val unavailable by rememberUpdatedState(onUnavailable)
    // The camera lives in a TextureView, so the rounded corners and the guide drawn over it both work.
    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    val reader = remember {
        val lock = PlateLock()
        PlateReader { plate ->
            val now = SystemClock.elapsedRealtime()
            frameSeen(now)
            lock.offer(plate, now)?.let { locked(it) }
        }
    }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var bound: Array<UseCase> = emptyArray()
        var gone = false
        providerFuture.addListener({
            if (gone) return@addListener
            try {
                val p = providerFuture.get()
                val ratio = AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                    // A busy reader never queues frames: the camera hands it the newest one when it is free.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, reader)
                provider = p
                bound = arrayOf(preview, analysis)
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                unavailable()
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            gone = true
            reader.close()
            provider?.unbind(*bound)
            executor.shutdown()
        }
    }

    // The torch follows what the camera says, so the button is right after the app was away and the torch went out.
    DisposableEffect(camera) {
        val state = camera?.cameraInfo?.torchState
        val watch = Observer<Int> { torchOn = it == TorchState.ON }
        state?.observe(owner, watch)
        onDispose { state?.removeObserver(watch) }
    }

    Box(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // The reader needs the view's size to know which part of each picture the guide covers.
        AndroidView({ previewView }, Modifier.fillMaxSize().onSizeChanged { reader.setView(it.width, it.height) })
        GuideFrame(Modifier.fillMaxSize())
        Text(
            stringResource(R.string.plate_scan_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f)).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            FilledTonalIconToggleButton(
                checked = torchOn, onCheckedChange = { camera?.cameraControl?.enableTorch(it) },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp),
                colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onBackground,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer, checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) { Icon(if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, stringResource(R.string.plate_torch)) }
        }
    }
}

/** The camera dimmed except for a rounded plate-shaped window in the middle, outlined in indigo. */
@Composable
private fun GuideFrame(modifier: Modifier) {
    val shade = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        // The same frame the reader is limited to, so what she sees inside it is what gets read.
        val f = PlateGuide.frame(size.width, size.height)
        val window = Rect(Offset(f.left, f.top), androidx.compose.ui.geometry.Size(f.width, f.height))
        val hole = Path().apply { addRoundRect(RoundRect(window, CornerRadius(20.dp.toPx()))) }
        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addPath(hole)
        }
        drawPath(dim, shade)
        drawPath(hole, line, style = Stroke(3.dp.toPx()))
    }
}
