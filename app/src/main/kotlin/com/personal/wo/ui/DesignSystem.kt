package com.personal.wo.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import java.time.*
import java.time.format.DateTimeFormatter

val Ink = Color(0xFF27273F)
val Violet = Color(0xFF6251C7)
val laneColors = listOf(Color(0xFF277767), Color(0xFFBD682C), Violet, Color(0xFFB15177))
@Composable fun laneTint(lane:Lane):Color = if(MaterialTheme.colorScheme.background.luminance()<.15f)
    listOf(Color(0xFF90D5C4),Color(0xFFFFBE8F),Color(0xFFC8BCFF),Color(0xFFF0ACCA))[lane.ordinal]
    else laneColors[lane.ordinal]
fun laneName(l: Lane) = listOf("足迹", "活动", "应用", "生活")[l.ordinal]
fun assertionName(a: Assertion) = listOf("来源记录", "根据记录推测", "已确认", "计划", "信息不足")[a.ordinal]
fun clock(t: Long, z: String) = Instant.ofEpochMilli(t).atZone(ZoneId.of(z)).format(DateTimeFormatter.ofPattern("HH:mm"))
fun laneIcon(l: Lane): ImageVector = when(l) {
    Lane.PLACE -> Icons.Outlined.LocationOn
    Lane.ACTIVITY -> Icons.Outlined.DirectionsWalk
    Lane.DIGITAL -> Icons.Outlined.Smartphone
    Lane.LIFE -> Icons.Outlined.FavoriteBorder
}
fun sourceIcon(s: Source): ImageVector = when(s) {
    Source.USAGE, Source.ACCESSIBILITY -> Icons.Outlined.Smartphone
    Source.LOCATION -> Icons.Outlined.LocationOn
    Source.STEPS -> Icons.Outlined.DirectionsWalk
    Source.HEALTH -> Icons.Outlined.FavoriteBorder
    Source.NOTIFICATION -> Icons.Outlined.Notifications
    Source.MEDIA -> Icons.Outlined.PlayArrow
    Source.PHOTO -> Icons.Outlined.PhotoLibrary
    Source.CALENDAR -> Icons.Outlined.DateRange
}
@Composable fun WoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val light = lightColorScheme(primary=Violet, onPrimary=Color.White,
        primaryContainer=Color(0xFFEEEAFB), onPrimaryContainer=Color(0xFF43338F),
        background=Color(0xFFF6F6FA), onBackground=Ink, surface=Color.White, onSurface=Ink,
        surfaceVariant=Color(0xFFEDEDF4), onSurfaceVariant=Color(0xFF69697D), outline=Color(0xFF8C8C9B),
        outlineVariant=Color(0xFFE3E3ED), secondary=Color(0xFF277767))
    val night = darkColorScheme(primary=Color(0xFFC8BCFF), onPrimary=Color(0xFF302264),
        primaryContainer=Color(0xFF382F60), onPrimaryContainer=Color(0xFFE4DCFF),
        background=Color(0xFF16161F), onBackground=Color(0xFFF0EFF7), surface=Color(0xFF22222F),
        onSurface=Color(0xFFF0EFF7), surfaceVariant=Color(0xFF30303E), onSurfaceVariant=Color(0xFFB7B6C8),
        outline=Color(0xFF8B899D), outlineVariant=Color(0xFF3A394B), secondary=Color(0xFF90D5C4))
    MaterialTheme(colorScheme=if(dark)night else light, typography=Typography(
        headlineLarge=TextStyle(fontSize=30.sp, lineHeight=39.sp, fontWeight=FontWeight.Bold),
        headlineMedium=TextStyle(fontSize=26.sp, lineHeight=34.sp, fontWeight=FontWeight.Bold),
        headlineSmall=TextStyle(fontSize=22.sp, lineHeight=30.sp, fontWeight=FontWeight.Bold),
        titleLarge=TextStyle(fontSize=20.sp, lineHeight=28.sp, fontWeight=FontWeight.SemiBold),
        titleMedium=TextStyle(fontSize=16.sp, lineHeight=24.sp, fontWeight=FontWeight.SemiBold),
        bodyLarge=TextStyle(fontSize=16.sp, lineHeight=25.sp),
        bodyMedium=TextStyle(fontSize=14.sp, lineHeight=22.sp),
        labelLarge=TextStyle(fontSize=14.sp, lineHeight=20.sp, fontWeight=FontWeight.Medium)),content=content)
}
@Composable fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun Section(title: String, action: String?=null, onClick: ()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        if(action!=null)TextButton(onClick){Text(action)}
    }
}
@Composable fun Paper(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable fun Empty(title: String, text: String) {
    Paper { Text(title,style=MaterialTheme.typography.titleMedium); Muted(text) }
}
@Composable fun IconTile(icon: ImageVector, color: Color=MaterialTheme.colorScheme.primary, size: Int=44) {
    Surface(Modifier.size(size.dp),shape=RoundedCornerShape((size/3).dp),color=color.copy(alpha=.12f)) {
        Box(contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size((size*.5f).dp),tint=color)}
    }
}
@Composable fun StatusPill(text: String, color: Color=MaterialTheme.colorScheme.primary) {
    Surface(shape=CircleShape,color=color.copy(alpha=.10f)) {
        Text(text,Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium,color=color)
    }
}
