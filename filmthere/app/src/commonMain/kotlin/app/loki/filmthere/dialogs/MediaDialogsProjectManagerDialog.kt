package app.loki.filmthere.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.loki.kit.SheetDialog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Media dialogs project manager: translated from `panels/media_dialogs.rs` (fn `project_manager`, line 506) by egui2compose.
 *
 * The controls and their order are the desktop dialog's. NOT translated: the starting values (the
 * desktop reads them from its own state), anything it draws by hand, and enabling rules. The
 * parameter names are guessed from the Rust fields each control was bound to: check them against
 * the command's params doc before trusting OK.
 *   command: file.projectManager
 *   params:  {"destination":str,"mode":"collect|consolidate","sequences":[id]?,"excludeUnused":bool=true,"handles":frames=30,"preset":"prores_lt|prores_hq|h264|…","includeProxies":bool,"includePreviews":bool=false,"projectName":str?,"dryRun":bool=false,"overwrite":bool=false,"wait":bool=false}
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDialogsProjectManagerDialog(onRun: (command: String, params: JsonObject) -> Unit, onDismiss: () -> Unit) {
    var f0 by remember { mutableStateOf(false) }
    var f1 by remember { mutableStateOf(false) }
    var f2 by remember { mutableStateOf(false) }
    var f3 by remember { mutableStateOf(false) }
    var f4 by remember { mutableStateOf("") }
    SheetDialog(
        "Media dialogs project manager", "", onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val params = buildJsonObject {
                put("on", f0)
                put("excludeUnused", f1)
                put("includeProxies", f2)
                put("includePreviews", f3)
                if (f4.isNotBlank()) put("destination", f4)
                }
                onRun("file.projectManager", params)
                onDismiss()
            }) { Text("OK") }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { f0 = !f0 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f0, { f0 = it })
                Text("Sequences")
            }
            Row(Modifier.fillMaxWidth().clickable { f1 = !f1 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f1, { f1 = it })
                Text("Exclude Unused Clips")
            }
            Row(Modifier.fillMaxWidth().clickable { f2 = !f2 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f2, { f2 = it })
                Text("Include Proxies")
            }
            Row(Modifier.fillMaxWidth().clickable { f3 = !f3 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f3, { f3 = it })
                Text("Include Preview Files")
            }
            OutlinedTextField(f4, { f4 = it }, label = { Text("Destination Path") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // egui button "Browse…"
            // egui button "Calculate"
        }
    }
}
