package app.saathi.data

import android.content.Context
import java.util.UUID

object InstallId {
    private const val PREFS_NAME = "saathi_telemetry"
    private const val KEY_INSTALL_ID = "install_id"

    @Volatile
    private var cachedId: String? = null

    internal fun newUuid(): String = UUID.randomUUID().toString()

    fun get(ctx: Context): String {
        cachedId?.let { return it }
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (existing != null) {
            cachedId = existing
            return existing
        }
        val generated = newUuid()
        prefs.edit().putString(KEY_INSTALL_ID, generated).apply()
        cachedId = generated
        return generated
    }
}
