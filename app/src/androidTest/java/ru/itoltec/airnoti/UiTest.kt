package ru.itoltec.airnoti

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import androidx.test.espresso.Espresso
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val sample = AirUiState(Settings(enabled = true, address = "test-band", packages = setOf("sample.chat")),
        hasBluetooth = true, bluetoothOn = true, access = true, listener = true,
        devices = listOf(BandEntry("test-band", "Мой браслет")))
    private fun show(content: @Composable () -> Unit) { rule.activity.runOnUiThread { rule.activity.setContent(content = content) }; rule.waitForIdle() }
    private fun text(id: Int) = rule.activity.getString(id)
    private fun screenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.filesDir, "ui-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun noAppSelectionIsNeededWithLegacyEmptySettings() {
        val state = sample.copy(settings = sample.settings.copy(all = false, packages = emptySet()))
        show { AirTheme { Surface { AirInterface(state, AirActions()) } } }
        rule.onNodeWithText(text(R.string.ui_ready_to_send)).assertExists()
        rule.onNodeWithTag("apps").assertDoesNotExist()
        rule.onNodeWithTag("all").assertDoesNotExist()
        rule.onNodeWithTag("test").performScrollTo().assertIsEnabled()
        rule.onNodeWithText(text(R.string.ui_signal_hint)).performScrollTo().assertExists()
    }
    @Test fun deniedPermissionAndAllResultsRemainTruthful() {
        var state by mutableStateOf(sample.copy(hasBluetooth = false))
        var requests = 0
        show { AirTheme { Surface { AirInterface(state, AirActions(bluetooth = { requests++ })) } } }
        rule.onNodeWithText(text(R.string.grant_bluetooth)).performScrollTo().performClick()
        assertEquals(1, requests)
        rule.onNodeWithText(text(R.string.permission_needed)).assertExists()
        rule.onNodeWithTag("test").performScrollTo().assertIsNotEnabled()
        for (result in AirTransport.Result.entries) {
            rule.runOnUiThread { state = sample.copy(result = result) }
            rule.onNodeWithTag("result").performScrollTo().assertTextEquals(text(result.message()))
        }
    }
    @Test fun notificationPermissionExplainsWhyTestIsNotForwarding() {
        var state by mutableStateOf(sample.copy(access = false, listener = false))
        var requests = 0
        show { AirTheme { Surface { AirInterface(state, AirActions(notifications = { requests++ })) } } }
        rule.onNodeWithText(text(R.string.ui_relay_permission_missing)).assertExists()
        rule.onNodeWithText(text(R.string.ui_grant_notification_access)).performScrollTo().performClick()
        assertEquals(1, requests)
        rule.onNodeWithText(text(R.string.ui_relay_permission_missing)).assertExists()
        rule.onNodeWithTag("test").performScrollTo().assertIsEnabled()
        rule.onNodeWithText(text(R.string.ui_manual_test_only)).performScrollTo().assertExists()
        rule.runOnUiThread { state = sample }
        rule.onNodeWithText(text(R.string.ui_grant_notification_access)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.ui_manual_test_only)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.ui_ready_to_send)).performScrollTo().assertExists()
    }
    @Test fun emptySelectionTestAndCancellationNeverShowFakeSuccess() {
        var state by mutableStateOf(sample.copy(settings = sample.settings.copy(packages = emptySet())))
        var sends = 0
        show { AirTheme { Surface { AirInterface(state, AirActions(send = { sends++; state = state.copy(sending = true) })) } } }
        rule.onNodeWithTag("test").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, sends)
        rule.onNodeWithText(text(R.string.sending)).assertExists()
        rule.runOnUiThread { state = state.copy(sending = false, settings = state.settings.copy(enabled = false)) }
        rule.onNodeWithText(text(R.string.ui_test_cancelled)).assertExists()
        rule.onNodeWithText(text(R.string.verified)).assertDoesNotExist()
        Espresso.pressBack()
        rule.onNodeWithTag("test").performScrollTo().assertIsNotEnabled()
    }
    @Test fun helpNavigationSurvivesRecreationWithoutChangingSettings() {
        val app = rule.activity.application as AirNotiApp
        val before = kotlinx.coroutines.runBlocking { app.preferences.flow.first() }
        screenshot("device-home")
        rule.onNodeWithText(text(R.string.help)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.strength)).performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText(text(R.string.ui_strength_health)).assertExists()
        Espresso.pressBack()
        rule.onNodeWithText(text(R.string.ui_setup_help)).assertExists()
        assertEquals(before, kotlinx.coroutines.runBlocking { app.preferences.flow.first() })
    }
    @Test fun lightDarkLargeAndEnglishScreens() {
        for ((name, night, scale) in listOf(Triple("light", false, 1f), Triple("dark", true, 1f), Triple("large", false, 2f), Triple("english", false, 1f))) {
            val config = Configuration(rule.activity.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                fontScale = scale
                screenWidthDp = if (scale == 2f) 320 else screenWidthDp
                if (name == "english") setLocale(java.util.Locale.ENGLISH)
            }
            val localizedContext = rule.activity.createConfigurationContext(config)
            show {
                CompositionLocalProvider(LocalConfiguration provides config, androidx.compose.ui.platform.LocalContext provides localizedContext,
                    LocalDensity provides Density(rule.activity.resources.displayMetrics.density, scale)) {
                    key(name) { AirTheme(dynamic = name == "english") { Surface(Modifier.widthIn(max = if (scale == 2f) 320.dp else 600.dp)) { AirInterface(sample, AirActions()) } } }
                }
            }
            rule.onNodeWithText("AirNoti").assertIsDisplayed()
            screenshot(name)
            rule.onNodeWithText(localizedContext.getString(R.string.help)).performScrollTo().performClick()
            rule.onNodeWithText(localizedContext.getString(R.string.ui_setup_help)).performScrollTo().performClick()
            screenshot("$name-setup")
        }
        show { AirTheme(dynamic = false) { Surface { AirInterface(sample.copy(result = AirTransport.Result.UNKNOWN), AirActions()) } } }
        rule.onNodeWithTag("result").performScrollTo()
        screenshot("error")
    }
}
