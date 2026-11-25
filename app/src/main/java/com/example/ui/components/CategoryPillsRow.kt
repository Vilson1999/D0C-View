package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
        ) + fadeIn(),
        exit = shrinkVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeOut(),
        modifier = modifier
    ) {
        val horizontalArrangement = if (visibleTypes.size <= 3) {
            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(8.dp)
        }

        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
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
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillScale"
    )
    val tintBg = if (isDarkTheme) type.tintDark else type.tintLight
    val containerColor by animateColorAsState(
        targetValue = if (selected) type.pillColor else tintBg,
        label = "pillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else if (isDarkTheme) Color.White.copy(alpha = 0.92f) else type.pillColor,
        label = "pillFg"
    )

    // Outer 48dp touch target box around compact sleek pill
    Box(
        modifier = Modifier
            .height(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("pill_${type.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            border = if (selected) null else BorderStroke(1.dp, type.pillColor.copy(alpha = 0.35f)),
            shadowElevation = if (selected) 3.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = docTypeIcon(type),
                    contentDescription = type.label,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = type.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color.White.copy(alpha = 0.24f) else type.pillColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = count.toString(),
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

fun docTypeIcon(type: DocType): ImageVector = when (type) {
    DocType.PDF -> AppIcons.PictureAsPdf
    DocType.DOC -> AppIcons.Description
    DocType.XML -> AppIcons.Code
    DocType.PPT -> AppIcons.Slideshow
    DocType.TEXT -> AppIcons.Notes
}
