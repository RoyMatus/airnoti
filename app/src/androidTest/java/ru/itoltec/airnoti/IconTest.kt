package ru.itoltec.airnoti

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IconTest {
    @Test fun adaptiveAndMonochromeRenderWithSafeMarginsAt48dp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adaptive = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val round = context.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
        assertNotNull(round.foreground)
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull(adaptive.monochrome)
        // 48 dp cells at the device density, with native Android VectorDrawable rendering.
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size * 4, size * 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(248, 250, 244))
        for (theme in 0..1) for (mask in 0..2) {
            val left = (mask + .5f) * size
            val top = (theme * 2 + .5f) * size
            canvas.save(); canvas.translate(left, top)
            val path = Path().apply {
                when (mask) {
                    0 -> addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
                    1 -> addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * .28f, size * .28f, Path.Direction.CW)
                    else -> { moveTo(size * .5f, 0f); cubicTo(size.toFloat(), 0f, size.toFloat(), 0f, size.toFloat(), size * .5f); cubicTo(size.toFloat(), size.toFloat(), size.toFloat(), size.toFloat(), size * .5f, size.toFloat()); cubicTo(0f, size.toFloat(), 0f, size.toFloat(), 0f, size * .5f); cubicTo(0f, 0f, 0f, 0f, size * .5f, 0f); close() }
                }
            }
            canvas.clipPath(path)
            val background = if (theme == 0) Color.rgb(49,94,58) else Color.rgb(41,67,44)
            canvas.drawColor(background)
            val foreground = context.getDrawable(if (theme == 0) R.drawable.ic_airnoti_foreground else R.drawable.ic_airnoti_monochrome)!!.mutate()
            if (theme == 1) foreground.setTint(Color.rgb(167,213,167))
            // Adaptive icons expose the central 72 of the 108-unit vector canvas.
            foreground.setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4)
            foreground.draw(canvas); canvas.restore()
        }
        File(context.filesDir, "ui-icon-masks.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        adaptive.setBounds(0, 0, size, size)
        val native = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        adaptive.draw(Canvas(native))
        assertNotEquals(Color.TRANSPARENT, native.getPixel(size / 2, size / 2))
        File(context.filesDir, "ui-icon-system.png").outputStream().use { native.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
