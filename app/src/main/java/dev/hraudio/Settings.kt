// SPDX-License-Identifier: MIT
package dev.hraudio

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private fun Context.audioPrefs() = getSharedPreferences("audio", Context.MODE_PRIVATE)

internal fun Context.loadOptions(): RunOptions {
    val prefs = audioPrefs()
    val bounds = prefs.getString("bounds", null)?.split(",")?.map(String::toInt).orEmpty()
    return RunOptions(bounds, prefs.getInt("seconds", 60), prefs.getBoolean("zoneAlerts", true))
}

internal fun Context.saveOptions(options: RunOptions) {
    audioPrefs().edit().putString("bounds", options.bounds.takeIf { it.isNotEmpty() }?.joinToString(","))
        .putInt("seconds", options.seconds).putBoolean("zoneAlerts", options.zoneAlerts).apply()
}

@Composable
internal fun SettingsScreen(stored: RunOptions, save: (RunOptions) -> Unit, done: () -> Unit) {
    var fields by rememberSaveable { mutableStateOf(ArrayList((0..4).map { stored.bounds.getOrNull(it)?.toString().orEmpty() })) }
    var interval by rememberSaveable { mutableStateOf(stored.seconds.toString()) }
    var alerts by rememberSaveable { mutableStateOf(stored.zoneAlerts) }
    val bounds = fields.mapNotNull(String::toIntOrNull)
    val validZones = fields.all(String::isBlank) || (bounds.size == 5 && bounds.first() > 0 &&
        bounds.zipWithNext().all { (low, high) -> low < high })
    val seconds = interval.toIntOrNull()
    val valid = validZones && seconds != null && seconds >= 0
    val options = if (valid) RunOptions(bounds, seconds!!, alerts) else null
    LaunchedEffect(options) { options?.let(save) }
    BackHandler { options?.let(save); done() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Zone boundaries", style = MaterialTheme.typography.titleLarge)
            Text("Enter the starting BPM for each zone, in increasing order.")
            fields.forEachIndexed { index, value ->
                val next = fields.getOrNull(index + 1)?.toIntOrNull()
                OutlinedTextField(value, { input ->
                    fields = ArrayList(fields).apply { set(index, input) }
                }, label = { Text("Zone ${index + 1} start") },
                    supportingText = {
                        if (validZones && bounds.isNotEmpty()) Text(if (next == null) "$value+ BPM" else "$value–${next - 1} BPM")
                    }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (!validZones) Text("Complete all five increasing boundaries to save your changes.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Speak zone changes")
                Switch(checked = alerts, onCheckedChange = { alerts = it })
            }
            OutlinedTextField(interval, { interval = it }, label = { Text("BPM readout interval (seconds)") },
                supportingText = { Text("Enter 0 to turn off periodic readings.") },
                isError = seconds == null || seconds < 0, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = { options?.let(save); done() }, enabled = valid) { Text("Done") }
        }
    }
}
