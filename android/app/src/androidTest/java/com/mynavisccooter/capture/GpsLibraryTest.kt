package com.mynavisccooter.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsLibraryTest {
    @Test fun communityReplacementRemovesWithdrawnEntriesAndPreservesPersonalData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val personalPrefs = context.getSharedPreferences("gps_personal_test", android.content.Context.MODE_PRIVATE)
        val approvedPrefs = context.getSharedPreferences("gps_approved_test", android.content.Context.MODE_PRIVATE)
        personalPrefs.edit().clear().commit(); approvedPrefs.edit().clear().commit()
        try {
            val personal = GpsLibrary(context, "gps_personal_test")
            personal.addPlace("Private", GpsPoint(52.0, 13.0))
            val approved = GpsLibrary(context, "gps_approved_test")
            approved.importJson("""{"schema":"mynavi-gps-community/v1","places":[{"id":"github:issue-1","name":"Approved","lat":52,"lon":13}],"hazards":[],"routes":[]}""", replace = true)
            assertEquals(1, approved.places().size)
            assertTrue(runCatching { approved.importJson("""{"schema":"mynavi-gps-community/v1","places":[{"id":"github:bad","name":"Bad","lat":500,"lon":13}]}""", replace = true) }.isFailure)
            assertEquals(1, approved.places().size)
            approved.importJson("""{"schema":"mynavi-gps-community/v1","places":[],"hazards":[],"routes":[]}""", replace = true)
            assertTrue(approved.places().isEmpty())
            assertEquals("Private", personal.places().single().name)
            assertEquals("Private", GpsLibrary(context, "gps_personal_test").places().single().name)
        } finally { personalPrefs.edit().clear().commit(); approvedPrefs.edit().clear().commit() }
    }
    @Test fun exportImportDeduplicatesAndInvalidBatchDoesNotPartiallyChangeLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("gps_library_v1", android.content.Context.MODE_PRIVATE)
        val prior = prefs.getString("data", null)
        try {
            prefs.edit().clear().commit()
            val source = GpsLibrary(context)
            val first = GpsPoint(52.0, 13.0); val last = GpsPoint(52.0, 13.01)
            source.addPlace("Casa teste", first)
            source.addHazard("Buraco", first, "Teste")
            val route = GpsRoute(listOf(first, last), 600.0, 180.0,
                listOf(GpsStep(first, 0.0, "Segue"), GpsStep(last, 600.0, "Chegada")))
            source.addRoute("Trajeto teste", route.points, true, route)
            val json = source.exportJson()
            prefs.edit().clear().commit()
            val target = GpsLibrary(context)
            assertEquals(3, target.importJson(json))
            assertEquals(0, target.importJson(json))
            assertEquals("Casa teste", target.places().single().name)
            assertEquals("Buraco", target.hazards().single().kind)
            assertEquals(2, target.routes().single().navigation!!.steps.size)
            val bad = """{"schema":"mynavi-gps-community/v1","places":[
              {"id":"new-place","name":"Novo","lat":52,"lon":13},
              {"id":"bad-place","name":"Errado","lat":200,"lon":13}]}"""
            assertTrue(runCatching { target.importJson(bad) }.isFailure)
            assertEquals(1, target.places().size)
            assertEquals(1, GpsLibrary(context).places().size)
        } finally {
            prefs.edit().clear().apply { if (prior != null) putString("data", prior) }.commit()
        }
    }
}
