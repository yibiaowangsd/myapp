package com.personal.wo.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.personal.wo.core.*
import java.time.LocalDate
import kotlin.math.abs

private fun axisAt(x: Float, width: Float, y: Float, arc: Float): Float {
    val u = (x - width / 2f) / (width / 2f).coerceAtLeast(1f)
    return y + arc * (1f - u * u)
}

/** A shared scene prevents the pager's individual clipping rectangles cutting off a side. */
@Composable internal fun CylinderPages(
    day: DayView, chapters: List<DayChapter>, pager: PagerState,
    activate: (Int) -> Unit, hasNextDate: Boolean, compact: Boolean, modifier: Modifier
) {
    val latest by rememberUpdatedState(chapters)
    val activateLatest by rememberUpdatedState(activate)
    val density = LocalDensity.current
    val largeType = density.fontScale >= 1.3f
    val faces = remember(pager) { mutableMapOf<Int, LayoutCoordinates>() }
    val scene = remember(pager) { arrayOfNulls<LayoutCoordinates>(1) }
    BoxWithConstraints(
        modifier.testTag("drum-scene").clipToBounds()
            .onGloballyPositioned { scene[0] = it }
            .pointerInput(pager) {
                // Native Pager consumes a drag and cancels this tap recognizer.
                // Hit-test the actual transformed card, not an invisible page rectangle.
                detectTapGestures(onTap = { point ->
                    val root = scene[0]
                    if (root != null && root.isAttached) {
                        val position = pager.currentPage + pager.currentPageOffsetFraction
                        val hit = faces.entries
                            .filter { it.key in 0..latest.size + 1 && it.value.isAttached }
                            .sortedByDescending { drumPose(it.key - position).depth }
                            .firstOrNull { (index, coordinates) ->
                                if (drumPose(index - position).alpha < .12f) false else {
                                    val local = coordinates.localPositionOf(root, point)
                                    local.x >= 0f && local.y >= 0f &&
                                        local.x < coordinates.size.width && local.y < coordinates.size.height
                                }
                            }
                        hit?.let { activateLatest(it.key) }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        // A short landscape window must not squeeze two rows of text into unusable cards.
        val staggered = !compact && maxHeight >= if (largeType) 360.dp else 280.dp
        val width = (maxWidth * if (staggered) .58f else .68f).coerceAtMost(320.dp)
        val availableHeight = if (staggered) (maxHeight - 64.dp) / 2 else maxHeight - 56.dp
        val height = availableHeight.coerceAtMost(if (largeType) 232.dp else 204.dp).coerceAtLeast(32.dp)
        val sceneWidth = with(density) { maxWidth.toPx() }
        val sceneHeight = with(density) { maxHeight.toPx() }
        val widthPx = with(density) { width.toPx() }
        val heightPx = with(density) { height.toPx() }
        val gap = with(density) { (if (staggered) 18.dp else 12.dp).toPx() }
        val axisY = if (staggered) sceneHeight / 2f else sceneHeight - with(density) { 20.dp.toPx() }
        val arc = with(density) { 6.dp.toPx() }
        val ink = MaterialTheme.colorScheme.onSurface
        val shape = RoundedCornerShape(22.dp)
        val visible by remember(pager, chapters.size) {
            derivedStateOf { (pager.currentPage - 3..pager.currentPage + 3).filter { it in 0..chapters.size + 1 } }
        }
        Canvas(Modifier.fillMaxSize().testTag("wheel-time-axis")) {
            val rail = Path().apply {
                moveTo(0f, axisY)
                quadraticBezierTo(size.width / 2f, axisY + 2f * arc, size.width, axisY)
            }
            drawPath(rail, ink.copy(alpha = .18f), style = Stroke(1.dp.toPx()))
            val position = pager.currentPage + pager.currentPageOffsetFraction
            visible.forEach { index ->
                val pose = drumPose(index - position)
                if (pose.alpha > 0f) {
                    val x = size.width / 2f + pose.x * size.width
                    val y = axisAt(x, size.width, axisY, arc)
                    val lane = if (staggered) drumLane(index) else -1f
                    val end = y + lane * gap * (.78f + .22f * pose.depth)
                    drawLine(ink.copy(alpha = .24f * pose.alpha), Offset(x, y), Offset(x, end), 1.dp.toPx())
                    drawCircle(ink.copy(alpha = .055f * pose.alpha), (6f + 3f * pose.depth).dp.toPx(), Offset(x, y))
                    drawCircle(ink.copy(alpha = .68f * pose.alpha), (2f + pose.depth).dp.toPx(), Offset(x, y))
                }
            }
        }
        visible.forEach { index ->
            key(chapters.getOrNull(index - 1)?.id ?: "edge-$index") {
                DisposableEffect(pager, index) { onDispose { faces.remove(index) } }
                val visual = Modifier.width(width).height(height)
                    .zIndex(1f / (1f + abs(index - pager.currentPage)))
                    .graphicsLayer {
                        // Read continuous state in the layer: text and image layout do not
                        // need to run on every frame of a drag or inertial settle.
                        val pose = drumPose(index - pager.currentPage - pager.currentPageOffsetFraction)
                        val x = sceneWidth / 2f + pose.x * sceneWidth
                        val lane = if (staggered) drumLane(index) else -1f
                        val y = axisAt(x, sceneWidth, axisY, arc) +
                            lane * (heightPx * pose.scale / 2f + gap * (.78f + .22f * pose.depth))
                        translationX = pose.x * sceneWidth
                        translationY = y - sceneHeight / 2f
                        rotationY = pose.rotation
                        // Camera distance is in pixels and must comfortably exceed a card.
                        cameraDistance = widthPx * 8f
                        scaleX = pose.scale
                        scaleY = pose.scale
                        alpha = pose.alpha
                        shadowElevation = (1f + 5f * pose.depth * pose.depth).dp.toPx()
                        this.shape = shape
                        clip = true
                    }
                    .onGloballyPositioned { faces[index] = it }
                    .drawWithContent {
                        drawContent()
                        val pose = drumPose(index - pager.currentPage - pager.currentPageOffsetFraction)
                        // A restrained grazing highlight, not a new flash on selection.
                        val start = (.5f + pose.rotation / 100f) * size.width
                        drawRect(Brush.linearGradient(
                            listOf(Color.White.copy(alpha = .025f + .035f * pose.depth), Color.Transparent),
                            start = Offset(start, 0f), end = Offset(size.width - start, size.height)
                        ))
                    }
                    .clearAndSetSemantics { testTag = "wheel-face-$index" }
                EventCardTheme {
                    val chapter = chapters.getOrNull(index - 1)
                    if (chapter != null) WheelChapterFace(chapter, day.zoneId, visual, height.value, largeType)
                    else WheelDateFace(day, index == 0, hasNextDate, visual, height.value)
                }
            }
        }
        // One native gesture owner retains velocity-based decay, interruption, key
        // navigation and accessibility. Real taps are resolved by the parent scene.
        val step = maxWidth * .66f
        HorizontalPager(
            state = pager, pageSize = PageSize.Fixed(step),
            contentPadding = PaddingValues(horizontal = (maxWidth - step) / 2),
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager, pagerSnapDistance = PagerSnapDistance.atMost(3),
                snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 240f, visibilityThreshold = .1f)
            ),
            key = { index -> latest.getOrNull(index - 1)?.id ?: if (index == 0) "previous-day" else "end-day" },
            modifier = Modifier.fillMaxSize().zIndex(2f).testTag("experience-drum")
        ) { index ->
            val chapter = latest.getOrNull(index - 1)
            val center = index == pager.currentPage
            Box(Modifier.fillMaxSize().then(if (center) Modifier.semantics {
                testTag = "chapter-open"
                contentDescription = chapter?.let {
                    "${it.part.label}，${partRange(it.part,day.zoneId)}。${it.summary}" +
                        it.apps.joinToString("，") { a -> "${a.episode.title}${durationText(a.durationMs)}" } +
                        if (it.photos.isNotEmpty()) "，${it.photos.size}项影像" else ""
                } ?: if (index == 0) "回看前一天" else if (hasNextDate) "继续到后一天" else "今天，先到这里"
                if (chapter != null || index == 0 || hasNextDate) {
                    onClick(label = if (chapter != null) "查看这个大事件的全部片段" else "查看日期") {
                        activateLatest(index); true
                    }
                }
            } else Modifier.clearAndSetSemantics { }))
        }
    }
}

@Composable private fun WheelChapterFace(chapter: DayChapter, zone: String, modifier: Modifier, height: Float, largeType: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    val roomy = height >= if (largeType) 180f else 138f
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ink.copy(alpha = .12f))) {
        Column(Modifier.fillMaxSize().padding(if (roomy) 14.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text((if (chapter.part.label.endsWith("（推测）")) "推测 · " else "") + partRange(chapter.part, zone),
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(chapter.part.label.removeSuffix("（推测）"), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium, maxLines = if (roomy && chapter.photos.isEmpty()) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (roomy) {
                if (chapter.photos.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chapter.photos.take(2).forEach { photo ->
                            PhotoThumbnail(photo.uri, Modifier.weight(1f).fillMaxHeight(), crop = true)
                        }
                    }
                } else {
                    Text(chapter.summary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (largeType) 2 else 3, overflow = TextOverflow.Ellipsis)
                }
                Text(if (chapter.photos.isNotEmpty()) "${chapter.photos.size}项影像 · ${chapter.part.episodes.size}个片段"
                    else "${durationText(chapter.durationMs)} · ${chapter.part.episodes.size}个片段",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun WheelDateFace(day: DayView, previous: Boolean, hasNext: Boolean, modifier: Modifier, height: Float) {
    val date = LocalDate.parse(day.dayDate).plusDays(if (previous) -1 else 1)
    val end = !previous && !hasNext
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(if (end) "今天，先到这里" else "${date.monthValue}月${date.dayOfMonth}日",
                style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (height >= 100f) Text(if (end) "截至 ${clock(day.asOfMs,day.zoneId)}" else if (previous) "回看前一天" else "继续到后一天",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
            if (height >= 170f) Text(if (end) "新的经历，会继续浮现。" else "轻触翻开这一天",
                Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
