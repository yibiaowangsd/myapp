package com.personal.wo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val welcomeInk = Ink
private val welcomePaper = WoPaper
private val welcomeLime = WoMint

@Composable
fun WelcomeScreen(onPhotos: () -> Unit, onSkip: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = welcomeInk) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp).testTag("public-welcome")
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(welcomeLime),
                    contentAlignment = Alignment.Center
                ) { Text("我", color = welcomeInk, fontWeight = FontWeight.Black, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Text("WO / LIFE IN FRAMES", color = welcomePaper.copy(alpha = .65f),
                    fontSize = 11.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(34.dp))
            Text("让今天\n成为回忆。", color = welcomePaper, fontSize = 45.sp,
                lineHeight = 55.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(15.dp))
            Text("你负责生活，\n我把散落的瞬间，连成你的一天。", color = welcomePaper.copy(alpha = .7f),
                fontSize = 16.sp, lineHeight = 26.sp)
            WelcomeFrames(Modifier.fillMaxWidth().height(225.dp).padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(welcomeLime, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text("从相册开始，找回最近 30 天", color = welcomeLime,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Text("照片按时间自动归档，随时回看、搜索和修正。生活记录保存在本机，无需注册。",
                color = welcomePaper.copy(alpha = .65f), fontSize = 14.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(27.dp))
            Button(
                onClick = onPhotos, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("welcome-photos"),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(containerColor = welcomeLime, contentColor = welcomeInk)
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(21.dp))
                Spacer(Modifier.width(12.dp))
                Text("从照片开始", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowForward, null, Modifier.size(21.dp))
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("welcome-skip")) {
                Text("先进去看看", color = welcomePaper.copy(alpha = .8f), fontSize = 14.sp)
            }
            Text("你可以只开放部分照片。其他来源，等需要时再连接。",
                color = welcomePaper.copy(alpha = .45f), fontSize = 11.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
    }
}

/** Abstract cover artwork: deliberately no invented personal photographs or events. */
@Composable
private fun WelcomeFrames(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * .79f
            drawLine(welcomePaper.copy(alpha = .18f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            repeat(25) { i ->
                val x = size.width * i / 24f
                drawLine(welcomePaper.copy(alpha = .22f), Offset(x, y + 7.dp.toPx()),
                    Offset(x, y + if (i % 6 == 0) 16.dp.toPx() else 11.dp.toPx()), 1.dp.toPx())
            }
        }
        Box(Modifier.offset(x = (-68).dp, y = 10.dp).size(118.dp, 145.dp)
            .graphicsLayer { rotationZ = -14f }.clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF647B6A))) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFFCEE2B4), radius = size.width * .23f,
                    center = Offset(size.width * .35f, size.height * .33f))
                val hill = Path().apply {
                    moveTo(0f, size.height * .72f)
                    quadraticBezierTo(size.width * .7f, size.height * .2f, size.width, size.height * .65f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(hill, Color(0xFF2F4B3C))
            }
        }
        Box(Modifier.offset(x = 72.dp, y = 5.dp).size(113.dp, 145.dp)
            .graphicsLayer { rotationZ = 16f }.clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFB4AED8), Color(0xFF6E6B88))))) {
            Canvas(Modifier.fillMaxSize()) {
                repeat(4) { i ->
                    drawRect(Color(0xFF33394D).copy(alpha = .65f),
                        topLeft = Offset(size.width * (i * .28f - .04f), size.height * (.65f - i * .1f)),
                        size = Size(size.width * .2f, size.height))
                }
            }
        }
        Box(Modifier.offset(y = (-7).dp).size(135.dp, 168.dp)
            .graphicsLayer { rotationZ = 2f; shadowElevation = 16.dp.toPx() }
            .clip(RoundedCornerShape(14.dp)).background(welcomePaper).padding(7.dp)) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight(.79f).clip(RoundedCornerShape(8.dp))) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFE5B67A), Color(0xFFEDCC9A))))
                drawCircle(Color(0xFFFFF2CA), radius = size.width * .23f,
                    center = Offset(size.width * .68f, size.height * .34f))
                val land = Path().apply {
                    moveTo(0f, size.height * .77f)
                    quadraticBezierTo(size.width * .3f, size.height * .42f, size.width * .65f, size.height * .78f)
                    quadraticBezierTo(size.width * .85f, size.height * .96f, size.width, size.height * .67f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(land, Color(0xFF8D6E45))
            }
            Text("每一刻，都值得留下", color = welcomeInk, fontSize = 9.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
        }
    }
}
