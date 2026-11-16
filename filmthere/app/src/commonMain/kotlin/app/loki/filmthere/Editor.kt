package app.loki.filmthere

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.loki.kit.LaneTimeline
import app.loki.kit.LocalWorkspace
import app.loki.kit.TimelineClip
import app.loki.kit.TimelineLane
import app.loki.kit.TimelineMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun seconds(ticks: Long?) = (ticks ?: 0L) / TICKS_PER_SECOND

/** The sequence's tracks as lanes: video on top, last track first as editors stack them, then audio. */
private fun lanesOf(seq: kotlinx.serialization.json.JsonElement?, selected: Set<Long>): List<TimelineLane> {
    fun lane(t: kotlinx.serialization.json.JsonElement, prefix: String, index: Int, colour: Color) = TimelineLane(
        t.long("id") ?: -1L, prefix + index, t.obj()?.get("items").arr().mapNotNull { c ->
            val id = c.long("clip") ?: return@mapNotNull null
            TimelineClip(id, seconds(c.long("start")), seconds(c.long("end")), c.str("name") ?: "", id in selected, c.bool("enabled") == false)
        }, dim = t.bool("enabled") == false, color = colour,
    )
    val video = seq.obj()?.get("video").arr().mapIndexed { i, t -> lane(t, "V", i + 1, Color(0xFF3E7BD4)) }.reversed()
    val audio = seq.obj()?.get("audio").arr().mapIndexed { i, t -> lane(t, "A", i + 1, Color(0xFF2F9E6B)) }
    return video + audio
}

/**
 * The Program monitor over the timeline. The monitor shows the engine's frame at the playhead (again
 * after every change), the timeline the sequence's tracks: a tap on the ruler moves the playhead, a
 * tap on a clip selects it (with the Razor, cuts it there).
 */
@Composable
fun Editor(native: SurfaceEngine, revision: Int, modifier: Modifier) {
    val ws = LocalWorkspace.current
    val seq = sequenceOf(ws?.state)
    val selected = seq.obj()?.get("selection").arr().mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }.toSet()
    val lanes = lanesOf(seq, selected)
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(0.55f).background(Color.Black)) {
            val wide = maxWidth.value * 1.2f
            // At most the sequence's own size; a monitor wider than that gains nothing.
            val scale = (wide / (seq.obj()?.get("settings").num("width") ?: 1920.0).toFloat()).coerceIn(0.1f, 1f)
            LaunchedEffect(revision, scale) {
                frame = withContext(Dispatchers.Default) { native.renderPage(0, scale)?.let { rgbaToImageBitmap(it.width, it.height, it.rgba) } } ?: frame
            }
            val f = frame
            if (f != null) Image(f, "Program monitor", Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
            else Text("Create or open a project: File › New › Demo Project.", Modifier.align(Alignment.Center).padding(16.dp), color = Color.White.copy(alpha = 0.7f))
        }
        LaneTimeline(
            lanes, total = seconds(seq.long("duration")).coerceAtLeast(10.0), playhead = seconds(seq.long("playhead")),
            modifier = Modifier.fillMaxWidth().weight(0.45f),
            marks = seq.obj()?.get("markers").arr().mapNotNull { m -> m.long("start")?.let { TimelineMark(seconds(it), m.str("name") ?: "") } },
            onSeek = { t -> ws?.run?.invoke("playhead.set", buildJsonObject { put("seconds", t) }) {} },
            onTap = { _, clip, at ->
                val w = ws ?: return@LaneTimeline
                if (clip == null) return@LaneTimeline
                if (w.tool == "Razor") {
                    w.run("timeline.razor", buildJsonObject { put("time", (at * TICKS_PER_SECOND).toLong()); put("clip", clip.id) }) {}
                } else {
                    w.run("timeline.select", buildJsonObject { put("clips", buildJsonArray { add(JsonPrimitive(clip.id)) }) }) {}
                }
            },
        )
    }
}
