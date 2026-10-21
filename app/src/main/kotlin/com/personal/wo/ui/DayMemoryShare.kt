package com.personal.wo.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.util.Size
import androidx.core.content.FileProvider
import com.personal.wo.core.DayView
import com.personal.wo.core.dayChapters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** A deliberately selected day, rendered locally and handed to Android's share sheet. */
object DayMemoryShare {
    suspend fun share(context: Context, day: DayView, minimumAppMinutes: Int = 10) {
        val uri = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "memory-cards").apply { mkdirs() }
            directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                ?.forEach { it.delete() }
            val file = File(directory, "Wo_${day.dayDate}.png")
            val cover = day.episodes.asSequence().flatMap { it.photoUris.asSequence() }.firstOrNull()?.let {
                runCatching { context.contentResolver.loadThumbnail(Uri.parse(it), Size(960, 520), null) }.getOrNull()
            }
            val bitmap = render(day, cover, minimumAppMinutes)
            try {
                file.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle(); cover?.recycle() }
            FileProvider.getUriForFile(context, "${context.packageName}.memory-cards", file)
        }
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("${day.dayDate} · 我", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享这一天"))
        }
    }

    internal fun render(day: DayView, cover: Bitmap? = null, minimumAppMinutes: Int = 10): Bitmap {
        val image = Bitmap.createBitmap(1080, 1620, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val ink = Color.rgb(18, 30, 27)
        val paper = Color.rgb(246, 245, 237)
        val accent = Color.rgb(179, 245, 198)
        val muted = Color.rgb(99, 112, 104)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value: String, x: Float, y: Float, size: Float, color: Int = ink, bold: Boolean = false) {
            paint.color = color; paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(value, x, y, paint)
        }
        fun line(value: String, x: Float, y: Float, size: Float, width: Float, color: Int = ink, bold: Boolean = false) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            canvas.drawText(TextUtils.ellipsize(value, p, width, TextUtils.TruncateAt.END).toString(), x, y, p)
        }
        canvas.drawColor(paper)
        paint.color = ink; canvas.drawRect(0f, 0f, 1080f, 444f, paint)
        text("我  /  A DAY TO KEEP", 72f, 87f, 26f, accent, true)
        val date = LocalDate.parse(day.dayDate)
        text("%02d".format(date.dayOfMonth), 64f, 300f, 212f, paper, true)
        text("${date.year} / %02d".format(date.monthValue), 648f, 186f, 36f, paper)
        text("星期${listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1]}", 648f, 242f, 31f, accent)
        text("把这一天，留给未来。", 76f, 377f, 38f, paper)
        val photoRect = RectF(48f, 426f, 1032f, 890f)
        val shape = Path().apply { addRoundRect(photoRect, 26f, 26f, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(shape)
        if (cover != null) {
            val scale = maxOf(photoRect.width() / cover.width, photoRect.height() / cover.height)
            val w = cover.width * scale; val h = cover.height * scale
            val target = RectF(photoRect.centerX() - w / 2, photoRect.centerY() - h / 2, photoRect.centerX() + w / 2, photoRect.centerY() + h / 2)
            paint.color = Color.WHITE; paint.isFilterBitmap = true
            canvas.drawBitmap(cover, null, target, paint)
        } else {
            paint.color = accent; canvas.drawRect(photoRect, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = Color.argb(90, 18, 30, 27)
            repeat(10) { i -> canvas.drawCircle(760f, 680f, 70f + i * 35, paint) }
            paint.style = Paint.Style.FILL
            text("EVERY DAY", 94f, 615f, 64f, ink, true)
            text("IS A STORY.", 94f, 692f, 64f, ink, true)
            text("日常，也值得收藏。", 98f, 806f, 32f)
        }
        canvas.restore()
        val chapters = dayChapters(day, minimumAppMinutes)
        text("DAY FRAGMENTS", 72f, 965f, 24f, muted, true)
        text("${chapters.size} 个大事件", 788f, 965f, 24f, muted)
        chapters.take(4).forEachIndexed { index, chapter ->
            val y = 1036f + index * 112
            paint.color = ink; canvas.drawCircle(84f, y - 12f, 6f, paint)
            if (index < minOf(chapters.size, 4) - 1) {
                paint.color = Color.rgb(204, 214, 205); paint.strokeWidth = 2f
                canvas.drawLine(84f, y + 1, 84f, y + 87, paint)
            }
            text(clock(chapter.part.startMs, day.zoneId), 116f, y, 27f, muted)
            line(chapter.part.label, 262f, y, 35f, 735f, bold = true)
            line(chapter.summary, 262f, y + 45, 25f, 735f, muted)
        }
        if (chapters.isEmpty()) {
            text("平常的一天，也有它的分量。", 74f, 1082f, 38f)
            text("这一天还没有可展示的大事件。", 74f, 1142f, 27f, muted)
        }
        paint.color = ink; canvas.drawRect(72f, 1492f, 1008f, 1494f, paint)
        text(day.dayDate.replace('-', '.'), 72f, 1551f, 25f, muted)
        text("我 · 生活回顾", 802f, 1551f, 25f, ink, true)
        return image
    }
}
