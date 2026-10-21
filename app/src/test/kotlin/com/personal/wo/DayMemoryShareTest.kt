package com.personal.wo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.personal.wo.core.demoDay
import com.personal.wo.ui.DayMemoryShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayMemoryShareTest {
    @Test fun cardRendersWithAndWithoutAnAccessiblePhoto() {
        val day = demoDay(LocalDate.of(2026, 10, 21), "Asia/Shanghai")
        val cover = Bitmap.createBitmap(960, 520, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80, 140, 160)) }
        val photo = DayMemoryShare.render(day, cover)
        val text = DayMemoryShare.render(day)
        assertEquals(1080, photo.width)
        assertEquals(1620, photo.height)
        assertEquals(cover.getPixel(0, 0), photo.getPixel(540, 620))
        assertNotEquals(photo.getPixel(540, 620), text.getPixel(540, 620))
        val output = File("build/ui-previews/wo-2-memory-card.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { text.compress(Bitmap.CompressFormat.PNG, 100, it) }
        photo.recycle(); text.recycle(); cover.recycle()
    }
}
