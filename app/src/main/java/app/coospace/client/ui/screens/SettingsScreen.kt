package app.coospace.client.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.coospace.client.R
import app.coospace.client.data.AppSettings
import app.coospace.client.data.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showSignOut by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(Modifier.nestedScroll(scroll.nestedScrollConnection)) {
        LargeTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scroll)

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { Header(stringResource(R.string.settings_appearance)) }
            item {
                Group {
                    ListItem(
                        colors = groupColors(),
                        leadingContent = { Icon(Icons.Rounded.Brightness6, null) },
                        headlineContent = { Text(stringResource(R.string.settings_theme)) },
                        supportingContent = {
                            val modes = ThemeMode.entries
                            val labels = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                modes.forEachIndexed { i, mode ->
                                    SegmentedButton(
                                        selected = settings.themeMode == mode,
                                        onClick = { settings.updateThemeMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                    ) { Text(stringResource(labels[i])) }
                                }
                            }
                        },
                    )
                    ListItem(
                        modifier = Modifier.clickable(enabled = dynamicSupported) { settings.updateDynamicColor(!settings.dynamicColor) },
                        colors = groupColors(),
                        leadingContent = { Icon(Icons.Rounded.Palette, null) },
                        headlineContent = { Text(stringResource(R.string.settings_dynamic)) },
                        supportingContent = {
                            Text(stringResource(if (dynamicSupported) R.string.settings_dynamic_desc else R.string.settings_dynamic_unavailable))
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColor && dynamicSupported,
                                enabled = dynamicSupported,
                                onCheckedChange = settings::updateDynamicColor,
                            )
                        },
                    )
                    ListItem(
                        modifier = Modifier.clickable { settings.updateRestylePages(!settings.restylePages) },
                        colors = groupColors(),
                        leadingContent = { Icon(Icons.Rounded.FormatPaint, null) },
                        headlineContent = { Text(stringResource(R.string.settings_restyle)) },
                        supportingContent = { Text(stringResource(R.string.settings_restyle_desc)) },
                        trailingContent = { Switch(checked = settings.restylePages, onCheckedChange = settings::updateRestylePages) },
                    )
                    var zoom by remember { mutableFloatStateOf(settings.textZoom.toFloat()) }
                    ListItem(
                        colors = groupColors(),
                        leadingContent = { Icon(Icons.Rounded.TextFields, null) },
                        headlineContent = { Text(stringResource(R.string.settings_text_size) + " · ${zoom.roundToInt()}%") },
                        supportingContent = {
                            Slider(
                                value = zoom,
                                onValueChange = { zoom = it },
                                onValueChangeFinished = { settings.updateTextZoom(zoom.roundToInt()) },
                                valueRange = 80f..160f,
                                steps = 7,
                            )
                        },
                    )
                }
            }

            item { Header(stringResource(R.string.settings_account)) }
            item {
                Group {
                    ListItem(
                        modifier = Modifier.clickable { showUrl = true },
                        colors = groupColors(),
                        leadingContent = { Icon(Icons.Rounded.Link, null) },
                        headlineContent = { Text(stringResource(R.string.settings_server)) },
                        supportingContent = { Text(settings.baseUrl) },
                    )
                    ListItem(
                        modifier = Modifier.clickable { showSignOut = true },
                        colors = groupColors(),
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text(stringResource(R.string.settings_signout), color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text(stringResource(R.string.settings_signout_desc)) },
                    )
                }
            }

            item { Header(stringResource(R.string.settings_about)) }
            item {
                Group {
                    ListItem(
                        colors = groupColors(),
                        leadingContent = { Icon(Icons.Rounded.Info, null) },
                        headlineContent = { Text(stringResource(R.string.app_name) + " " + appVersion(context)) },
                        supportingContent = { Text(stringResource(R.string.about_body)) },
                    )
                }
            }
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            title = { Text(stringResource(R.string.signout_confirm_title)) },
            text = { Text(stringResource(R.string.signout_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOut = false
                    onSignOut()
                    Toast.makeText(context, R.string.signed_out, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_signout)) }
            },
            dismissButton = { TextButton(onClick = { showSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showUrl) {
        var value by remember { mutableStateOf(settings.baseUrl) }
        AlertDialog(
            onDismissRequest = { showUrl = false },
            icon = { Icon(Icons.Rounded.Link, null) },
            title = { Text(stringResource(R.string.settings_server)) },
            text = {
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotBlank(),
                    onClick = { settings.updateBaseUrl(value); showUrl = false },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { value = AppSettings.DEFAULT_BASE_URL }) { Text("Reset") }
            },
        )
    }
}

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

@Composable
private fun groupColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}
