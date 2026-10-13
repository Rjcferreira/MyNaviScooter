# GPS online

The map uses MapLibre Native with online OpenFreeMap styles. The GPS page initializes the map lazily and stops location updates when hidden or when the app is paused.

Routes use the FOSSGIS OSRM bicycle backend at `https://routing.openstreetmap.de/routed-bike`. The OSRM API calls its path profile `driving`; the selected backend is **bike**, not car. Requests provide origin and destination coordinates to this provider. Manual requests are separated by at least two seconds; automatic reroutes by at least 30 seconds, after three accurate off-route fixes. Requests are bounded to 2 MB with connection/read timeouts. No straight-line fallback is presented as a road route.

Provider policy: https://routing.openstreetmap.de/about.html. Required attribution and the OpenStreetMap fix-the-map link are available in GPS → More → About. This community service has usage limits and is not an unlimited production SLA. Bicycle routing does not establish scooter-specific legal access.

## Implemented

- Online map; zoom, north/heading, light/dark, recenter.
- Address search through the device's Android Geocoder, and long-press destination/manual origin.
- Bicycle-based route polyline, distance, estimated duration and step list.
- Foreground location guidance, next maneuver, distance remaining, arrival, optional Portuguese Android text-to-speech, and rate-limited off-route recalculation.
- Saved places; saved/recommended routes, including route instructions when available.
- Local reports: pothole, obstacle, uneven surface, elevation and dangerous segment. Reports within 80 m can be announced during navigation. Reports older than 30 days are excluded from map/proximity notifications; stored records can still be inspected/deleted.
- Foreground GPS journey recording, saving its geometry in the library.
- Explicit JSON sharing through Android FileProvider and bounded import, coordinate validation, deduplication and atomic batch validation.
- Public GitHub community catalog, a separate local snapshot, periodic/manual refresh and browser-based moderated Issue contributions. See `community/README.md`.

## Limits

Guidance and recording pause when leaving the GPS page or putting the app in the background. A foreground navigation service and persisted unfinished recordings are future work. Recorded tracks have no turn instructions; routing-engine routes preserve instructions. Personal data can be exchanged by file; the public community catalog is periodically downloaded from GitHub after maintainer review. There is no real-time database or automatic publication of Issue submissions. No live traffic, terrain-aware scooter restrictions or automatic detour around user reports is claimed. Device geocoder, TTS language availability, satellite signal and online providers affect operation.

## Verification

JVM tests cover route parsing, maneuver order, longitude/latitude order, nearest-segment progress, remaining distance and arrival/off-route behavior. Instrumentation covers page isolation and GPS controls; library round-trip, deduplication and rollback of malformed imports. Real-device testing is still required for voice timing and GPS drift while riding.
