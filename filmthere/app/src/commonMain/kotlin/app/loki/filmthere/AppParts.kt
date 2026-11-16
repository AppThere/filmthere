package app.loki.filmthere

// This file is the app's own (egui2compose writes it once). FilmCraft is a video editor: the surface is
// the Program monitor (the engine renders the frame at the playhead) over the timeline (Editor.kt,
// drawn from `sequence.inspect`); the dock has the Project and the History.
// Written ONCE by egui2compose for an app with the art workspace and never written again. It says what the generic workspace cannot know: which
// command describes the document, which panels the dock has and what they draw, how a tool is
// picked, and what the status bar says. The defaults below work for any engine; replace them.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.loki.kit.DockPanel
import app.loki.kit.EngineClient
import app.loki.kit.RailTool
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronsLeft
import com.composables.icons.lucide.ChevronsRight
import com.composables.icons.lucide.FolderTree
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Trash2
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Ticks in a second, the unit of every time the engine reports. */
const val TICKS_PER_SECOND = 254_016_000_000.0

/** Read after every change: the project (bins and items), the active sequence, the editor's state. */
val STATE_COMMANDS: List<String> = listOf("project.inspect", "sequence.inspect", "state.inspect")

internal fun JsonElement?.obj() = this as? JsonObject
internal fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
internal fun JsonElement?.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonElement?.num(key: String): Double? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.doubleOrNull
internal fun JsonElement?.long(key: String): Long? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull
internal fun JsonElement?.bool(key: String): Boolean? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull

fun docOf(state: JsonElement?): JsonElement? = (state as? JsonObject)?.get("project.inspect")
internal fun sequenceOf(state: JsonElement?): JsonElement? = (state as? JsonObject)?.get("sequence.inspect")

val PANEL_COMMANDS: Set<String> = setOf(
    "file.open", "file.save", "file.saveAs", "file.import", "file.exportMedia", "file.newSequence", "playhead.start", "playhead.end", "playhead.stepBack", "playhead.stepForward",
    "playhead.set", "timeline.select", "timeline.delete", "timeline.razor", "project.select", "source.open", "history.list",
)

val FIRST_PANEL: String? = "project"

val PANEL_GROUPS: List<List<String>> = listOf(listOf("project", "history"))

fun documentTitle(state: JsonElement?): String = docOf(state).str("name") ?: APP_NAME

fun documentDirty(state: JsonElement?): Boolean = docOf(state).bool("dirty") == true

/** Ticks as m:ss:ff at the sequence's frame rate (a timecode, without drop-frame). */
internal fun timecode(seq: JsonElement?): String {
    val frame = seq.long("playhead")?.let { it / TICKS_PER_SECOND } ?: (seq.num("playhead")?.let { it / TICKS_PER_SECOND }) ?: 0.0
    val rate = seq.obj()?.get("settings").num("frame_rate")?.takeIf { it > 0 } ?: seq.obj()?.get("settings").num("fps")?.takeIf { it > 0 } ?: 24.0
    val whole = frame.toLong().coerceAtLeast(0)
    val ff = ((frame - whole) * rate).toInt().coerceAtLeast(0)
    return "%d:%02d:%02d".format(whole / 60, whole % 60, ff)
}

fun statusSegments(state: JsonElement?): List<String> {
    val seq = sequenceOf(state)
    val s = seq.obj()?.get("settings")
    return listOfNotNull(
        seq.str("name"),
        s?.let { "${it.long("width")} × ${it.long("height")}" }?.takeIf { s.long("width") != null },
        seq.obj()?.get("selection").arr().size.takeIf { it > 0 }?.let { "$it selected" },
    )
}

/** The editing tools that act on a tap in the timeline. */
fun loadTools(engine: EngineClient): List<RailTool> =
    listOf("Selection", "Razor", "Hand").mapNotNull { id -> TOOLS.firstOrNull { it.id == id } }.map { RailTool(it.id, it.title, icon = toolIcon(it.id)) }

/** The tools are the front end's own here: a tap in the timeline reads the tool in hand (Editor.kt). */
fun toolCommand(id: String): Pair<String, JsonObject>? = null

fun toolInHand(state: JsonElement?): String? = null

private fun exec(run: (String, JsonObject) -> Unit, command: String, params: JsonObject = JsonObject(emptyMap())) =
    run(command, params)

/** The transport: where the playhead is, and the cut and delete that act at it. */
@Composable
fun OptionsBar(tool: String, tools: List<RailTool>, state: JsonElement?, run: (String, JsonObject) -> Unit) {
    val seq = sequenceOf(state)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ exec(run, "playhead.start") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronsLeft, "To the start", Modifier.size(18.dp)) }
        IconButton({ exec(run, "playhead.stepBack") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronLeft, "Back a frame", Modifier.size(18.dp)) }
        Text(timecode(seq), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton({ exec(run, "playhead.stepForward") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronRight, "Forward a frame", Modifier.size(18.dp)) }
        IconButton({ exec(run, "playhead.end") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronsRight, "To the end", Modifier.size(18.dp)) }
        IconButton({ exec(run, "timeline.razor", buildJsonObject { put("time", seq.long("playhead") ?: 0L) }) }, Modifier.size(36.dp)) { Icon(Lucide.Scissors, "Cut at the playhead", Modifier.size(18.dp)) }
        IconButton({ exec(run, "timeline.delete") }, Modifier.size(36.dp)) { Icon(Lucide.Trash2, "Delete the selection", Modifier.size(18.dp)) }
    }
}

@Composable
fun ToolFooter(state: JsonElement?, run: (String, JsonObject) -> Unit) {}

// Open, save, import and export are the desktop's file dialogs; the engine takes paths.
val APP_ACTIONS: List<app.loki.kit.RibbonItem> = listOf(
    app.loki.kit.RibbonItem("app.open", "Open Project…", ""),
    app.loki.kit.RibbonItem("app.import", "Import Media…", ""),
    app.loki.kit.RibbonItem("app.save", "Save", ""),
    app.loki.kit.RibbonItem("app.saveAs", "Save As…", ""),
    app.loki.kit.RibbonItem("app.exportVideo", "Export as H.264…", ""),
)
val APP_ACTION_IDS: Set<String> = APP_ACTIONS.map { it.id }.toSet()

fun withAppActions(tabs: List<app.loki.kit.RibbonTab>): List<app.loki.kit.RibbonTab> = app.loki.kit.addToFileMenu(tabs, APP_ACTIONS)

suspend fun appAction(id: String, state: JsonElement?, io: app.loki.kit.FileIo) {
    val d = docOf(state)
    fun cmd(command: String, params: JsonObject) = buildJsonObject { put("command", command); put("params", params) }
    suspend fun saveTo(p: String, command: String, params: JsonObject, then: String) {
        if (io.call("engine.execute", cmd(command, params)) != null) { io.publish(p); io.refresh(); io.status("$then ${p.substringAfterLast('/')}") }
    }
    val name = (d.str("name") ?: "Project").replace('/', '_')
    when (id) {
        "app.open" -> {
            val p = io.pick(false, "") ?: return
            if (io.call("engine.execute", cmd("file.open", buildJsonObject { put("path", p) })) != null) io.refresh()
        }
        "app.import" -> {
            val p = io.pick(false, "") ?: return
            val r = io.call("engine.execute", cmd("file.import", buildJsonObject { put("paths", buildJsonArray { add(JsonPrimitive(p)) }) }))
            if (r != null) { io.refresh(); io.status("Imported ${p.substringAfterLast('/')}") }
        }
        "app.save" -> d.str("path")?.let { saveTo(it, "file.save", JsonObject(emptyMap()), "Saved") } ?: appAction("app.saveAs", state, io)
        "app.saveAs" -> {
            val p = io.pick(true, "$name.fcp") ?: return
            saveTo(p, "file.saveAs", buildJsonObject { put("path", p) }, "Saved")
        }
        "app.exportVideo" -> {
            val p = io.pick(true, "$name.mp4") ?: return
            saveTo(p, "file.exportMedia", buildJsonObject { put("path", p); put("format", "h264") }, "Exported")
        }
    }
}

fun dockPanels(state: JsonElement?, revision: Int, run: (String, JsonObject) -> Unit): List<DockPanel> = listOf(
    DockPanel("project", "Project", Lucide.FolderTree) { m -> ProjectPanel(state, run, m) },
    DockPanel("history", "History", Lucide.History) { m -> HistoryPanel(state, m) },
)

/** The project's bins and what is in them, flattened with the depth; a tap opens the item in the Source monitor. */
@Composable
private fun ProjectPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val rows = mutableListOf<Triple<Int, JsonElement, Boolean>>()
    fun walk(n: JsonElement?, depth: Int) {
        for (c in n.obj()?.get("children").arr()) {
            val isBin = c.obj()?.containsKey("children") == true
            rows += Triple(depth, c, isBin)
            if (isBin) walk(c, depth + 1)
        }
    }
    walk(docOf(state).obj()?.get("root"), 0)
    if (rows.isEmpty()) { Text("An empty project: File › Import Media…, or File › New › Demo Project.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    LazyColumn(modifier) {
        items(rows) { (depth, n, isBin) ->
            val item = n.long("item")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(enabled = item != null) { exec(run, "source.open", buildJsonObject { put("item", item ?: return@clickable) }) }.padding(start = (12 + depth * 16).dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(n.str("name") ?: "", Modifier.weight(1f), maxLines = 1, fontWeight = if (isBin) FontWeight.Bold else FontWeight.Normal)
                n.str("type")?.let { Text(it.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun HistoryPanel(state: JsonElement?, modifier: Modifier) {
    Text("Edit › Undo and Redo step through the project's history.", modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
