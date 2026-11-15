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
        apps = listOf(UiApp("sample.chat", "Сообщения"), UiApp("sample.mail", "Почта")),
        devices = listOf(BandEntry("test-band", "Мой браслет")))
    private fun show(content: @Composable () -> Unit) { rule.activity.runOnUiThread { rule.activity.setContent(content = content) }; rule.waitForIdle() }
    private fun text(id: Int) = rule.activity.getString(id)
    private fun screenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.filesDir, "ui-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun allModePreservesIndividualSelectionAndSearchesPackage() {
        var state by mutableStateOf(sample)
        show { AirTheme { Surface { AirInterface(state, AirActions(all = { state = state.copy(settings = state.settings.copy(all = it)) })) } } }
        rule.onNodeWithTag("apps").performScrollTo().performClick()
        rule.onNodeWithTag("app:sample.chat").performScrollTo().assertIsOn()
        rule.onNodeWithTag("all").performScrollTo().performClick()
        rule.onNodeWithTag("app:sample.mail").performScrollTo().assertIsOn().assertIsNotEnabled()
        rule.onNodeWithTag("all").performScrollTo().performClick()
        rule.onNodeWithTag("app:sample.chat").performScrollTo().assertIsOn()
        rule.onNodeWithTag("app:sample.mail").performScrollTo().assertIsOff()
        rule.onNodeWithTag("search").performScrollTo().performTextInput("sample.mail")
        rule.onNodeWithTag("app:sample.chat").assertDoesNotExist()
        rule.onNodeWithTag("app:sample.mail").assertExists()
        Espresso.pressBack() // dismiss keyboard
        Espresso.pressBack() // system navigation
        rule.onNodeWithTag("relay").assertExists()
        assertEquals(setOf("sample.chat"), state.settings.packages)
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
    @Test fun navigationAndQuerySurviveSavedStateRestore() {
        val app = rule.activity.application as AirNotiApp
        val before = kotlinx.coroutines.runBlocking { app.preferences.flow.first() }
        screenshot("device-home")
        rule.onNodeWithTag("apps").performScrollTo().performClick()
        screenshot("device-apps")
        rule.onNodeWithTag("search").performScrollTo().performTextInput("airnoti.unmatched.query")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("search").performScrollTo().assertTextContains("airnoti.unmatched.query")
        rule.onNodeWithText(text(R.string.ui_apps_title)).performScrollTo().assertExists()
        val after = kotlinx.coroutines.runBlocking { app.preferences.flow.first() }
        assertEquals(before, after)
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
            rule.onNodeWithTag("apps").performScrollTo().performClick()
            rule.onNodeWithTag("search").performScrollTo().assertIsDisplayed()
            screenshot("$name-apps")
            rule.onNodeWithTag("all").performScrollTo().assertHasClickAction()
            Espresso.pressBack()
            rule.onNodeWithText(localizedContext.getString(R.string.help)).performScrollTo().performClick()
            rule.onNodeWithText(localizedContext.getString(R.string.ui_setup_help)).performScrollTo().performClick()
            screenshot("$name-setup")
        }
        show { AirTheme(dynamic = false) { Surface { AirInterface(sample.copy(result = AirTransport.Result.UNKNOWN), AirActions()) } } }
        rule.onNodeWithTag("result").performScrollTo()
        screenshot("error")
    }
}
