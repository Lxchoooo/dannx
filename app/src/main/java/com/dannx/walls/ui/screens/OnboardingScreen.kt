package com.dannx.walls.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.ui.theme.EmphasizedEasing
import com.dannx.walls.ui.theme.LocalReduceMotion
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
fun OnboardingScreen(
    liteMode: Boolean,
    onFinish: () -> Unit,
    initialPage: Int = 0,
) {
    // Lite o animaciones del sistema desactivadas: misma entrada tranquila.
    val motionOff = liteMode || LocalReduceMotion.current
    val pages = remember {
        listOf(
            OnboardingPage(
                title = R.string.onboarding_title_1,
                message = R.string.onboarding_message_1,
                icon = null,
            ),
            OnboardingPage(
                title = R.string.onboarding_title_2,
                message = R.string.onboarding_message_2,
                icon = Icons.Rounded.AutoAwesome,
            ),
            OnboardingPage(
                title = R.string.onboarding_title_3,
                message = R.string.onboarding_message_3,
                icon = Icons.Rounded.Wallpaper,
            ),
        )
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(pages.indices),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }

    // Entrada en cascada: marca, título y mensaje suben a su turno. En lite o
    // con movimiento reducido arranca terminada, sin capturas a medio camino.
    val intro = remember { Animatable(if (motionOff) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motionOff) {
            intro.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 760, easing = EmphasizedEasing),
            )
        }
    }

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
        AnimatedBackdrop(accent = backdropAccent, liteMode = motionOff)

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
                    modifier = Modifier.graphicsLayer {
                        val p = stage(intro.value, 0.15f, 0.6f)
                        alpha = p
                        translationY = (1f - p) * -8.dp.toPx()
                    },
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
                    liteMode = motionOff,
                    intro = intro,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .graphicsLayer {
                        val p = stage(intro.value, 0.4f, 0.9f)
                        alpha = p
                        translationY = (1f - p) * 12.dp.toPx()
                    },
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.indices.forEach { index ->
                    // El ancho sigue al dedo: el punto crece mientras la página
                    // se acerca, sin esperar al asentamiento.
                    val distance = abs(
                        (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction,
                    ).coerceIn(0f, 1f)
                    val presence = 1f - distance
                    val dotWidth = 8.dp + 22.dp * presence
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = dotWidth, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                lerp(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    accents[index],
                                    presence,
                                ),
                            ),
                    )
                }
            }

            val isLast = pagerState.currentPage == pages.lastIndex
            Button(
                onClick = {
                    when {
                        isLast && motionOff -> onFinish()
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
                    .height(56.dp)
                    .graphicsLayer {
                        val p = stage(intro.value, 0.45f, 1f)
                        alpha = p
                        translationY = (1f - p) * 18.dp.toPx()
                    },
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (last) R.string.onboarding_start else R.string.onboarding_next,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Icon(
                            imageVector = if (last) {
                                Icons.Rounded.Check
                            } else {
                                Icons.Rounded.ArrowForward
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
    val icon: ImageVector?,
)

@Composable
private fun PageContent(
    page: OnboardingPage,
    index: Int,
    accent: Color,
    pagerState: androidx.compose.foundation.pager.PagerState,
    liteMode: Boolean,
    intro: Animatable<Float, AnimationVector1D>,
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
                Reveal(intro, start = 0f, end = 0.6f, rise = 14f) {
                    WordmarkRow(accent = accent, liteMode = liteMode)
                }
                Spacer(Modifier.height(28.dp))
            } else {
                Reveal(intro, start = 0.06f, end = 0.62f, rise = 22f) {
                    PageBadge(icon = page.icon, accent = accent, liteMode = liteMode)
                }
                Spacer(Modifier.height(22.dp))
            }

            // Cada capa entra a su turno: la insignia primero, el título
            // después y el mensaje al final, como si se posaran.
            Reveal(intro, start = 0.16f, end = 0.76f, rise = 16f) {
                Text(
                    text = stringResource(page.title),
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(14.dp))
            Reveal(intro, start = 0.3f, end = 0.92f, rise = 12f) {
                Text(
                    text = stringResource(page.message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Envoltorio de entrada: aparece y sube dentro de la ventana que le toca de la
 * animación global. La lectura ocurre en `graphicsLayer`, así que la capa se
 * actualiza sin recomponer el contenido.
 */
@Composable
private fun Reveal(
    progress: Animatable<Float, AnimationVector1D>,
    start: Float,
    end: Float,
    rise: Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.graphicsLayer {
            val p = stage(progress.value, start, end)
            alpha = p
            translationY = (1f - p) * rise.dp.toPx()
        },
    ) {
        content()
    }
}

/**
 * Insignia de las páginas de descubrimiento: tarjeta con gradiente del acento,
 * borde de luz y una flotación lenta. En lite queda quieta y sin sombra de color.
 */
@Composable
private fun PageBadge(icon: ImageVector?, accent: Color, liteMode: Boolean) {
    val float = if (liteMode) {
        null
    } else {
        rememberInfiniteTransition(label = "pageBadge").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5_400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pageBadgeFloat",
        )
    }

    Box(
        modifier = Modifier
            .size(112.dp)
            .graphicsLayer {
                val t = float?.value ?: 0.5f
                translationY = -(t - 0.5f) * 10.dp.toPx()
                rotationZ = (t - 0.5f) * 3f
            }
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(42.dp),
                ambientColor = accent.copy(alpha = 0.5f),
                spotColor = accent.copy(alpha = 0.7f),
            )
            .clip(RoundedCornerShape(42.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.26f),
                        accent.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.55f),
                        Color.Transparent,
                        accent.copy(alpha = 0.22f),
                    ),
                ),
                shape = RoundedCornerShape(42.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        )
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = accent,
            )
        }
    }
}

/**
 * Miniaturas del logo. En claro son los containers pastel; en oscuro esos
 * containers son marrones y verdes apagados, así que se usan los acentos
 * claros del tema, que conservan el aire pastel sobre el fondo negro.
 */
@Composable
private fun logoColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) {
        listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    } else {
        listOf(
            scheme.primaryContainer,
            scheme.tertiaryContainer,
            scheme.secondaryContainer,
        )
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = accent,
            modifier = Modifier.graphicsLayer {
                val t = drift?.value ?: 0f
                translationY = -t * 4.dp.toPx()
                val scale = 0.995f + 0.012f * t
                scaleX = scale
                scaleY = scale
            },
        )
        Spacer(Modifier.height(24.dp))
        val colors = logoColors()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEachIndexed { index, color ->
                LogoCard(
                    color = color,
                    modifier = Modifier
                        .size(width = 64.dp, height = 96.dp)
                        .graphicsLayer {
                            val t = drift?.value ?: 0f
                            // Cada tarjeta flota con su propio desfase para que
                            // el grupo no se mueva como un bloque rígido.
                            val bob = sin((t * Math.PI + index * 0.9f).toFloat())
                            rotationZ = -8f + index * 8f + bob * 2.4f
                            translationY = bob * 5.dp.toPx()
                            val scale = 1f + bob * 0.015f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
}

/**
 * Tarjeta del logo: gradiente suave de su propio color, filo de luz y una
 * sombra teñida. La usan la portada y el remate para que el cierre se sienta
 * como la misma marca.
 */
@Composable
private fun LogoCard(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = color.copy(alpha = 0.45f),
                spotColor = color.copy(alpha = 0.6f),
            )
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(color, Color.White, 0.22f),
                        lerp(color, Color.Black, 0.05f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.24f),
                shape = RoundedCornerShape(22.dp),
            ),
    )
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
            dampingRatio = 0.7f,
            stiffness = 170f,
        ),
        label = "finishStage",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        // Halo del acento: se abre detrás de la marca y da paso a la app.
        Box(
            Modifier
                .size(300.dp)
                .graphicsLayer {
                    alpha = progress * 0.9f
                    val scale = 0.55f + 0.65f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.3f), Color.Transparent),
                    ),
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = accent,
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 10.dp.toPx()
                },
            )
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                logoColors().forEachIndexed { index, color ->
                    LogoCard(
                        color = color,
                        modifier = Modifier
                            .size(width = 64.dp, height = 96.dp)
                            .graphicsLayer {
                                alpha = progress
                                rotationZ = (index - 1) * 12f * progress
                                translationY = (1f - progress) * (14 + index * 8).dp.toPx()
                                val scale = 0.82f + 0.18f * progress
                                scaleX = scale
                                scaleY = scale
                            },
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
        // Velo del acento de la página: el fondo cambia de temperatura al
        // deslizar, sin competir con el texto.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                    ),
                ),
        )

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

/**
 * Progreso de una etapa concreta dentro de una animación global, en 0..1.
 * Permite escalonar varias capas con un solo valor.
 */
private fun stage(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)
