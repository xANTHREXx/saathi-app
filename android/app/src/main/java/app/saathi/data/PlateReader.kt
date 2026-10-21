package app.saathi.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.util.Log
import app.saathi.BuildConfig
import app.saathi.core.Plate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Reads a number plate from a photo on the phone (ML Kit, bundled model): no network, the photo never leaves. */
object PlateReader {
    // Enough pixels for a plate a few metres away, without holding a full 50 MP frame in memory.
    private const val MAX_SIDE = 2000

    /** Reads the plate in the photo at [photo], then deletes it whatever happens: the photo is never kept. */
    suspend fun read(photo: File): String? = withContext(Dispatchers.IO) {
        try {
            val (bmp, rotation) = decode(photo) ?: return@withContext null
            read(bmp, rotation)
        } finally {
            photo.delete()
        }
    }

    suspend fun read(bmp: Bitmap, rotationDegrees: Int): String? = suspendCancellableCoroutine { cont ->
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        rec.process(InputImage.fromBitmap(bmp, rotationDegrees))
            .addOnSuccessListener { r ->
                // Plate.find copes with the plate running over two lines: "KA 05 / AB 1234".
                val plate = Plate.find(r.text)
                if (BuildConfig.DEBUG) Log.d("Saathi", "plate OCR: ${r.text.length} chars -> $plate")
                cont.resume(plate)
                rec.close()
            }
            .addOnFailureListener { cont.resume(null); rec.close() }
    }

    /** The photo downsampled to about [MAX_SIDE] on its long side, with the rotation its EXIF asks for. */
    private fun decode(photo: File): Pair<Bitmap, Int>? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        val long = maxOf(bounds.outWidth, bounds.outHeight)
        if (long <= 0) return@runCatching null
        var sample = 1
        while (long / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
        val side = maxOf(bmp.width, bmp.height)
        if (side > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / side
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        }
        val rotation = when (ExifInterface(photo.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        bmp to rotation
    }.getOrNull()
}
