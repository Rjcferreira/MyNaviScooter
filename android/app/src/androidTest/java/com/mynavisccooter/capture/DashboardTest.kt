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
            host.setContentView(dashboard.create(false, {}, {}, {}, {}, {}, {}, {}, {}, {}))
        }
        onView(withText("Boa viagem!")).check(matches(isDisplayed()))
        onView(withContentDescription("Atalhos das funções")).perform(click())
        onView(withText("GPS")).perform(click())
        onView(withText("Boa viagem!")).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
        onView(withText("Pesquisar local ou endereço")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        onView(withHint("Pesquisar local ou endereço")).check(matches(isDisplayed()))
        screenshot("gps-fullscreen")
        onView(withContentDescription("Atalhos das funções")).perform(click())
        onView(withText("Perfis")).perform(click())
        onView(withText("Boa viagem!")).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
        onView(withText("Original")).check(matches(isDisplayed()))
        screenshot("profiles-fullscreen")
    }
}
