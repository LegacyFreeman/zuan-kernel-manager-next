/*
 * Versi 2 untuk bereksperimen.
 */
package com.zuan.kernelmanager.next.ui.navigation

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.settings.NavLabelState
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

val ContinuousCapsule2: Shape = RoundedCornerShape(100) 

internal val LocalLiquidBottomTabScale2 = staticCompositionLocalOf { { 1f } }

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun LiquidNavigationBar2(
    navController: NavController,
    hazeState: HazeState,
    labelState: NavLabelState // <-- Menerima state label
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavigationRoute.Overall,
        NavigationRoute.Dashboard,
        NavigationRoute.SoC,
        NavigationRoute.About
    )

    val selectedIndex = items.indexOfFirst { it.route == currentDestination?.route }.takeIf { it != -1 } ?: 0
    val backdrop = rememberLayerBackdrop()

    val onNavigate: (Int) -> Unit = { index ->
        if (index != selectedIndex) {
            navController.navigate(items[index].route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val commonModifier = Modifier
        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
        .fillMaxWidth()
        .height(80.dp)

    Box(contentAlignment = Alignment.BottomCenter) {
        
        Box(
            modifier = commonModifier
                .clip(ContinuousCapsule2) 
                .zkmHazeEffect(
                    state = hazeState,
                    style = HazeMaterials.regular() 
                ) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 0.1f,
                        endIntensity = 1f,
                        preferPerformance = true
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        forceInvalidateOnPreDraw = true
                    }
                }
        )

        LiquidBottomTabsImpl2(
            selectedIndex = selectedIndex, 
            onTabSelected = onNavigate, 
            backdrop = backdrop,
            tabsCount = items.size,
            modifier = commonModifier
        ) {
            items.forEachIndexed { index, item ->
                
                // Menentukan apakah teks harus tampil atau tidak
                val isSelected = selectedIndex == index
                val showLabel = when (labelState) {
                    NavLabelState.ALWAYS_SHOW -> true
                    NavLabelState.ALWAYS_HIDE -> false
                    NavLabelState.ONLY_SELECTED -> isSelected
                }
                
                // Mendapatkan nama string resource
                val labelText = when(item) {
                    NavigationRoute.Overall -> stringResource(R.string.nav_home)
                    NavigationRoute.Dashboard -> stringResource(R.string.nav_home)
                    NavigationRoute.SoC -> stringResource(R.string.nav_soc)
                    NavigationRoute.About -> stringResource(R.string.nav_about)
                    else -> ""
                }

                LiquidBottomTab2(
                    onClick = { onNavigate(index) } 
                ) {
                     Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Render teks jika diizinkan
                    if (showLabel) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = labelText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidBottomTabsImpl2(
    selectedIndex: Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    
    val containerColor = if (isLightTheme) {
        Color.White.copy(alpha = 0.5f) 
    } else {
        Color.Black.copy(alpha = 0.5f)
    }

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier, 
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(), 
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (currentIndex != targetIndex) {
                        currentIndex = targetIndex
                        onTabSelected(targetIndex)
                    }
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        
        LaunchedEffect(selectedIndex) {
            if (currentIndex != selectedIndex) {
                currentIndex = selectedIndex
                dampedDragAnimation.animateToValue(selectedIndex.toFloat())
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule2 },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale; scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .fillMaxHeight() 
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale2 provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f) 
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule2 },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier) 
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule2 },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                    shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8f.dp * dampedDragAnimation.pressProgress, alpha = dampedDragAnimation.pressProgress) },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(72.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

@Composable
fun RowScope.LiquidBottomTab2(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale2.current
    Column(
        modifier
            .clip(ContinuousCapsule2)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s; scaleY = s
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
