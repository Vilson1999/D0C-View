package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocType
import com.example.ui.theme.AppMotion
import com.example.ui.theme.AppMotionPolicy
import com.example.ui.theme.LocalAnimationsEnabled

@Composable
fun CategoryPillsRow(
    selectedType: DocType,
    visibleTypes: List<DocType>,
    counts: Map<DocType, Int>,
    visible: Boolean,
    isDarkTheme: Boolean,
    onSelectType: (DocType) -> Unit,
    modifier: Modifier = Modifier
) {
    val rowState = rememberLazyListState()
    val animationsEnabled = LocalAnimationsEnabled.current

    LaunchedEffect(selectedType, visible, visibleTypes, animationsEnabled) {
        if (!visible || visibleTypes.isEmpty()) return@LaunchedEffect
        val index = visibleTypes.indexOf(selectedType)
        if (index >= 0) {
            if (animationsEnabled) {
                rowState.animateScrollToItem(index)
            } else {
                rowState.scrollToItem(index)
            }
        }
    }

    AnimatedVisibility(
        visible = visible && visibleTypes.isNotEmpty(),
        enter = if (animationsEnabled) {
            expandVertically(
                animationSpec = AppMotionPolicy.smoothSpring(
                    animationsEnabled = true,
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(200, easing = AppMotionPolicy.SmoothEasing))
        } else {
            EnterTransition.None
        },
        exit = if (animationsEnabled) {
            shrinkVertically(
                animationSpec = tween(180, easing = AppMotionPolicy.SmoothEasing)
            ) + fadeOut(tween(150))
        } else {
            ExitTransition.None
        },
        modifier = modifier
    ) {
        val horizontalArrangement = if (visibleTypes.size <= 3) {
            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(8.dp)
        }

        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(visibleTypes, key = { it.name }) { type ->
                val selected = type == selectedType
                val count = counts[type] ?: 0
                CategoryPill(
                    type = type,
                    count = count,
                    selected = selected,
                    isDarkTheme = isDarkTheme,
                    onClick = { onSelectType(type) }
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(
    type: DocType,
    count: Int,
    selected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale = AppMotion.rememberBouncyScale(
        pressed = pressed,
        selected = selected,
        pressedScale = 0.88f,
        selectedScale = 1.06f,
        dampingRatio = AppMotionPolicy.BOUNCY_DAMPING_RATIO,
        stiffness = Spring.StiffnessMedium,
        label = "pillScale"
    )
    val iconScale = AppMotion.rememberBouncyScale(
        pressed = pressed,
        selected = selected,
        pressedScale = 0.84f,
        selectedScale = 1.12f,
        dampingRatio = 0.44f,
        stiffness = Spring.StiffnessMedium,
        label = "pillIconScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 2.4.dp else 0.dp,
        animationSpec = AppMotionPolicy.smoothSpring(animationsEnabled),
        label = "pillElevation"
    )
    val tintBg = if (isDarkTheme) type.tintDark else type.tintLight
    val containerColor by animateColorAsState(
        targetValue = if (selected) type.pillColor else tintBg,
        animationSpec = AppMotionPolicy.smoothSpring(animationsEnabled),
        label = "pillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else if (isDarkTheme) Color.White.copy(alpha = 0.92f) else type.pillColor,
        animationSpec = AppMotionPolicy.smoothSpring(animationsEnabled),
        label = "pillFg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) type.pillColor else type.pillColor.copy(alpha = 0.35f),
        animationSpec = AppMotionPolicy.smoothSpring(animationsEnabled),
        label = "pillBorder"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .testTag("pill_${type.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(0.8.dp, borderColor),
            shadowElevation = elevation
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.6.dp, vertical = 5.6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = docTypeIcon(type),
                    contentDescription = type.label,
                    tint = contentColor,
                    modifier = Modifier
                        .size(AppIcons.scaledSize(17.8112.dp))
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                Spacer(modifier = Modifier.width(4.8.dp))
                Text(
                    text = type.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.4.sp,
                        lineHeight = 14.4.sp
                    )
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.width(4.8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color.White.copy(alpha = 0.24f) else type.pillColor.copy(alpha = 0.16f)
                    ) {
                        AnimatedContent(
                            targetState = count,
                            transitionSpec = { AppMotion.bouncyIconContentTransform(animationsEnabled) },
                            label = "pillCount"
                        ) { targetCount ->
                            Text(
                                text = targetCount.toString(),
                                color = contentColor,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.8.sp,
                                    lineHeight = 11.2.sp
                                ),
                                modifier = Modifier.padding(horizontal = 4.8.dp, vertical = 0.8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun docTypeIcon(type: DocType): ImageVector = when (type) {
    DocType.PDF -> AppIcons.pdf()
    DocType.DOC -> AppIcons.doc()
    DocType.EXCEL -> AppIcons.excel()
    DocType.PPT -> AppIcons.ppt()
    DocType.TEXT -> AppIcons.textDoc()
}
