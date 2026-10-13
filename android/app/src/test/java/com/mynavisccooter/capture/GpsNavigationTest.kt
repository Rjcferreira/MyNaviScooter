package com.mynavisccooter.capture

import org.junit.Assert.*
import org.junit.Test

class GpsNavigationTest {
    @Test fun communityFeedRejectsProposalsAndPersonalIdentifiers() {
        GpsCommunity.validateCatalog("""{"schema":"mynavi-gps-community/v1","publication":"moderated","places":[],"hazards":[],"routes":[]}""")
        assertTrue(runCatching { GpsCommunity.validateCatalog("""{"schema":"mynavi-gps-community/v1","publication":"proposal","places":[],"hazards":[],"routes":[]}""") }.isFailure)
        assertTrue(runCatching { GpsCommunity.validateCatalog("""{"schema":"mynavi-gps-community/v1","publication":"moderated","places":[{"id":"personal-record"}],"hazards":[],"routes":[]}""") }.isFailure)
    }
    private val start = GpsPoint(52.0, 13.0)
    private val middle = GpsPoint(52.0, 13.005)
    private val end = GpsPoint(52.0, 13.01)
    private val distance = GpsGeometry.distance(start, end)
    private val route = GpsRoute(listOf(start, middle, end), distance, 300.0,
        listOf(GpsStep(start, 0.0, "Segue"), GpsStep(middle, distance / 2, "Vira"), GpsStep(end, distance, "Chegada")))
    @Test fun projectionComputesRemainingDistanceBetweenVertices() {
        val progress = GpsGeometry.progress(route, GpsPoint(52.0, 13.0025))
        assertEquals(distance * .75, progress.remainingMeters, 1.0)
        assertEquals(225.0, progress.seconds, 1.0)
        assertEquals("Vira", progress.nextInstruction)
        assertTrue(progress.offRouteMeters < 1)
        assertFalse(progress.arrived)
    }
    @Test fun arrivalRequiresPositionNearDestinationNotJustProjectionAtEndpoint() {
        assertTrue(GpsGeometry.progress(route, end).arrived)
        assertFalse(GpsGeometry.progress(route, GpsPoint(52.01, 13.01)).arrived)
        assertTrue(GpsGeometry.progress(route, GpsPoint(52.01, 13.01)).offRouteMeters > 1000)
    }
    @Test fun coordinatesRejectNonFiniteAndOutOfRangeValues() {
        assertFalse(GpsPoint(Double.NaN, 13.0).valid())
        assertFalse(GpsPoint(91.0, 13.0).valid())
        assertFalse(GpsPoint(52.0, 181.0).valid())
    }
    @Test fun parserUsesLongitudeLatitudeOrderAndAccumulatedStepDistances() {
        val result = GpsRouting.parse("""{"code":"Ok","routes":[{"distance":600,"duration":180,
          "geometry":{"coordinates":[[13,52],[13.01,52]]},"legs":[{"steps":[
          {"distance":400,"name":"Rua A","maneuver":{"type":"depart","location":[13,52]}},
          {"distance":200,"name":"Rua B","maneuver":{"type":"turn","modifier":"right","location":[13.006,52]}},
          {"distance":0,"maneuver":{"type":"arrive","location":[13.01,52]}}]}]}]}""")
        assertEquals(start, result.points.first())
        assertEquals(400.0, result.steps[1].startsAt, 0.0)
        assertEquals("Vira à direita · Rua B", result.steps[1].instruction)
        assertEquals(600.0, result.steps.last().startsAt, 0.0)
    }
    @Test(expected = IllegalStateException::class) fun rejectsMissingRouteInsteadOfDrawingStraightLine() {
        GpsRouting.parse("""{"code":"NoRoute","routes":[]}""")
    }
}
