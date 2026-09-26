package com.mynavisccooter.capture

import android.graphics.Bitmap
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DashboardTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun homeAndSettingsWorkWithoutBluetoothHardware() {
        onView(withText("Boa viagem!")).check(matches(isDisplayed()))
        screenshot("dashboard-home")
        onView(withText("Configurações e diagnóstico")).perform(scrollTo())
        screenshot("dashboard-controls")
        onView(withText("Configurações e diagnóstico")).perform(click())
        onView(withText("Sobre esta versão")).perform(click())
        onView(withText("Fechar")).check(matches(isDisplayed()))
        screenshot("dashboard-about")
    }
}
