package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocType

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

    LaunchedEffect(selectedType, visible, visibleTypes) {
        if (!visible || visibleTypes.isEmpty()) return@LaunchedEffect
        val index = visibleTypes.indexOf(selectedType)
        if (index >= 0) {
            rowState.animateScrollToItem(index)
        }
    }

    AnimatedVisibility(
        visible = visible && visibleTypes.isNotEmpty(),
        enter = expandVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(200)),
        exit = shrinkVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeOut(tween(150)),
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
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 0.dp),
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            selected -> 1.04f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "pillScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 2.4.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillElevation"
    )
    val tintBg = if (isDarkTheme) type.tintDark else type.tintLight
    val containerColor by animateColorAsState(
        targetValue = if (selected) type.pillColor else tintBg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else if (isDarkTheme) Color.White.copy(alpha = 0.92f) else type.pillColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillFg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) type.pillColor else type.pillColor.copy(alpha = 0.35f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
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
                    modifier = Modifier.size(14.72.dp)
                )
                Spacer(modifier = Modifier.width(4.8.dp))
                Text(
                    text = type.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge.copy(
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
                            transitionSpec = {
                                (scaleIn(initialScale = 0.8f) + fadeIn(tween(140)))
                                    .togetherWith(scaleOut(targetScale = 0.8f) + fadeOut(tween(100)))
                            },
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
