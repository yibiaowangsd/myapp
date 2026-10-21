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

val Ink = Color(0xFF172620)
val WoMint = Color(0xFFB9F3CA)
val WoPaper = Color(0xFFF5F3EC)
val Violet = Color(0xFF28685A) // Kept as a source-compatible accent for older screens.
val laneColors = listOf(Color(0xFF28685A), Color(0xFFB46734), Color(0xFF596D96), Color(0xFFA4586A))
@Composable fun laneTint(lane:Lane):Color = if(MaterialTheme.colorScheme.background.luminance()<.15f)
    listOf(Color(0xFFB9F3CA),Color(0xFFF8C793),Color(0xFFAAC5E8),Color(0xFFF2B9C4))[lane.ordinal]
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
    val light = lightColorScheme(primary=Ink, onPrimary=WoPaper,
        primaryContainer=WoMint, onPrimaryContainer=Ink,
        background=WoPaper, onBackground=Ink, surface=Color(0xFFFFFEF9), onSurface=Ink,
        surfaceVariant=Color(0xFFEAECE2), onSurfaceVariant=Color(0xFF5B685F), outline=Color(0xFF7C8B80),
        outlineVariant=Color(0xFFDEE3D8), secondary=Color(0xFF28685A), secondaryContainer=Color(0xFFDDEADE),
        onSecondaryContainer=Ink, surfaceContainer=Color(0xFFF0F0E7))
    val night = darkColorScheme(primary=WoMint, onPrimary=Ink,
        primaryContainer=Color(0xFF244635), onPrimaryContainer=WoMint,
        background=Color(0xFF101914), onBackground=Color(0xFFF5F3E9), surface=Color(0xFF1B2921),
        onSurface=Color(0xFFF5F3E9), surfaceVariant=Color(0xFF293B30), onSurfaceVariant=Color(0xFFB6C4B9),
        outline=Color(0xFF84968A), outlineVariant=Color(0xFF34483A), secondary=Color(0xFFB9F3CA),
        secondaryContainer=Color(0xFF293F31), onSecondaryContainer=WoMint, surfaceContainer=Color(0xFF202F26))
    MaterialTheme(colorScheme=if(dark)night else light, typography=Typography(
        displayLarge=TextStyle(fontSize=64.sp,lineHeight=68.sp,fontWeight=FontWeight.Light,letterSpacing=(-3).sp),
        headlineLarge=TextStyle(fontSize=32.sp, lineHeight=41.sp, fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp),
        headlineMedium=TextStyle(fontSize=26.sp, lineHeight=34.sp, fontWeight=FontWeight.Bold),
        headlineSmall=TextStyle(fontSize=22.sp, lineHeight=30.sp, fontWeight=FontWeight.Bold),
        titleLarge=TextStyle(fontSize=20.sp, lineHeight=28.sp, fontWeight=FontWeight.SemiBold),
        titleMedium=TextStyle(fontSize=16.sp, lineHeight=24.sp, fontWeight=FontWeight.SemiBold),
        bodyLarge=TextStyle(fontSize=16.sp, lineHeight=25.sp),
        bodyMedium=TextStyle(fontSize=14.sp, lineHeight=22.sp),
        labelLarge=TextStyle(fontSize=14.sp, lineHeight=20.sp, fontWeight=FontWeight.Medium)),
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(26.dp),extraLarge=RoundedCornerShape(32.dp)),content=content)
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
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))) {
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
