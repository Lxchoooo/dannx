package com.dannx.walls.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bienvenida en tres pantallas: qué es DannX, cómo descubrir fondos y cómo
 * aplicarlos. Sale una sola vez la primera vez que se abre la app.
 */
@Composable
fun OnboardingScreen(liteMode: Boolean, onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = R.string.onboarding_title_1,
                message = R.string.onboarding_message_1,
                kicker = null,
                icon = null,
            ),
            OnboardingPage(
                title = R.string.onboarding_title_2,
                message = R.string.onboarding_message_2,
                kicker = R.string.onboarding_kicker_explore,
                icon = Icons.Rounded.AutoAwesome,
            ),
            OnboardingPage(
                title = R.string.onboarding_title_3,
                message = R.string.onboarding_message_3,
                kicker = R.string.onboarding_kicker_apply,
                icon = Icons.Rounded.Wallpaper,
            ),
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }

    val accents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    // El acento del fondo acompaña el cambio de página.
    val backdropAccent by animateColorAsState(
        targetValue = accents[pagerState.currentPage],
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "backdropAccent",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (finishing) 0f else 1f,
        animationSpec = tween(220),
        label = "onboardingContent",
    )

    // El remate: el logo aparece, respira y da paso a la app.
    LaunchedEffect(finishing) {
        if (finishing) {
            delay(560)
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedBackdrop(accent = backdropAccent, liteMode = liteMode)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
                .padding(vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onFinish,
                    enabled = !finishing,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 18.dp,
                beyondViewportPageCount = 1,
                userScrollEnabled = !finishing,
                key = { it },
            ) { index ->
                PageContent(
                    page = pages[index],
                    index = index,
                    accent = accents[index],
                    pagerState = pagerState,
                    liteMode = liteMode,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    val width by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        label = "dotWidth",
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    accents[index]
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }

            val isLast = pagerState.currentPage == pages.lastIndex
            Button(
                onClick = {
                    when {
                        isLast && liteMode -> onFinish()
                        isLast -> finishing = true
                        else -> scope.launch {
                            pagerState.animateScrollToPage(
                                page = pagerState.currentPage + 1,
                                animationSpec = tween(
                                    durationMillis = 420,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                enabled = !finishing,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                AnimatedContent(
                    targetState = isLast,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                    },
                    label = "onboardingButtonLabel",
                ) { last ->
                    Text(
                        text = stringResource(
                            if (last) R.string.onboarding_start else R.string.onboarding_next,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = finishing,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            FinishStage(accent = backdropAccent)
        }
    }
}

private data class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val kicker: Int?,
    val icon: ImageVector?,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageContent(
    page: OnboardingPage,
    index: Int,
    accent: Color,
    pagerState: androidx.compose.foundation.pager.PagerState,
    liteMode: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = (pagerState.currentPage - index) +
                    pagerState.currentPageOffsetFraction
                val distance = abs(offset).coerceIn(0f, 1f)
                alpha = 1f - distance * 0.6f
                translationY = -offset * 38.dp.toPx()
                val scale = 1f - distance * 0.05f
                scaleX = scale
                scaleY = scale
            },
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 30.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (index == 0) {
                WordmarkRow(accent = accent, liteMode = liteMode)
                Spacer(Modifier.height(28.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    page.icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = accent,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                page.kicker?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            Text(
                text = stringResource(page.title),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(page.message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Marca de la primera pantalla: palabra grande y miniaturas decorativas. */
@Composable
private fun WordmarkRow(accent: Color, liteMode: Boolean) {
    if (liteMode) {
        WordmarkContent(accent = accent, drift = null)
    } else {
        val transition = rememberInfiniteTransition(label = "wordmark")
        val drift = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wordmarkDrift",
        )
        WordmarkContent(accent = accent, drift = drift)
    }
}

@Composable
private fun WordmarkContent(accent: Color, drift: State<Float>?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            val t = drift?.value ?: 0f
            translationY = -t * 6.dp.toPx()
            scaleX = 0.99f + 0.02f * t
            scaleY = 0.99f + 0.02f * t
        },
    ) {
        Text(
            text = "DannX",
            style = MaterialTheme.typography.displayLarge,
            color = accent,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple(-8f, MaterialTheme.colorScheme.primaryContainer, 64.dp),
                Triple(0f, MaterialTheme.colorScheme.tertiaryContainer, 64.dp),
                Triple(8f, MaterialTheme.colorScheme.secondaryContainer, 64.dp),
            ).forEachIndexed { index, (rotation, color, size) ->
                Box(
                    Modifier
                        .size(width = size, height = size * 1.5f)
                        .graphicsLayer {
                            val t = drift?.value ?: 0f
                            rotationZ = rotation + (index - 1) * 3f * t
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .background(color),
                )
            }
        }
    }
}

/**
 * Remate al terminar la bienvenida: el logo entra, escala y deja ver la app.
 */
@Composable
private fun FinishStage(accent: Color) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 190f,
        ),
        label = "finishStage",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = progress
                val scale = 0.86f + 0.14f * progress
                scaleX = scale
                scaleY = scale
            },
        ) {
            Text(
                text = "DannX",
                style = MaterialTheme.typography.displayLarge,
                color = accent,
            )
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                ).forEachIndexed { index, color ->
                    Box(
                        Modifier
                            .size(width = 64.dp, height = 96.dp)
                            .graphicsLayer {
                                rotationZ = (index - 1) * 10f * progress
                                translationY = (1f - progress) * 14.dp.toPx()
                            }
                            .clip(RoundedCornerShape(22.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}

/**
 * Fondo del setup: formas expresivas que rotan y derivan a distinta velocidad,
 * así nunca se repite el mismo dibujo. En modo lite quedan quietas.
 */
@Composable
private fun AnimatedBackdrop(accent: Color, liteMode: Boolean) {
    if (liteMode) {
        BackdropShapes(
            accent = accent,
            spinA = null,
            spinB = null,
            driftA = null,
            driftB = null,
            breathe = null,
        )
    } else {
        val transition = rememberInfiniteTransition(label = "backdrop")
        BackdropShapes(
            accent = accent,
            spinA = transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(52_000, easing = LinearEasing),
                    initialStartOffset = StartOffset(0),
                ),
                label = "spinA",
            ),
            spinB = transition.animateFloat(
                initialValue = 0f,
                targetValue = -360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(38_000, easing = LinearEasing),
                    initialStartOffset = StartOffset(3_500),
                ),
                label = "spinB",
            ),
            driftA = transition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(21_000, easing = LinearEasing),
                    initialStartOffset = StartOffset(1_200),
                ),
                label = "driftA",
            ),
            driftB = transition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(29_000, easing = LinearEasing),
                    initialStartOffset = StartOffset(6_000),
                ),
                label = "driftB",
            ),
            breathe = transition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(9_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "breathe",
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackdropShapes(
    accent: Color,
    spinA: State<Float>?,
    spinB: State<Float>?,
    driftA: State<Float>?,
    driftB: State<Float>?,
    breathe: State<Float>?,
) {
    Box(Modifier.fillMaxSize()) {
        // Cookie grande arriba a la derecha.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 78.dp, y = (-46).dp)
                .size(236.dp)
                .graphicsLayer {
                    rotationZ = spinA?.value ?: 0f
                    val phase = driftA?.value ?: 0f
                    translationX = cos(phase) * 9.dp.toPx()
                    translationY = sin(phase) * 9.dp.toPx()
                    val scale = breathe?.value ?: 1f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(accent.copy(alpha = 0.18f)),
        )

        // Blob redondeado abajo a la izquierda.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-64).dp, y = 54.dp)
                .size(176.dp)
                .graphicsLayer {
                    rotationZ = (spinB?.value ?: 0f) + 18f
                    val phase = driftB?.value ?: 0f
                    translationX = cos(phase) * 13.dp.toPx()
                    translationY = sin(phase) * 13.dp.toPx()
                    val scale = 1.06f - ((breathe?.value ?: 1f) - 0.94f)
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(58.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        )

        // Círculo chico que cruza el medio.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 30.dp, y = (-150).dp)
                .size(86.dp)
                .graphicsLayer {
                    val phase = (driftA?.value ?: 0f) * 1.6f
                    translationX = cos(phase) * 18.dp.toPx()
                    translationY = sin(phase) * 18.dp.toPx()
                    rotationZ = spinB?.value ?: 0f
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)),
        )
    }
}
