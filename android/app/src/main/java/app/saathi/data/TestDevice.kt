package app.saathi.data

import android.content.Context
import app.saathi.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * A test phone's requests carry `x-saathi-test: 1`, and the backend keeps everything they write out of real
 * numbers and reports. Debug builds are test phones unless the demo panel says otherwise; release builds aren't.
 */
object TestDevice {
    const val HEADER = "x-saathi-test"

    /** What the interceptor reads on every request; set once at start and again when the switch moves. */
    @Volatile private var on = false

    /** The saved choice wins; with none saved, a debug build is a test phone. */
    fun resolve(saved: Boolean?, debugBuild: Boolean): Boolean = saved ?: debugBuild

    /** Only the Saathi backend gets the header, not other hosts a client may reach (the APK download, say). */
    fun applies(requestHost: String, apiHost: String?): Boolean = apiHost != null && requestHost.equals(apiHost, ignoreCase = true)

    fun isTest(ctx: Context): Boolean = resolve(Store.settings(ctx).testDevice, BuildConfig.DEBUG)

    /** Call once at app start. */
    fun init(ctx: Context) { on = isTest(ctx) }

    /** Applies a choice already saved in Settings.testDevice to requests from now on. */
    fun update(value: Boolean) { on = value }

    private val apiHost: String? = runCatching { BuildConfig.API_URL.toHttpUrlOrNull()?.host }.getOrNull()

    /** Add to every OkHttp client that talks to the backend. */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        chain.proceed(if (on && applies(req.url.host, apiHost)) req.newBuilder().header(HEADER, "1").build() else req)
    }
}
