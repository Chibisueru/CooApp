package app.coospace.client

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.coospace.client.data.AppSettings
import app.coospace.client.data.SavedPage
import app.coospace.client.ui.screens.BrowseScreen
import app.coospace.client.ui.screens.HomeScreen
import app.coospace.client.ui.screens.SettingsScreen
import app.coospace.client.ui.theme.CooSpaceTheme
import app.coospace.client.ui.theme.appColorScheme
import app.coospace.client.ui.theme.isAppInDarkTheme
import app.coospace.client.web.CooWebController
import app.coospace.client.web.pageStyleJs

private enum class Tab(val label: Int, val selected: ImageVector, val unselected: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Browse(R.string.nav_browse, Icons.Rounded.School, Icons.Outlined.School),
    Settings(R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

class MainActivity : ComponentActivity() {

    private lateinit var settings: AppSettings
    private lateinit var web: CooWebController
    private var pendingFileCallback: ((Array<Uri>?) -> Unit)? = null
    private val deepLink = mutableStateOf<String?>(null)

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            ?: result.data?.clipData?.takeIf { result.resultCode == Activity.RESULT_OK }?.let { clip ->
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
        pendingFileCallback?.invoke(uris)
        pendingFileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        settings = AppSettings(applicationContext)
        web = CooWebController(
            this,
            onPageVisited = { title, url -> settings.addRecent(SavedPage(title, url)) },
            onSessionDetected = settings::updateSignedIn,
        )
        web.launchFilePicker = { intent, callback ->
            pendingFileCallback?.invoke(null)
            pendingFileCallback = callback
            runCatching { filePicker.launch(intent) }.isSuccess.also { if (!it) { callback(null); pendingFileCallback = null } }
        }
        savedInstanceState?.let { web.webView.restoreState(it) }
        handleIntent(intent)

        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) deepLink.value = data.toString()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    @Composable
    private fun App() {
        val dark = isAppInDarkTheme(settings.themeMode)
        val scheme = appColorScheme(dark, settings.dynamicColor)
        var tab by rememberSaveable { mutableStateOf(Tab.Home) }

        DisposableEffect(dark) {
            val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }

        LaunchedEffect(scheme, dark, settings.restylePages) {
            web.setStyle(pageStyleJs(scheme, dark, settings.restylePages))
        }
        LaunchedEffect(settings.textZoom) { web.setTextZoom(settings.textZoom) }

        val link = deepLink.value
        LaunchedEffect(link) {
            if (link != null) {
                web.load(link)
                tab = Tab.Browse
                deepLink.value = null
            }
        }
        LaunchedEffect(tab) { if (tab == Tab.Browse) web.loadIfNeeded(settings.baseUrl) }

        CooSpaceTheme(colorScheme = scheme) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(if (tab == t) t.selected else t.unselected, null) },
                                label = { Text(stringResource(t.label)) },
                            )
                        }
                    }
                },
            ) { padding ->
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab",
                ) { current ->
                    when (current) {
                        Tab.Home -> HomeScreen(settings, padding) { url ->
                            web.load(url)
                            tab = Tab.Browse
                        }
                        Tab.Browse -> BrowseScreen(web, settings, padding)
                        Tab.Settings -> SettingsScreen(settings, padding) {
                            web.signOut()
                            settings.updateSignedIn(false)
                            tab = Tab.Home
                        }
                    }
                }
            }
        }
    }
}
