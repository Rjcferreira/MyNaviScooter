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
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cp ${file.absolutePath} /data/local/tmp/$name.png"
        )).use { it.readBytes() }
    }
    @Test fun onboardingStopsAtConnectionWithoutBluetoothHardware() {
        Thread.sleep(20_700)
        val needsConsent = runCatching {
            onView(withText("Continuar")).check(matches(isDisplayed())); true
        }.getOrDefault(false)
        if (needsConsent) {
            screenshot("onboarding-terms")
            onView(withText("Continuar")).perform(click())
            onView(withText("Alterações podem afetar a homologação")).check(matches(isDisplayed()))
            screenshot("onboarding-liability")
            onView(withText("Li, compreendi e aceito continuar.")).perform(click())
            onView(withText("Aceitar e entrar")).perform(click())
        }
        onView(withText("Liga a tua scooter")).check(matches(isDisplayed()))
        onView(withText("Procurar scooter")).check(matches(isDisplayed()))
        onView(withText("Boa viagem!")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        screenshot("connection-home")
        // Exercise function pages without requiring a physical BLE scooter.
        activity.scenario.onActivity { host ->
            val dashboard = Dashboard(host)
            host.setContentView(android.widget.FrameLayout(host).apply {
                setPadding(0, dashboard.dp(40), 0, dashboard.dp(24))
                // Mirror MainActivity's inset-aware container so the menu is
                // not underneath the emulator's status bar.
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                    val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    view.setPadding(bars.left, maxOf(bars.top, dashboard.dp(40)), bars.right, maxOf(bars.bottom, dashboard.dp(24)))
                    insets
                }
                addView(dashboard.create(false, {}, {}, {}, {}, {}, {}, {}, {}, {}))
                androidx.core.view.ViewCompat.requestApplyInsets(this)
            })
        }
        onView(withText("Boa viagem!")).check(matches(isDisplayed()))
        screenshot("telemetry-fullscreen")
        onView(withContentDescription("Atalhos das funções")).perform(click())
        screenshot("function-menu")
        onView(withText("GPS")).perform(click())
        onView(withText("Boa viagem!")).check { view, _ ->
            org.junit.Assert.assertTrue(view == null || !view.getGlobalVisibleRect(android.graphics.Rect()))
        }
        onView(withText("Pesquisar local ou endereço")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        onView(withHint("Pesquisar local ou endereço")).check(matches(isDisplayed()))
        screenshot("gps-fullscreen")
        onView(withContentDescription("Atalhos das funções")).perform(click())
        onView(withText("Perfis")).perform(click())
        onView(withText("Boa viagem!")).check { view, _ ->
            org.junit.Assert.assertTrue(view == null || !view.getGlobalVisibleRect(android.graphics.Rect()))
        }
        onView(withText("Original")).check(matches(isDisplayed()))
        screenshot("profiles-fullscreen")
    }
}
