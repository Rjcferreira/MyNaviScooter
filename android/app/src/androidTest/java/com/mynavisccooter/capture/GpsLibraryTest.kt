package com.mynavisccooter.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsLibraryTest {
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
