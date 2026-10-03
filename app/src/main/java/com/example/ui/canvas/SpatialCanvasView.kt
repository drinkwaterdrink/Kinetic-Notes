package com.example.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NoteEntity
import com.example.data.local.NoteLinkEntity
import com.example.data.local.NoteType
import com.example.ui.theme.KineticDarkBackground
import com.example.ui.theme.KineticDarkSurfaceVariant
import com.example.ui.theme.KineticPrimary
import com.example.ui.theme.KineticSecondary
import com.example.ui.theme.KineticTextMuted
import com.example.ui.theme.KineticTextPrimary
import com.example.ui.theme.KineticTextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SpatialCanvasView(
    notes: List<NoteEntity>,
    links: List<NoteLinkEntity>,
    zoom: Float,
    panX: Float,
    panY: Float,
    isLinkingMode: Boolean,
    linkSourceNoteId: String?,
    isSnapToGrid: Boolean,
    onTransformChange: (panDeltaX: Float, panDeltaY: Float, zoomChange: Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onCardMove: (noteId: String, newX: Float, newY: Float) -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
    onFocusClick: (NoteEntity) -> Unit,
    onCardTapInLinkingMode: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // In-memory reactive positions for 60/120fps fluid dragging without database thrash
    val cardPositions = remember { mutableStateMapOf<String, Offset>() }

    // Sync database positions when notes list changes from outside (e.g. initial load or auto-sort)
    LaunchedEffect(notes) {
        notes.forEach { note ->
            if (!cardPositions.containsKey(note.id)) {
                cardPositions[note.id] = Offset(note.x, note.y)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(KineticDarkBackground)
            // Pan & pinch-to-zoom on background canvas
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomFactor, _ ->
                    onTransformChange(pan.x, pan.y, zoomFactor)
                }
            }
            .testTag("spatial_canvas")
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Architectural Dot-Matrix Canvas Grid Background
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panX
                    translationY = panY
                    scaleX = zoom
                    scaleY = zoom
                }
        ) {
            val gridSize = 26f
            val startX = -panX / zoom - 200f
            val endX = (-panX + widthPx) / zoom + 200f
            val startY = -panY / zoom - 200f
            val endY = (-panY + heightPx) / zoom + 200f

            val firstGridX = (startX / gridSize).toInt() * gridSize
            val firstGridY = (startY / gridSize).toInt() * gridSize

            var currX = firstGridX
            while (currX <= endX) {
                var currY = firstGridY
                while (currY <= endY) {
                    drawCircle(
                        color = Color(0x28FFFFFF),
                        radius = 1.3f,
                        center = Offset(currX, currY)
                    )
                    currY += gridSize
                }
                currX += gridSize
            }
        }

        // 2. Dashed Bézier Link Threads between connected notes (dynamically routes using real-time drag positions)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panX
                    translationY = panY
                    scaleX = zoom
                    scaleY = zoom
                }
        ) {
            val cardW = 210f
            val cardH = 140f

            links.forEach { link ->
                val sourcePos = cardPositions[link.sourceId]
                val targetPos = cardPositions[link.targetId]

                if (sourcePos != null && targetPos != null) {
                    val sCenter = Offset(sourcePos.x + cardW / 2f, sourcePos.y + cardH / 2f)
                    val tCenter = Offset(targetPos.x + cardW / 2f, targetPos.y + cardH / 2f)

                    val sAnchor = if (sCenter.x < tCenter.x) Offset(sourcePos.x + cardW, sCenter.y) else Offset(sourcePos.x, sCenter.y)
                    val tAnchor = if (sCenter.x < tCenter.x) Offset(targetPos.x, tCenter.y) else Offset(targetPos.x + cardW, tCenter.y)

                    val dx = abs(tAnchor.x - sAnchor.x) * 0.5f
                    val c1 = if (sAnchor.x < tAnchor.x) Offset(sAnchor.x + dx, sAnchor.y) else Offset(sAnchor.x - dx, sAnchor.y)
                    val c2 = if (sAnchor.x < tAnchor.x) Offset(tAnchor.x - dx, tAnchor.y) else Offset(tAnchor.x + dx, tAnchor.y)

                    val path = Path().apply {
                        moveTo(sAnchor.x, sAnchor.y)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, tAnchor.x, tAnchor.y)
                    }

                    val threadColor = try {
                        Color(android.graphics.Color.parseColor(link.colorHex))
                    } catch (e: Exception) {
                        KineticSecondary
                    }

                    // Dashed bezier curve
                    drawPath(
                        path = path,
                        color = threadColor.copy(alpha = 0.85f),
                        style = Stroke(
                            width = 2.2f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    )

                    // Anchors
                    drawCircle(color = threadColor, radius = 4f, center = sAnchor)
                    drawCircle(color = threadColor, radius = 4f, center = tAnchor)
                }
            }
        }

        // 3. Movable Spatial Cards in World Space
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panX
                    translationY = panY
                    scaleX = zoom
                    scaleY = zoom
                }
        ) {
            notes.forEach { note ->
                val currentPos = cardPositions[note.id] ?: Offset(note.x, note.y)
                val isSelectedForLinking = isLinkingMode && linkSourceNoteId == note.id

                ReferenceNoteCardItem(
                    note = note,
                    currentPos = currentPos,
                    zoom = zoom,
                    isSelectedForLinking = isSelectedForLinking,
                    onCardClick = {
                        if (isLinkingMode) {
                            onCardTapInLinkingMode(note.id)
                        } else {
                            onNoteClick(note)
                        }
                    },
                    onFocusClick = { onFocusClick(note) },
                    onConnectClick = { onCardTapInLinkingMode(note.id) },
                    onDeleteClick = { onDeleteNote(note.id) },
                    onDragMove = { dx, dy ->
                        if (!isLinkingMode) {
                            val nextX = currentPos.x + dx
                            val nextY = currentPos.y + dy
                            cardPositions[note.id] = Offset(nextX, nextY)
                        }
                    },
                    onDragComplete = {
                        if (!isLinkingMode) {
                            val finalPos = cardPositions[note.id] ?: currentPos
                            val finalX = if (isSnapToGrid) (Math.round(finalPos.x / 20f) * 20).toFloat() else finalPos.x
                            val finalY = if (isSnapToGrid) (Math.round(finalPos.y / 20f) * 20).toFloat() else finalPos.y
                            cardPositions[note.id] = Offset(finalX, finalY)
                            onCardMove(note.id, finalX, finalY)
                        }
                    }
                )
            }
        }

        // 4. Floating Zoom HUD Pill on Bottom-Left - elevated with 104.dp padding to prevent ANY overlap with Ask Gemini
        Surface(
            color = Color(0xFF131722).copy(alpha = 0.94f),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x28FFFFFF)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 104.dp)
                .testTag("zoom_hud_capsule")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = KineticTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "${(zoom * 100).roundToInt()}%",
                    color = KineticSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = KineticTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .width(1.dp)
                        .background(Color(0x28FFFFFF))
                        .padding(horizontal = 2.dp)
                )

                Text(
                    text = "Reset",
                    color = KineticTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onResetZoom() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ReferenceNoteCardItem(
    note: NoteEntity,
    currentPos: Offset,
    zoom: Float,
    isSelectedForLinking: Boolean,
    onCardClick: () -> Unit,
    onFocusClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDragMove: (dx: Float, dy: Float) -> Unit,
    onDragComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(note.colorHex))
    } catch (e: Exception) {
        KineticPrimary
    }

    var totalDragDist by remember { mutableFloatStateOf(0f) }

    Surface(
        color = Color(0xFF131722).copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelectedForLinking) 2.dp else 1.dp,
            color = if (isSelectedForLinking) KineticSecondary else Color(0x22FFFFFF)
        ),
        shadowElevation = 8.dp,
        modifier = modifier
            .offset { IntOffset(currentPos.x.roundToInt(), currentPos.y.roundToInt()) }
            .width(210.dp)
            .pointerInput(note.id, zoom) {
                detectDragGestures(
                    onDragStart = {
                        totalDragDist = 0f
                    },
                    onDragEnd = {
                        if (totalDragDist < 10f) {
                            onCardClick()
                        } else {
                            onDragComplete()
                        }
                    },
                    onDragCancel = {
                        onDragComplete()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x / zoom
                        val dy = dragAmount.y / zoom
                        totalDragDist += abs(dragAmount.x) + abs(dragAmount.y)
                        onDragMove(dx, dy)
                    }
                )
            }
            .testTag("canvas_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header: Category Pill with Glowing Dot + Connect + Archive
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category Tag Pill
                Surface(
                    color = Color(0xFF0F131D),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = note.tag,
                            color = KineticTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Connect Thread Button
                IconButton(
                    onClick = onConnectClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Connect",
                        tint = KineticTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Delete / Archive Button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Archive",
                        tint = KineticTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = note.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Body Preview based on Note Type
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                when (note.type) {
                    NoteType.CHECKLIST -> {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "PROGRESS",
                                    color = KineticTextMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "4/4 (100%)",
                                    color = Color(0xFF10B981),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { 1.0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF10B981),
                                trackColor = Color(0x20FFFFFF),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Minimap viewport radar sync",
                                color = KineticTextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    NoteType.CODE -> {
                        Surface(
                            color = Color(0xFF090D15),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x18FFFFFF)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text(
                                    text = note.codeSnippet ?: "const midX = (x1 + x2) / 2;",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    else -> {
                        if (note.isLocked) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted",
                                    tint = KineticTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Encrypted Note",
                                    color = KineticTextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Text(
                                text = note.content.ifBlank { "Tap card to edit notes, write markdown, and [[Link]]..." },
                                color = KineticTextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Timestamp + FOCUS ⛶ Trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0x10FFFFFF),
                        shape = RoundedCornerShape(0.dp)
                    )
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "2h ago",
                    color = KineticTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.clickable { onFocusClick() }
                ) {
                    Text(
                        text = "FOCUS ⛶",
                        color = KineticPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
