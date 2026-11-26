package io.homeassistant.companion.android.launch

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

private const val FORK_PACKAGE = "io.homeassistant.companion.android.minimal.debug"
private const val FORK_REPOSITORY = "hubertpi/android_HA_app"
private const val RELEASE_API = "https://api.github.com/repos/$FORK_REPOSITORY/releases/latest"
private const val RELEASES_PAGE = "https://github.com/$FORK_REPOSITORY/releases"
private const val TAG_PREFIX = "apk-"
private const val APK_ASSET_NAME = "Home-Assistant-Fork.apk"
private const val NOTIFICATION_ID = "android_ha_fork_update"
private const val NOTIFICATION_DOMAIN = "persistent_notification"

private val releaseClient = OkHttpClient()
private val releaseJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ForkRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ForkReleaseAsset> = emptyList(),
)

@Serializable
private data class ForkReleaseAsset(val name: String)

@Composable
internal fun ForkUpdatePrompt(serverManager: ServerManager, enabled: Boolean) {
    val context = LocalContext.current
    if (context.packageName != FORK_PACKAGE) return

    val title = stringResource(commonR.string.fork_update_title)
    val message = stringResource(commonR.string.fork_update_message)
    var releasePage by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled || checked) return@LaunchedEffect
        val installedVersion = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0),
        )
        releasePage = findForkUpdate(installedVersion)
        checked = true

        val page = releasePage ?: return@LaunchedEffect
        postUpdateNotification(serverManager, title, message, page)
    }

    val page = releasePage
    if (enabled && page != null) {
        AlertDialog(
            onDismissRequest = { releasePage = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, page.toUri()))
                        releasePage = null
                    } catch (_: ActivityNotFoundException) {
                        Timber.w("No browser available to open the fork release")
                    }
                }) {
                    Text(stringResource(commonR.string.update))
                }
            },
            dismissButton = {
                TextButton(onClick = { releasePage = null }) {
                    Text(stringResource(commonR.string.fork_update_later))
                }
            },
        )
    }
}

private suspend fun findForkUpdate(installedVersion: Long): String? = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(RELEASE_API).build()
    try {
        releaseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            parseForkUpdate(response.body.string(), installedVersion)
        }
    } catch (_: IOException) {
        null
    }
}

internal fun parseForkUpdate(body: String, installedVersion: Long): String? {
    val release = try {
        releaseJson.decodeFromString<ForkRelease>(body)
    } catch (_: SerializationException) {
        return null
    }
    val version = release.tagName.removePrefix(TAG_PREFIX).toLongOrNull() ?: return null
    if (release.tagName != "$TAG_PREFIX$version" || version <= installedVersion || release.draft || release.prerelease) {
        return null
    }
    if (release.assets.none { it.name == APK_ASSET_NAME }) return null

    return "$RELEASES_PAGE/tag/$TAG_PREFIX$version"
}

private suspend fun postUpdateNotification(serverManager: ServerManager, title: String, message: String, page: String) {
    try {
        val server = serverManager.getServer() ?: return
        serverManager.integrationRepository(server.id).callAction(
            domain = NOTIFICATION_DOMAIN,
            action = "create",
            actionData = mapOf(
                "notification_id" to NOTIFICATION_ID,
                "title" to title,
                "message" to "$message [Open the release]($page)",
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Unable to post the fork update notification")
    }
}
