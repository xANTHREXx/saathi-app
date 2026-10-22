package app.saathi.data

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.saathi.BuildConfig
import app.saathi.core.Plate
import app.saathi.core.PlateGuide
import app.saathi.core.Region
import app.saathi.core.TextLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Reads number plates from the live camera, one frame at a time (ML Kit, bundled model): no network, and no frame is
 * kept. Only text inside the guide frame counts (see [PlateGuide]), so another vehicle's plate in view is ignored.
 * [onRead] gets each frame's plate, or null when there was none, on the main thread. The camera holds back the
 * next frame until the one being read is closed, so only one read runs at a time. Call [close] when scanning ends.
 */
class PlateReader(private val onRead: (String?) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var closed = false
    @Volatile private var viewWidth = 0
    @Volatile private var viewHeight = 0

    /** The size in pixels of the view the camera is drawn in, which is what places the guide frame in the picture. */
    fun setView(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(frame: ImageProxy) {
        val image = frame.image
        if (closed || image == null) { frame.close(); return }
        try {
            val rotation = frame.imageInfo.rotationDegrees
            val (upW, upH) = PlateGuide.uprightSize(image.width, image.height, rotation)
            // Until the view has a size the guide has no place in the picture, so nothing is read.
            val region = PlateGuide.readRegion(viewWidth.toFloat(), viewHeight.toFloat(), upW.toFloat(), upH.toFloat())
            if (region == null) { frame.close(); return }
            recognizer.process(InputImage.fromMediaImage(image, rotation))
                .addOnSuccessListener { r ->
                    val lines = r.textBlocks.flatMap { it.lines }.map { line ->
                        TextLine(line.text, line.boundingBox?.let { Region(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) })
                    }
                    // Plate.find copes with the plate running over two lines: "KA 05 / AB 1234".
                    val text = PlateGuide.textInside(lines, region)
                    val plate = Plate.find(text)
                    if (BuildConfig.DEBUG && plate != null) Log.d("Saathi", "plate OCR: ${text.length} chars in the guide -> $plate")
                    if (!closed) onRead(plate)
                }
                .addOnFailureListener { if (!closed) onRead(null) }
                // Last, so the frame stays open for the whole read and the camera sends no other meanwhile.
                .addOnCompleteListener { frame.close() }
        } catch (e: Exception) {
            frame.close()
        }
    }

    fun close() {
        closed = true
        recognizer.close()
    }
}
