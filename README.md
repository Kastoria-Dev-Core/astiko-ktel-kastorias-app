# Αστικά ΚΤΕΛ Καστοριάς (Android)

Native Android app για τα αστικά λεωφορεία της Καστοριάς: γραμμές, ωράρια, επόμενα δρομολόγια ανά στάση, στάσεις κοντά μου, χάρτης OpenStreetMap, αγαπημένα, Ελληνικά/English, light/dark.

> **Placeholder data.** Οι γραμμές, οι στάσεις, οι συντεταγμένες και τα ωράρια στο `app/src/main/assets/` είναι δοκιμαστικά. Όσο το `meta.json` έχει `"placeholder": true`, το app δείχνει banner.

## Stack

- Kotlin, Jetpack Compose, Material 3, single activity
- Navigation Compose με type-safe routes (`@Serializable`)
- ViewModel + StateFlow, manual DI (`AppContainer` στο `AstikaApp`)
- DataStore Preferences (αγαπημένα, theme)
- Per-app language μέσω AppCompat (`AppCompatDelegate.setApplicationLocales`), εμφανίζεται και στις ρυθμίσεις του Android 13+
- kotlinx.serialization για τα JSON
- osmdroid με OpenStreetMap tiles, CARTO dark tiles στο dark mode. Δεν χρειάζεται API key.
- Fused Location Provider για "Κοντά μου" και τη θέση στον χάρτη
- java.time για όλη τη λογική ωραρίων (minSdk 26)

Εκδόσεις (gradle/libs.versions.toml): AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Gradle 8.11.1, compileSdk/targetSdk 35. Αν το Android Studio προτείνει AGP Upgrade Assistant, είναι ασφαλές να το τρέξεις.

## Run

1. Android Studio → **Open** → φάκελος `astika-ktel-android`
2. Περίμενε το Gradle sync
3. Run σε emulator ή συσκευή (Android 8.0+)

Unit tests για τη λογική ωραρίων:

```bash
./gradlew test
```

## Δομή

```
app/src/main/
  assets/                  stops.json, lines.json, timetables.json, meta.json
  java/com/kastoriadevs/astikoktel/app/
    AstikaApp.kt           Application + AppContainer (manual DI), osmdroid config
    MainActivity.kt        edge-to-edge, theme, NavHost
    data/
      Models.kt            @Serializable models, TransitData lookups, Departure
      TransitRepository.kt φορτώνει τα JSON από assets
      PrefsRepository.kt   DataStore: αγαπημένα, theme
      LocationClient.kt    one-shot θέση (Fused)
    domain/
      Schedule.kt          καθημερινή/Σάββατο/Κυριακή-αργία, θερινό/χειμερινό, επόμενα δρομολόγια, Πάσχα
      Geo.kt               αποστάσεις, κοντινές στάσεις
    ui/
      theme/Theme.kt       παλέτα από το app icon (B3), light και dark
      components/          LineBadge, StopCard, DepartureRow, ScreenHeader, Segmented, ...
      navigation/          routes, bottom bar, NavHost
      splash/              animated splash (Canvas + PathMeasure), παίζει όσο φορτώνουν τα δεδομένα
      home/ lines/ line/ stop/ map/ settings/
  res/
    drawable/              launcher icon (vector), monochrome icon, ic_logo, ic_route, ic_map
    values/ values-el/     strings (English default, Ελληνικά)
app/src/test/              ScheduleTest (JUnit)
```

## Data format

Ίδιο με το PWA. Για να μπουν τα πραγματικά δεδομένα αντικαθιστάς τα 4 JSON στο `assets/` και βάζεις `"placeholder": false`.

- `stops.json`: `{ "id", "name": { "el", "en" }, "lat", "lng" }`
- `lines.json`: γραμμές με `directions`, κάθε direction με στάσεις στη σειρά και `offset` σε λεπτά από την αφετηρία
- `timetables.json`: `{ "line", "direction", "service": "weekday|saturday|sunday", "season": "winter|summer", "departures": ["06:40", ...] }`. Αν λείπει θερινό, χρησιμοποιείται το χειμερινό.
- `meta.json`: περίοδος θερινού και σταθερές αργίες. Οι κινητές αργίες (Καθαρά Δευτέρα, Μεγάλη Παρασκευή, Δευτέρα του Πάσχα, Αγίου Πνεύματος) υπολογίζονται από το Ορθόδοξο Πάσχα.

## Splash

- Το system splash (Android 12+) είναι σκέτο χρώμα λίμνης, χωρίς icon, και περνάει απευθείας στο animated splash του app.
- Intro περίπου 1,8 s: κύματα, η γραμμή σχεδιάζεται, στάσεις, λεωφορείο, τίτλος και μπάρα φόρτωσης.
- Τα JSON φορτώνουν σε background thread. Όταν τελειώσουν το intro και η φόρτωση, το splash μεγαλώνει, σβήνει και αποκαλύπτει την Αρχική.
- Με "Αφαίρεση κινούμενων εικόνων" στο σύστημα, δείχνει κατευθείαν την τελική εικόνα.
- Δεν ξαναπαίζει όταν ξαναδημιουργείται το activity, π.χ. σε αλλαγή γλώσσας.

## Χάρτης

- Light: `tile.openstreetmap.org` (OSM Tile Usage Policy: User-Agent με το application id, local cache, attribution στον χάρτη)
- Dark: CARTO `dark_all` (δωρεάν για μικρή χρήση, attribution OSM + CARTO)
- Για μεγάλη χρήση ή δημοσίευση στο Play Store, σκέψου δικό σου tile server ή πάροχο (π.χ. MapTiler, Stadia).

## Σημειώσεις

- Ανεπίσημη εφαρμογή, δεν συνδέεται με το Αστικό ΚΤΕΛ Καστοριάς.
- Η λογική ωραρίων (`Schedule`, `Geo`) έχει unit tests που περνάνε. Το UI γράφτηκε χωρίς πρόσβαση σε Gradle/Google Maven, οπότε το πρώτο build γίνεται στο Android Studio.
