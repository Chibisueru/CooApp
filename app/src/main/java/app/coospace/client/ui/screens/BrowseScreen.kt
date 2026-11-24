package app.coospace.client.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.coospace.client.R
import app.coospace.client.data.AppSettings
import app.coospace.client.data.SavedPage
import app.coospace.client.web.CooWebController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    controller: CooWebController,
    settings: AppSettings,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = controller.canGoBack) { controller.goBack() }

    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            navigationIcon = {
                if (controller.canGoBack) {
                    IconButton(onClick = controller::goBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                }
            },
            title = {
                Column {
                    Text(
                        controller.title.ifBlank { stringResource(if (controller.loading) R.string.loading else R.string.app_name) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    controller.url?.let { u ->
                        Text(
                            Uri.parse(u).host.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
            actions = {
                val pinned = settings.isPinned(controller.url)
                IconButton(
                    enabled = controller.url != null,
                    onClick = {
                        val u = controller.url ?: return@IconButton
                        val title = controller.title.ifBlank { Uri.parse(u).lastPathSegment ?: u }
                        val now = settings.togglePin(SavedPage(title, u))
                        Toast.makeText(context, if (now) R.string.pinned_toast else R.string.unpinned_toast, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        stringResource(if (pinned) R.string.unpin_page else R.string.pin_page),
                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = controller::reload) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.go_home)) },
                            leadingIcon = { Icon(Icons.Rounded.Home, null) },
                            onClick = { menuOpen = false; controller.load(settings.baseUrl) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_link)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, null) },
                            enabled = controller.url != null,
                            onClick = {
                                menuOpen = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, controller.url)
                                    .putExtra(Intent.EXTRA_SUBJECT, controller.title)
                                context.startActivity(Intent.createChooser(send, null))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_in_browser)) },
                            leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                            enabled = controller.url != null,
                            onClick = {
                                menuOpen = false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(controller.url))) }
                            },
                        )
                    }
                }
            },
        )

        AnimatedVisibility(controller.loading) {
            LinearProgressIndicator(
                progress = { controller.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    controller.webView.also { wv ->
                        (wv.parent as? ViewGroup)?.removeView(wv)
                    }
                },
                update = { it.setBackgroundColor(surface.toArgb()) },
                onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
            )

            if (controller.error) {
                Column(
                    Modifier.fillMaxSize().background(surface).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CloudOff, null, Modifier.size(44.dp)) }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.error_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.error_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (controller.errorDetail.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            controller.errorDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = controller::reload) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}
