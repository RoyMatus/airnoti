package ru.itoltec.airnoti

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun lightDarkAndLargeFontRemainScrollable() {
        val app = rule.activity.application as AirNotiApp
        val before = runBlocking { app.preferences.flow.first() }
        rule.runOnUiThread { app.disable() }
        try {
            for ((name, night, scale) in listOf(Triple("light", false, 1f), Triple("dark", true, 1f), Triple("large", false, 2f))) {
                rule.activity.runOnUiThread {
                    val configuration = Configuration(rule.activity.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        fontScale = scale
                    }
                    rule.activity.setContent {
                        CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(rule.activity.resources.displayMetrics.density, scale)) {
                            androidx.compose.runtime.key(name) { AirTheme { androidx.compose.material3.Surface { AirScreen(app, 0) } } }
                        }
                    }
                }
                rule.waitForIdle()
                rule.onNodeWithText("AirNoti").assertIsDisplayed()
                val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
                File(app.filesDir, "ui-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                rule.onNodeWithText(rule.activity.getString(R.string.search)).performScrollTo().assertIsDisplayed()
                rule.onNodeWithText(rule.activity.getString(R.string.all_apps)).assertHasClickAction()
            }
        } finally { rule.runOnUiThread { if (before.enabled) app.enable() else app.disable() } }
    }
}
