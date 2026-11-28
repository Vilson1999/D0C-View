package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DocumentItem
import com.example.ui.theme.AppMotion
import com.example.ui.theme.AppMotionPolicy
import com.example.ui.theme.LocalAnimationsEnabled
import com.example.ui.theme.LocalMaterialUiEnabled
import com.example.ui.theme.bouncyPressScale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    document: DocumentItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isDarkTheme: Boolean,
    showDotFolderBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale = AppMotion.rememberBouncyScale(
        pressed = pressed,
        pressedScale = 0.965f,
        dampingRatio = AppMotionPolicy.GENTLE_BOUNCY_DAMPING_RATIO,
        stiffness = Spring.StiffnessMedium,
        label = "cardPressScale"
    )

    val materialUi = LocalMaterialUiEnabled.current
    val cardColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (materialUi) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                document.type.pillColor.copy(alpha = if (isDarkTheme) 0.18f else 0.09f)
            }
        } else {
            if (materialUi) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
        },
        animationSpec = AppMotionPolicy.smoothSpring(animationsEnabled),
        label = "cardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (materialUi) MaterialTheme.colorScheme.primary else document.type.pillColor
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = AppMotionPolicy.smoothSpring(animationsEnabled),
        label = "cardBorder"
    )

    val badgeBg = if (isDarkTheme) document.type.tintDark else document.type.tintLight

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(if (materialUi) 18.dp else 16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("doc_card_${document.name}"),
        shape = RoundedCornerShape(if (materialUi) 18.dp else 16.dp),
        color = cardColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isSelected) 3.dp else if (materialUi) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBg,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val badgeKey = (if (isSelectionMode) 2 else 0) + (if (isSelected) 1 else 0)
                        AnimatedContent(
                            targetState = badgeKey,
                            transitionSpec = { AppMotion.bouncyIconContentTransform(animationsEnabled) },
                            label = "cardBadgeSwitch"
                        ) { state ->
                            val selectionActive = state >= 2
                            val itemSelected = state % 2 == 1
                            if (selectionActive) {
                                Icon(
                                    imageVector = if (itemSelected) AppIcons.checkCircle() else AppIcons.radioButtonUnchecked(),
                                    contentDescription = if (itemSelected) "Selected" else "Not selected",
                                    tint = if (itemSelected) document.type.pillColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(AppIcons.scaledSize(24.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = docTypeIcon(document.type),
                                    contentDescription = document.type.label,
                                    tint = document.type.pillColor,
                                    modifier = Modifier.size(AppIcons.scaledSize(22.dp))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = document.type.pillColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = document.extension.uppercase(),
                            color = document.type.pillColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${document.formattedSize} • ${document.formattedDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (document.isFromDotFolder && showDotFolderBadge) {
                        Surface(
                            shape = CircleShape,
                            color = if (materialUi) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                            }
                        ) {
                            Text(
                                text = "Hidden",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (materialUi) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Box {
                val moreInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = { menuExpanded = true },
                    interactionSource = moreInteraction,
                    modifier = Modifier
                        .bouncyPressScale(moreInteraction, pressedScale = 0.84f)
                        .testTag("doc_more_${document.name}")
                ) {
                    Icon(
                        imageVector = AppIcons.moreVert(),
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DocumentActionDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onRename = onRename,
                    onShare = onShare,
                    onPrint = onPrint,
                    onInfo = onInfo,
                    onDelete = onDelete,
                    tagPrefix = "menu"
                )
            }
        }
    }
}
