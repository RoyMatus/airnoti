package ru.itoltec.airnoti

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitbit.goldengate.bindings.GoldenGate
import com.fitbit.goldengate.bindings.io.RxSource
import com.fitbit.goldengate.bindings.io.TxSink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @Test fun nativeLibrariesLoadAndJniSourcesReleaseWithoutBluetooth() {
        System.loadLibrary("gg"); GoldenGate.init(loggingEnabled = false)
        val tx = TxSink(); val rx = RxSource()
        assertTrue(tx.thisPointer != 0L); assertTrue(rx.thisPointer != 0L)
        rx.close(); tx.close()
    }
    @Test fun dataStorePersistsAndAllModePreservesSelections() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val store = SettingsRepository(c)
        val before = store.flow.first()
        try {
            store.enabled(false)
            store.select("airnoti.test.selection", true)
            store.all(true)
            assertTrue(SettingsRepository(c).flow.first().packages.contains("airnoti.test.selection"))
            store.all(false)
            assertTrue(SettingsRepository(c).flow.first().packages.contains("airnoti.test.selection"))
        } finally {
            store.select("airnoti.test.selection", "airnoti.test.selection" in before.packages)
            store.all(before.all); store.enabled(before.enabled)
        }
    }
}
