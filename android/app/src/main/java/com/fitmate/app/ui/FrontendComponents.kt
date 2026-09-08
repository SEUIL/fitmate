package com.fitmate.app.ui

import android.graphics.ImageDecoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.core.content.FileProvider
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.ServerSocialApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun Choices(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected == option, { onSelect(option) }, { Text(option) }) }
    }
}

@Composable internal fun ToggleLine(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = value, role = Role.Switch, onValueChange = onChange), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, null)
    }
}

@Composable internal fun ErrorText(message: String?) {
    var lastMessage by remember { mutableStateOf(message.orEmpty()) }
    LaunchedEffect(message) { if (message != null) lastMessage = message }
    AnimatedVisibility(message != null,
        enter = fadeIn(tween(MotionDuration)) + expandVertically(tween(MotionDuration)),
        exit = fadeOut(tween(100)) + shrinkVertically(tween(MotionDuration))) {
        Text(message ?: lastMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Keeps mock-state controls available to developers without shipping them in release builds. */
@Composable internal fun DebugOnly(content: @Composable () -> Unit) {
    if (BuildConfig.DEBUG) content()
}

@Composable internal fun ConfirmAction(title: String, body: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(confirm) { Text("확인") } },
        dismissButton = { TextButton(dismiss) { Text("취소") } })
}

/** The picker grants access only to a user-selected image; no broad gallery permission. */
@Composable internal fun PhotoActions(onSelected: (String) -> Unit, camera: Boolean = false) {
    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            error = null; onSelected(uri.toString())
        }
    }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val pending = pendingCameraUri
        pendingCameraUri = null
        if (saved && pending != null) { error = null; onSelected(pending) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({
            runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .onFailure { error = "사진 선택을 열 수 없어요. 다시 시도해 주세요." }
        }, Modifier.weight(1f)) { Text("사진 선택") }
        if (camera) OutlinedButton({
            runCatching {
                val folder = File(context.cacheDir, "captures").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", File.createTempFile("inbody_", ".jpg", folder))
                pendingCameraUri = uri.toString()
                capture.launch(uri)
            }.onFailure { error = "카메라를 열 수 없어요. 갤러리에서 사진을 선택해 주세요." }
        }, Modifier.weight(1f)) { Text("촬영") }
    }
    ErrorText(error)
}

@Composable internal fun LocalPhoto(uri: String, modifier: Modifier = Modifier, square: Boolean = false) {
    val context = LocalContext.current
    var failed by remember(uri) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val scale = (info.size.width.coerceAtLeast(info.size.height) / 1024f).coerceAtLeast(1f)
                    decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            }.getOrNull()
        }
        failed = value == null
    }
    when {
        bitmap != null -> Image(bitmap!!, "선택한 사진", if (square) modifier.fillMaxWidth().aspectRatio(1f) else modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp), contentScale = if (square) ContentScale.Crop else ContentScale.Fit)
        failed -> Text("사진을 불러올 수 없어요. 다시 선택해 주세요.", color = MaterialTheme.colorScheme.error)
        else -> LinearProgressIndicator(modifier.fillMaxWidth())
    }
}

/** Renders both device picker URIs and authenticated media paths returned by the server. */
@Composable internal fun FitMatePhoto(uri: String, server: ServerSocialApi?, modifier: Modifier = Modifier, square: Boolean = false) {
    if (uri.startsWith("content:") || uri.startsWith("file:")) LocalPhoto(uri, modifier, square)
    else if (server != null) ServerPhoto(uri, server, modifier, square)
    else Text("사진을 불러올 수 없어요.", color = MaterialTheme.colorScheme.error)
}

@Composable private fun ServerPhoto(path: String, server: ServerSocialApi, modifier: Modifier, square: Boolean) {
    var failed by remember(path) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(server.imageBytes(path)))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val scale = (info.size.width.coerceAtLeast(info.size.height) / 1024f).coerceAtLeast(1f)
                    decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            }.getOrNull()
        }
        failed = value == null
    }
    when {
        bitmap != null -> Image(bitmap!!, "업로드한 사진", if (square) modifier.fillMaxWidth().aspectRatio(1f) else modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp), contentScale = if (square) ContentScale.Crop else ContentScale.Fit)
        failed -> Text("사진을 불러올 수 없어요.", color = MaterialTheme.colorScheme.error)
        else -> LinearProgressIndicator(modifier.fillMaxWidth())
    }
}
