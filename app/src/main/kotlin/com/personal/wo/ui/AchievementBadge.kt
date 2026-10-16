package com.personal.wo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.personal.wo.core.AchievementMetric
import kotlin.math.*

internal fun achievementTint(metric:AchievementMetric):Color=Color(when(metric){
    AchievementMetric.DAYS->0xFFC3954B;AchievementMetric.STREAK->0xFF886EA8
    AchievementMetric.STEPS->0xFF609481;AchievementMetric.EXERCISE->0xFFCE8974
    AchievementMetric.PHOTOS->0xFF729DB7;AchievementMetric.READING->0xFF7886B3
    AchievementMetric.PLACES->0xFF71958F;AchievementMetric.FAVORITES->0xFFAC86A4
})

/** Original, offline vector artwork. Shared paths also define the SVG design reference. */
internal fun achievementGlyph(metric:AchievementMetric):List<String> = when(metric){
    AchievementMetric.DAYS->listOf("M29 35 L29 62 Q40 58 50 63 Q61 58 71 62 L71 35 Q60 31 50 37 Q40 31 29 35 Z","M50 37 L50 62 M35 43 L43 45 M35 49 L43 51 M57 44 L65 42 M57 50 L65 48","M43 27 Q50 19 57 27 M50 20 L50 17 M38 25 L36 23 M62 25 L64 23")
    AchievementMetric.STREAK->listOf("M30 32 L70 32 L70 65 L30 65 Z M30 41 L70 41 M39 28 L39 36 M61 28 L61 36","M38 51 L43 56 L51 47 M53 58 L58 63 L66 54","M37 22 Q50 11 63 22")
    AchievementMetric.STEPS->listOf("M37 31 C29 30 28 45 32 49 C36 54 42 49 42 44 C42 39 44 33 37 31 Z","M32 54 Q30 62 36 64 Q44 64 41 55 Z","M61 24 C54 22 51 32 52 39 C53 46 60 47 63 42 C66 37 69 26 61 24 Z","M54 49 Q50 56 57 59 Q64 60 65 52 Z","M35 72 Q52 76 65 64")
    AchievementMetric.EXERCISE->listOf("M50 25 A5 5 0 1 1 50 35 A5 5 0 1 1 50 25","M39 46 L48 38 L58 42 L66 35 M49 39 L45 54 L57 64 L65 62 M45 54 L36 64 L29 61","M30 35 Q28 26 35 20 M64 69 Q73 68 76 60")
    AchievementMetric.PHOTOS->listOf("M27 30 L73 30 L73 65 L27 65 Z","M29 60 L40 47 L50 55 L57 43 L72 61","M41 36 A4 4 0 1 1 41 44 A4 4 0 1 1 41 36","M35 71 L78 71 L78 38")
    AchievementMetric.READING->listOf("M27 31 Q39 27 50 35 Q62 27 73 31 L73 64 Q60 60 50 68 Q39 60 27 64 Z","M50 36 L50 66 M33 39 Q39 38 44 41 M33 46 Q39 45 44 48 M56 42 Q62 38 67 39 M56 49 Q62 45 67 46","M45 22 L50 15 L55 22 L50 29 Z")
    AchievementMetric.PLACES->listOf("M50 22 C32 22 29 40 39 52 L50 65 L61 52 C71 40 68 22 50 22 Z","M50 31 A7 7 0 1 1 50 45 A7 7 0 1 1 50 31","M37 65 Q22 70 36 75 Q50 80 66 74 Q77 69 64 65")
    AchievementMetric.FAVORITES->listOf("M50 65 C44 60 26 48 28 37 C30 26 42 25 50 34 C58 25 70 26 72 37 C74 48 56 60 50 65 Z","M50 38 L53 45 L61 46 L55 51 L57 59 L50 55 L43 59 L45 51 L39 46 L47 45 Z","M28 24 L28 18 M25 21 L31 21 M73 57 L73 51 M70 54 L76 54")
}

@Composable internal fun AchievementBadge(metric:AchievementMetric,tier:Int,unlocked:Boolean,modifier:Modifier=Modifier) {
    val level=tier.coerceIn(1,4)
    val glyph=remember(metric){achievementGlyph(metric).map{PathParser().parsePathString(it).toPath()}}
    val outline=remember(level){Path().apply {
        if(level==1)addOval(androidx.compose.ui.geometry.Rect(13f,8f,87f,82f))
        else {
            val points=if(level==2)6 else if(level==3)12 else 96
            for(i in 0..points) {
                val a=(-90.0+i*360.0/points)*PI/180
                val r=if(level==4)36.5+1.5*cos(12*a) else 37.0
                val x=50f+(cos(a)*r).toFloat();val y=45f+(sin(a)*r).toFloat()
                if(i==0)moveTo(x,y) else lineTo(x,y)
            };close()
        }
    }}
    val ribbon=remember{PathParser().parsePathString("M28 70 L23 96 L39 87 L48 96 L49 76 M51 76 L52 96 L62 87 L78 96 L71 70").toPath()}
    val tint=if(unlocked)achievementTint(metric) else Color(0xFF92989F)
    val metal=if(!unlocked)Color(0xFFC4C8CB) else when(level){1->Color(0xFFBB8C64);2->Color(0xFF9DAFB9);3->Color(0xFFD4AD66);else->Color(0xFFC7BC97)}
    Canvas(modifier.aspectRatio(1f).semantics{contentDescription="${metric.label}，${level}阶${if(unlocked)"已点亮" else "未点亮"}徽章"}) {
        val factor=size.minDimension/100f
        scale(factor,factor,Offset.Zero) {
            drawCircle(Color.Black.copy(alpha=if(unlocked).065f else .025f),37f,Offset(50f,48f))
            drawPath(ribbon,lerp(tint,Color.White,if(unlocked).12f else .7f))
            drawPath(outline,Brush.linearGradient(listOf(lerp(metal,Color.White,.55f),metal,lerp(metal,Color.White,.28f)),Offset(20f,10f),Offset(79f,82f)))
            drawCircle(Brush.radialGradient(listOf(lerp(tint,Color.White,if(unlocked).93f else .97f),lerp(tint,Color.White,if(unlocked).55f else .87f)),Offset(40f,28f),62f),31.5f,Offset(50f,45f))
            drawCircle(Color.White.copy(alpha=.65f),31.2f,Offset(50f,45f),style=Stroke(.8f))
            drawCircle(metal.copy(alpha=.7f),28.3f,Offset(50f,45f),style=Stroke(.55f))
            val ink=if(unlocked)lerp(tint,Color(0xFF273C40),.53f) else Color(0xFFA7AEB3)
            glyph.forEach{drawPath(it,ink,style=Stroke(2.05f,cap=StrokeCap.Round,join=StrokeJoin.Round))}
            // Tier ornaments have distinct silhouettes; the art is not just recoloured.
            repeat(level) { i ->
                val x=50f+(i-(level-1)/2f)*7f
                val gem=Path().apply{moveTo(x,76f);lineTo(x+2f,79f);lineTo(x,82f);lineTo(x-2f,79f);close()}
                drawPath(gem,if(unlocked)Color(0xFFFFF1CB) else Color(0xFFDADDE0))
            }
            if(level>=3)for(side in listOf(-1f,1f))repeat(3) { i ->
                val x=50f+side*(34f-i*1.2f);val y=50f+i*7f
                drawOval(metal,Offset(x-2f,y-2f),androidx.compose.ui.geometry.Size(4f,7f))
            }
            if(level==4) {
                drawLine(Color.White.copy(alpha=.8f),Offset(77f,15f),Offset(77f,27f),1.2f,StrokeCap.Round)
                drawLine(Color.White.copy(alpha=.8f),Offset(71f,21f),Offset(83f,21f),1.2f,StrokeCap.Round)
            }
        }
    }
}
