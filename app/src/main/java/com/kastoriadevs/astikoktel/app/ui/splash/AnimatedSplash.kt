package com.kastoriadevs.astikoktel.app.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.theme.Lake
import com.kastoriadevs.astikoktel.app.ui.theme.LakeLight
import com.kastoriadevs.astikoktel.app.ui.theme.LakeMid
import com.kastoriadevs.astikoktel.app.ui.theme.OffWhite
import com.kastoriadevs.astikoktel.app.ui.theme.RouteYellow
import androidx.compose.ui.graphics.Color

/*
 * Animated splash, built from the app icon (B3).
 * Design space: 390 x 844 units (same as the approved storyboard). The art sits at 39% of the height,
 * the waves stick to the bottom edge. Timings in ms:
 *   0-430 waves rise, 290-1220 route draws, stops pop at 320 and 720, bus at 1120, title at 1370,
 *   loader from 1550 until the data is ready, then a 360 ms exit that reveals Home underneath.
 */

private const val INTRO_MS = 1800f
private const val EXIT_MS = 360

private val EaseOut: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val RouteEasing: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val Overshoot: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private val Wave1: Path = PathParser()
    .parsePathString("M0 690 C 90 650, 190 690, 290 645 C 330 628, 365 612, 390 606 V 844 H 0 Z")
    .toPath()
private val Wave2: Path = PathParser()
    .parsePathString("M0 770 C 110 738, 230 772, 390 716 V 844 H 0 Z")
    .toPath()

/** Route in icon units (512 space) */
private val RoutePath: Path = PathParser()
    .parsePathString("M150 360 C 150 300, 250 330, 250 270 S 340 240, 340 180")
    .toPath()
private val RouteLength: Float by lazy { PathMeasure().apply { setPath(RoutePath, false) }.length }

private val Stop1 = Offset(150f, 360f)
private val Stop2 = Offset(250f, 270f)
private val Bus = Offset(340f, 180f)

/** Progress 0..1 of [t] inside [start, end], eased */
private fun window(t: Float, start: Float, end: Float, easing: Easing): Float =
    easing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

/** Stop pop: 0 -> 1.25 -> 1 */
private fun pop(t: Float, start: Float): Float = when {
    t <= start -> 0f
    t < start + 180f -> 1.25f * EaseOut.transform((t - start) / 180f)
    t < start + 288f -> 1.25f - 0.25f * ((t - start - 180f) / 108f)
    else -> 1f
}

/** Small double hop of the bus after it lands, in icon units */
private fun hop(t: Float): Float {
    fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f.coerceIn(0f, 1f)
    return when {
        t < 1440f -> 0f
        t < 1548f -> lerp(0f, -8f, (t - 1440f) / 108f)
        t < 1656f -> lerp(-8f, 0f, (t - 1548f) / 108f)
        t < 1728f -> lerp(0f, -3f, (t - 1656f) / 72f)
        t < 1800f -> lerp(-3f, 0f, (t - 1728f) / 72f)
        else -> 0f
    }
}

/**
 * Full-screen splash. Plays the intro once, keeps the loader running until [ready],
 * then fades out (revealing what is composed underneath) and calls [onFinished].
 */
@Composable
fun AnimatedSplash(ready: Boolean, onFinished: () -> Unit) {
    StatusBarIcons(darkIcons = false)
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val time = remember { Animatable(if (reduceMotion) INTRO_MS else 0f) }
    val exit = remember { Animatable(0f) }
    val introDone by remember { derivedStateOf { time.value >= INTRO_MS } }

    LaunchedEffect(Unit) {
        if (!reduceMotion) time.animateTo(INTRO_MS, tween(INTRO_MS.toInt(), easing = LinearEasing))
    }
    LaunchedEffect(introDone, ready) {
        if (introDone && ready) {
            if (!reduceMotion) exit.animateTo(1f, tween(EXIT_MS, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    val loader = rememberInfiniteTransition(label = "splash-loader")
    val barX by loader.animateFloat(
        initialValue = -56f,
        targetValue = 56f,
        animationSpec = infiniteRepeatable(tween(640, easing = LinearEasing), RepeatMode.Restart),
        label = "splash-loader-bar",
    )

    val t = time.value
    val e = exit.value

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - e }
            .background(Lake)
            // Swallow touches so nothing underneath reacts while the splash is up
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val unit = maxWidth / 390f
        val artCenterY = maxHeight * 0.39f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f + 0.06f * e
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0.39f)
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.width / 390f
                val bottom = size.height - 844f * s

                val w1 = window(t, 0f, 432f, EaseOut)
                if (w1 > 0f) {
                    withTransform({
                        translate(0f, bottom + (1f - w1) * 90f * s)
                        scale(s, s, pivot = Offset.Zero)
                    }) { drawPath(Wave1, LakeMid, alpha = w1) }
                }
                val w2 = window(t, 144f, 576f, EaseOut)
                if (w2 > 0f) {
                    withTransform({
                        translate(0f, bottom + (1f - w2) * 120f * s)
                        scale(s, s, pivot = Offset.Zero)
                    }) { drawPath(Wave2, LakeLight, alpha = w2) }
                }

                val cx = size.width / 2f
                val cy = size.height * 0.39f
                withTransform({
                    translate(cx - 195f * s, cy - 330f * s)
                    scale(s, s, pivot = Offset.Zero)
                    translate(-3.75f, 142.5f)
                    scale(0.75f, 0.75f, pivot = Offset.Zero)
                }) { drawArt(t) }
            }

            val titleIn = window(t, 1368f, 1800f, EaseOut)
            val loaderIn = window(t, 1550f, 1800f, LinearEasing)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = artCenterY + unit * 150f)
                    .graphicsLayer {
                        alpha = titleIn
                        translationY = (1f - titleIn) * 18f * unit.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.splash_title_1),
                    color = OffWhite,
                    fontSize = (30f * unit.value).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.splash_title_2),
                    color = Color(0xFFBFD6E1),
                    fontSize = (20f * unit.value).sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .padding(top = unit * 22f)
                        .width(unit * 80f)
                        .height(unit * 4f)
                        .graphicsLayer { alpha = loaderIn }
                        .clip(RoundedCornerShape(50))
                        .background(OffWhite.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = unit * (20f + if (reduceMotion) 0f else barX))
                            .width(unit * 40f)
                            .height(unit * 4f)
                            .clip(RoundedCornerShape(50))
                            .background(RouteYellow),
                    )
                }
            }
        }
    }
}

/** The icon art in icon units (512 space), at time [t] */
private fun DrawScope.drawArt(t: Float) {
    val route = window(t, 288f, 1224f, RouteEasing)
    if (route > 0f) {
        val measure = PathMeasure().apply { setPath(RoutePath, false) }
        val segment = Path()
        measure.getSegment(0f, RouteLength * route, segment, true)
        drawPath(segment, RouteYellow, style = Stroke(width = 36f, cap = StrokeCap.Round))
    }
    drawStop(Stop1, pop(t, 324f))
    drawStop(Stop2, pop(t, 720f))

    val bus = window(t, 1116f, 1440f, Overshoot)
    if (bus > 0f) {
        withTransform({ scale(bus, bus, pivot = Bus) }) {
            drawCircle(OffWhite, radius = 64f, center = Bus)
            withTransform({ translate(0f, hop(t)) }) { drawBusGlyph() }
        }
    }
}

private fun DrawScope.drawStop(center: Offset, scale: Float) {
    if (scale <= 0f) return
    withTransform({ scale(scale, scale, pivot = center) }) {
        drawCircle(Lake, radius = 25f, center = center)
        drawCircle(OffWhite, radius = 25f, center = center, style = Stroke(width = 14f))
    }
}

private fun DrawScope.drawBusGlyph() {
    drawRoundRect(Lake, topLeft = Offset(306f, 144f), size = Size(68f, 70f), cornerRadius = CornerRadius(16f))
    drawRoundRect(RouteYellow, topLeft = Offset(315f, 155f), size = Size(50f, 28f), cornerRadius = CornerRadius(6f))
    drawCircle(OffWhite, radius = 5.5f, center = Offset(324f, 199f))
    drawCircle(OffWhite, radius = 5.5f, center = Offset(356f, 199f))
    drawRoundRect(Lake, topLeft = Offset(312f, 211f), size = Size(13f, 10f), cornerRadius = CornerRadius(3f))
    drawRoundRect(Lake, topLeft = Offset(355f, 211f), size = Size(13f, 10f), cornerRadius = CornerRadius(3f))
}
