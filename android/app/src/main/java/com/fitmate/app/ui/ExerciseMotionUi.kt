package com.fitmate.app.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun exerciseBodyLabel(value: String?) = when (value) {
    "LEGS", "HAMSTRINGS", "GLUTES", "QUADRICEPS" -> "하체"
    "CHEST" -> "가슴"; "BACK" -> "등"; "SHOULDERS" -> "어깨"
    "BICEPS", "TRICEPS" -> "팔"; "CORE" -> "코어"; "FULL", "FULL_BODY" -> "전신"
    else -> value?.takeIf { it.isNotBlank() } ?: "기타"
}
internal fun exerciseEquipmentLabel(value: String?) = when (value) {
    "BARBELL" -> "바벨"; "DUMBBELL" -> "덤벨"; "MACHINE" -> "머신"
    "CABLE" -> "케이블"; "BODYWEIGHT" -> "맨몸"; "KETTLEBELL" -> "케틀벨"; "BAND" -> "밴드"
    else -> value?.takeIf { it.isNotBlank() } ?: "기구 정보 없음"
}

@Composable internal fun ExerciseThumbnail(id: String?, name: String) {
    val motion = exerciseMotion(id, name)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        if (motion != null) Image(painterResource(motion.poster), null, Modifier.size(52.dp))
        else Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable internal fun ExerciseAnimation(id: String?, name: String, playing: Boolean = true, compact: Boolean = false) {
    val motion = exerciseMotion(id, name)
    if (motion == null) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("동작 애니메이션 준비 중", style = MaterialTheme.typography.titleSmall)
                    Text("세트와 운동 기록은 계속할 수 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    key(motion.video) {
        val context = LocalContext.current
        val owner = LocalLifecycleOwner.current
        var failed by remember { mutableStateOf(false) }
        var prepared by remember { mutableStateOf(false) }
        var paused by remember { mutableStateOf(false) }
        val shouldPlay by rememberUpdatedState(playing && !paused)
        val video = remember { VideoView(context) }
        DisposableEffect(video, owner) {
            video.setOnPreparedListener { player ->
                player.isLooping = true; player.setVolume(0f, 0f); prepared = true
                if (shouldPlay && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) video.start()
                else video.seekTo(1)
            }
            video.setOnErrorListener { _, _, _ -> failed = true; true }
            video.setVideoURI(Uri.parse("android.resource://${context.packageName}/${motion.video}"))
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && shouldPlay && prepared) video.start()
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) video.pause()
            }
            owner.lifecycle.addObserver(observer)
            onDispose {
                owner.lifecycle.removeObserver(observer)
                video.setOnPreparedListener(null); video.setOnErrorListener(null); video.stopPlayback()
            }
        }
        LaunchedEffect(playing, paused, prepared) {
            if (prepared) {
                if (shouldPlay && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) video.start()
                else video.pause()
            }
        }
        if (failed) Text("동작 영상을 불러오지 못했어요. 운동 기록은 계속할 수 있어요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Surface(shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.White) {
          Box(Modifier.fillMaxWidth().height(if (compact) 164.dp else 210.dp).clip(RoundedCornerShape(16.dp))) {
            AndroidView(factory = { video }, modifier = Modifier.fillMaxSize())
            if (!prepared) Image(painterResource(motion.poster), "$name 동작", Modifier.fillMaxSize())
            TextButton({ paused = !paused }, Modifier.align(Alignment.BottomEnd), enabled = playing) {
                Text(if (!playing) "동작 일시정지 중" else if (paused) "동작 재생" else "동작 일시정지", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
    }
}

@Composable internal fun ExerciseIntroduction(id: String?, name: String, body: String?, equipment: String?, description: String? = null) {
    Text(name, style = MaterialTheme.typography.headlineSmall)
    Text("${exerciseBodyLabel(body)} · ${exerciseEquipmentLabel(equipment)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    ExerciseAnimation(id, name)
    Text("어떤 운동인가요?", style = MaterialTheme.typography.titleMedium)
    Text(description?.takeIf { it.isNotBlank() } ?: exerciseMotion(id, name)?.description
        ?: "$name 운동입니다. ${exerciseBodyLabel(body)} 운동으로 루틴에 추가하고 세트별 중량과 횟수를 기록할 수 있어요.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
