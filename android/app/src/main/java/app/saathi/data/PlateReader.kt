package app.saathi.data

import android.graphics.Bitmap
import app.saathi.core.Plate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Reads a number plate from a photo on the phone (ML Kit, bundled model): no network, the photo never leaves. */
object PlateReader {
    suspend fun read(bmp: Bitmap): String? = suspendCancellableCoroutine { cont ->
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        rec.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { r ->
                // Auto plates often run over two lines: "KA 05 / AB 1234".
                cont.resume(Plate.find(r.text.replace('\n', ' ')) ?: Plate.find(r.text.replace("\n", "")))
                rec.close()
            }
            .addOnFailureListener { cont.resume(null); rec.close() }
    }
}
