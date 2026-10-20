package app.saathi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The demo replay must show a detour: off route first, then the long stop. */
class DemoTrackTest {
    @Serializable private data class Pt(val lat: Double, val lng: Double, val t: Long = 0)
    @Serializable private data class Planned(val to: String, val duration_s: Long, val points: List<Pt>)

    private val json = Json { ignoreUnknownKeys = true }
    private fun asset(name: String) = File("src/main/assets/tracks/$name").readText()

    @Test fun blrAutoGoesOffRouteBeforeTheStop() {
        val track = json.decodeFromString<List<Pt>>(asset("blr-auto.json")).map { Fix(it.lat, it.lng, it.t) }
        val plan = json.decodeFromString<Planned>(asset("blr-auto.route.json"))
        val w = Watcher(Mode.AUTO, emptyList(), durationS = null, startedAt = 0)
        w.plan(plan.points.map { Fix(it.lat, it.lng, 0) }, plan.duration_s, from = 0)
        val kinds = track.mapNotNull { w.add(it) }.map { it.kind }
        assertEquals(listOf(EventKind.OFF_ROUTE, EventKind.LONG_STOP), kinds.take(2))
    }
}
