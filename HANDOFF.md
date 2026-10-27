# Handoff

Ημερομηνία: 2026-10-12. Repo: `Kastoria-Dev-Core/astiko-ktel-kastorias-app`.

## Ιστορικό σε μία παράγραφο

Ξεκίνησε ως PWA (React + Vite + TS, ξεχωριστό project, εκτός repo), μετά δοκιμάστηκε Expo και εγκαταλείφθηκε. Τελικά ξαναγράφτηκε ως native Android (Kotlin + Compose) με την ίδια λογική και τα ίδια JSON δεδομένα. Το app icon (B3) και το splash σχεδιάστηκαν και εγκρίθηκαν πριν υλοποιηθούν.

## Κατάσταση

| Περιοχή | Κατάσταση |
| --- | --- |
| Λογική ωραρίων (`domain/Schedule.kt`, `Geo.kt`) | Compiled και **8/8 unit tests περνάνε** (Πάσχα, αργίες, θερινό, μεσάνυχτα, αποστάσεις) |
| Kotlin syntax όλων των αρχείων | Ελεγμένο, χωρίς parse errors |
| UI (Compose), Gradle build | **Δεν έχει γίνει compile ποτέ.** Γράφτηκε σε περιβάλλον χωρίς πρόσβαση σε Google Maven / Maven Central. Το πρώτο Gradle sync τρέχει στο Windows PC του owner. |
| Δεδομένα | **Placeholder** (4 γραμμές, 11 στάσεις, φτιαχτά ωράρια και συντεταγμένες). Banner στο app όσο `meta.json` έχει `"placeholder": true`. |
| Εκτέλεση σε συσκευή | Δεν έχει γίνει ακόμα |

## Βήμα 1: πράσινο build

Τρέξε `./gradlew assembleDebug` και διόρθωσε ό,τι σκάσει, με minimal αλλαγές. Σημεία με το μεγαλύτερο ρίσκο (APIs που γράφτηκαν χωρίς compiler):

- `ui/map/MapScreen.kt` (osmdroid): Kotlin property syntax σε `isTilesScaledToDpi`, `minZoomLevel`, `maxZoomLevel`, `zoomController`. SAM lambdas σε `Polyline/Polygon.setOnClickListener`, `Marker.setOnMarkerClickListener`, `addOnFirstLayoutListener`. `Polygon.setPoints(Polygon.pointsAsCircle(...))`. `controller.animateTo(GeoPoint, Double, Long)`.
- `ui/navigation/AstikaNavHost.kt`: `NavDestination.hasRoute<T>()`, `navigate(route: Any)` με το `Tab.route`.
- `ui/splash/AnimatedSplash.kt`: `PathParser().parsePathString(...).toPath()`, `PathMeasure.getSegment`, `withTransform` με `scale(..., pivot = ...)`.
- `ui/components/Components.kt`: `Surface(onClick = ...)` overload, `Segmented` (generic), `Modifier.weight(1f, fill = false)`.
- `AstikaApp.kt`: `Configuration.getInstance().apply { osmdroidBasePath = ...; osmdroidTileCache = ... }`.
- `app/build.gradle.kts`: `kotlinOptions` (deprecated warning, όχι error).
- Αν χρειαστεί upgrade AGP/Kotlin/BOM, κάν' το **αφού** περάσει το πρώτο build, σε ξεχωριστό commit.

## Βήμα 2: έλεγχος σε συσκευή

- Splash: παίζει μία φορά, δεν ξαναπαίζει σε αλλαγή γλώσσας. Με "Remove animations" δείχνει κατευθείαν την τελική εικόνα. Ταιριάζει με τα καρέ στο design (storyboard: 0, 400, 900, 1300, 1800 ms).
- Χάρτης: φορτώνουν tiles (light OSM, dark CARTO), φίλτρο γραμμής, tap σε στάση ανοίγει bottom sheet, tap σε κενό το κλείνει, κουμπί θέσης.
- "Κοντά μου": permission flow (allow, deny, location off).
- Αλλαγή γλώσσας από Ρυθμίσεις, αλλαγή theme, αγαπημένα επιβιώνουν restart.
- Status bar icons σωστά σε κάθε οθόνη, light και dark.
- Ωράριο γραμμής: κρύβει τα περασμένα εκτός από το προηγούμενο, μετά το τελευταίο δείχνει "Δεν υπάρχουν άλλα δρομολόγια σήμερα".

## Βήμα 3: πραγματικά δεδομένα

- Πηγή: **astikaktelkastorias.gr**. Το `robots.txt` του απαγορεύει automated access, οπότε **όχι scraping**. Ο owner θα αποθηκεύει τις σελίδες χειροκίνητα (Ctrl+S) ή θα στέλνει screenshots/PDF.
- Χρειάζεται parser (π.χ. `scripts/parse-astika.py` ή Kotlin JVM script) που διαβάζει τις αποθηκευμένες σελίδες και γράφει τα 4 JSON στο `app/src/main/assets/`, μαζί με validation:
  - κάθε `stop` που αναφέρεται σε γραμμή υπάρχει στο `stops.json`
  - τα `offset` αυξάνονται σε κάθε direction
  - οι ώρες είναι `HH:MM` και ταξινομημένες
- Αν δεν δίνουν συντεταγμένες στάσεων, θα μπουν χειροκίνητα από χάρτη.
- Ήδη γνωστό: σταθμός ΚΤΕΛ Υπεραστικών στην Αθανασίου Διάκου 14, περίπου `40.5216, 21.2601`.
- Όταν μπουν τα πραγματικά: `"placeholder": false` στο `meta.json`, και το banner φεύγει.

## Backlog (με σειρά προτεραιότητας)

1. Δρομολόγια μετά τα μεσάνυχτα: υποστήριξη `24:15` (όπως GTFS). Σήμερα ένα 00:15 θα μετρούσε στη λάθος μέρα. Θέλει αλλαγή στο `Schedule` + tests.
2. Πραγματικά shapes διαδρομών στον χάρτη αντί για ευθείες μεταξύ στάσεων (offline snap με OSRM σε script, αποθήκευση ως polyline στο JSON).
3. `announcements.json`: έκτακτες αλλαγές, απεργίες, εορταστικά ωράρια, με ημερομηνίες ισχύος. Banner στην Αρχική.
4. Planner "από Α σε Β" μόνο για απευθείας γραμμές.
5. App shortcuts (long-press στο icon) για αγαπημένες στάσεις.
6. Ειδοποίηση "5 λεπτά πριν": τοπικά με `AlarmManager`/WorkManager, χωρίς backend.
7. Φοιτητική ενότητα: τιμές εισιτηρίων, κάρτα.

## Πριν από Play Store

- `applicationId` / package είναι `com.michadasis.astikaktel`. Ο owner να αποφασίσει αν θα αλλάξει σε κάτι του org (`Kastoria-Dev-Core`) **πριν** το πρώτο release, γιατί μετά δεν αλλάζει.
- Signing config (keystore εκτός repo), release build με R8. Τα keep rules για kotlinx.serialization και τα routes είναι στο `proguard-rules.pro`, θέλουν έλεγχο σε release build.
- Privacy policy (το app ζητά τοποθεσία, δεν τη στέλνει πουθενά).
- OSM Tile Usage Policy: σε μεγάλη χρήση χρειάζεται δικός tile provider (MapTiler, Stadia κ.λπ.). Το CARTO free tier έχει όρια.
- Disclaimer "ανεπίσημη εφαρμογή" υπάρχει ήδη στις Ρυθμίσεις. Ιδανικά να υπάρξει επικοινωνία/άδεια από το Αστικό ΚΤΕΛ για τα δεδομένα.

## Σχετικά assets εκτός repo

- Design canvas (claude.ai artifact) με τα 4 icon concepts, το B3 refined, μεγέθη/maskable, το live splash και το storyboard.
- Icon pack B3 (PNG/SVG για web) και το PWA project, σε ξεχωριστά zip.
