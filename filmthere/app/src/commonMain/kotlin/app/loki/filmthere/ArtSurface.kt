package app.loki.filmthere

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The Program monitor over the timeline (Editor.kt): the monitor is the engine's frame at the playhead. */
@Composable
fun ArtSurface(native: SurfaceEngine, revision: Int, modifier: Modifier) = Editor(native, revision, modifier)
