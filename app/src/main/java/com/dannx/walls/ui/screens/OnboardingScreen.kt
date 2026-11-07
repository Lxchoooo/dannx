package com.dannx.walls.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toShape
import kotlinx.coroutines.launch

/**
 * Bienvenida en tres pantallas: qué es DannX, cómo descubrir fondos y cómo
 * aplicarlos. Sale una sola vez la primera vez que se abre la app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Papeles que se sienten tuyos.",
                message = "Fondos curados, ordenados y hechos para que tu teléfono " +
                    "parezca diseñado por vos.",
                bullets = emptyList(),
                icon = null,
            ),
            OnboardingPage(
                title = "Descubrí tu estilo",
                message = "Navegá por categoría o por tag y encontrá el fondo que " +
                    "va con tu día.",
                bullets = listOf(
                    "Categorías y tags, sin ruido",
                    "Lo último queda guardado aunque no haya internet",
                    "Favoritos para volver en un toque",
                ),
                icon = Icons.Rounded.AutoAwesome,
            ),
            OnboardingPage(
                title = "Hacelo tuyo",
                message = "Aplicá, descargá y ordená. Tu pantalla cambia en segundos.",
                bullets = listOf(
                    "Fondo de inicio o de bloqueo, ya recortado",
                    "Descargá a Galería sin perder calidad",
                    "Colecciones para cada mood",
                ),
                icon = Icons.Rounded.Wallpaper,
            ),
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFinish) {
                Text("Saltar", style = MaterialTheme.typography.labelLarge)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            pageSpacing = 18.dp,
            key = { it },
        ) { index ->
            PageContent(
                page = pages[index],
                index = index,
                selected = index == pagerState.currentPage,
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
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "dot",
                )
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
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
                if (isLast) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = if (isLast) "Empezar" else "Siguiente",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private data class OnboardingPage(
    val title: String,
    val message: String,
    val bullets: List<String>,
    val icon: ImageVector?,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageContent(
    page: OnboardingPage,
    index: Int,
    selected: Boolean,
) {
    val accent = when (index) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "iconScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        // Formas expresivas de fondo: forma "cookie" y blob redondeado
        // fuera del marco, con el toque visual de Material 3 Expressive.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-40).dp)
                .size(230.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(accent.copy(alpha = if (index == 0) 0.22f else 0.16f)),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 30.dp)
                .size(160.dp)
                .clip(RoundedCornerShape(56.dp))
                .rotate(18f)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(34.dp))

            if (index == 0) {
                WordmarkRow(accent, iconScale)
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
                            modifier = Modifier
                                .size(52.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            tint = accent,
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
                Text(
                    text = if (index == 1) "DannX" else "DannX",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }

            Spacer(Modifier.height(if (index == 0) 12.dp else 18.dp))
            Text(
                text = page.title,
                style = if (index == 0) {
                    MaterialTheme.typography.displayLarge
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = page.message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            page.bullets.forEach { bullet ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** Marca de la primera pantalla: palabra grande y miniaturas decorativas. */
@Composable
private fun WordmarkRow(accent: androidx.compose.ui.graphics.Color, iconScale: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            alpha = 1f
            scaleX = 0.9f + 0.1f * iconScale
            scaleY = 0.9f + 0.1f * iconScale
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
            ).forEach { (rotation, color, size) ->
                Box(
                    Modifier
                        .size(width = size, height = size * 1.5f)
                        .clip(RoundedCornerShape(22.dp))
                        .rotate(rotation)
                        .background(color),
                )
            }
        }
    }
}
