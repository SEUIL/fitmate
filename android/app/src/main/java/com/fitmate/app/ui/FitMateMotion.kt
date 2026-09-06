package com.fitmate.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import com.fitmate.app.ui.theme.FitMateShapes

@Composable
internal fun SetRecordAction(label: String, confirmation: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).pressFeedback(interaction),
        interactionSource = interaction, shape = FitMateShapes.Button) {
        AnimatedContent(confirmation ?: label, transitionSpec = {
            (fadeIn(tween(150)) + scaleIn(tween(180), initialScale = .94f)) togetherWith fadeOut(tween(90))
        }, label = "setRecorded") { text ->
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun recordHighlight(event: Int): Float {
    val highlight = remember { Animatable(0f) }
    LaunchedEffect(event) {
        if (event > 0) {
            highlight.snapTo(0f)
            highlight.animateTo(1f, tween(140))
            highlight.animateTo(0f, tween(1000))
        }
    }
    return highlight.value
}

// Compose animations use the system animator duration scale, including zero.
internal const val MotionDuration = 180

@Composable
internal fun Modifier.pressFeedback(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, tween(110), label = "buttonPress")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
internal fun animatedProgress(target: Float): Float {
    val progress by animateFloatAsState(target.coerceIn(0f, 1f), tween(MotionDuration), label = "progress")
    return progress
}

@Composable
internal fun likeScale(liked: Boolean): Float {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        val newlyLiked = liked && !previous
        previous = liked
        scale.snapTo(1f)
        if (newlyLiked) scale.animateTo(1f, keyframes {
            durationMillis = 220
            1f at 0
            1.22f at 85
            1f at 220
        })
    }
    return scale.value
}

/** Animate only the incoming screen: outgoing screens never keep timers or click handlers alive. */
@Composable
internal fun RouteEntrance(route: String, tab: Boolean, depth: Int, content: @Composable () -> Unit) {
    var lastDepth by remember { mutableIntStateOf(depth) }
    val direction = remember(route) { if (depth < lastDepth) -1 else 1 }
    var first by remember { mutableStateOf(true) }
    val progress = remember(route) { Animatable(if (first) 1f else 0f) }
    val distance = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(route) {
        first = false
        lastDepth = depth
        progress.animateTo(1f, tween(MotionDuration, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = progress.value
        translationX = if (tab) 0f else direction * distance * (1f - progress.value)
    }) { content() }
}
