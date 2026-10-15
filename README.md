<div align="center">
  <img src="docs/icon.svg" width="100" />
  <h1>Kastoria City Buses</h1>
  <p>Every city bus line, stop and timetable in Kastoria, right in your pocket.</p>
</div>

## Why this exists

The city bus timetables of Kastoria live on the operator's website as separate tables per line, and the times you see there are only the departures from the start of the route. If you're standing at a stop in the middle of town, you're left guessing when the bus actually gets to you. This app puts every line and stop on your phone and tells you when the next bus leaves from where you are.

## What it does

Open it and you see your favourite stops, each with its next departures and how many minutes away they are. It knows whether today runs on the weekday, Saturday or Sunday and holiday timetable, including the moving holidays around Orthodox Easter, so you never have to check. From there you can browse every line with its full timetable and stops, or find a stop on the map and see which buses pass by.

## Installation

Grab the latest APK from the releases page and install it on your Android phone.

https://github.com/Kastoria-Dev-Core/astiko-ktel-kastorias-app/releases

Every change merged into `main` is built and published there automatically, and each release installs over the previous one. It needs Android 8.0 or newer. There's no iOS build.

## Features

* Next departures for any stop, with a countdown for buses leaving within the hour
* Favourite stops on the home screen, remembered across restarts
* Search for any stop by name, in Greek or English, with or without accents
* Every line with its full timetable for each direction, folding away the buses that already left except the last one
* "Where do you want to go?" on the home screen: pick the centre, Maniakoi or the university and see which buses from the stops near you get there, and when. Your location stays on the device and is never sent anywhere
* Buses on the map at positions estimated from the timetable, moving along the streets, until the buses get GPS
* A map of all 209 stops and 18 lines drawn along the streets, with a filter per line and a card with the next buses when you tap a stop
* Picks the weekday, Saturday or Sunday and holiday timetable on its own, including Clean Monday, Good Friday, Easter Monday and Holy Spirit Monday
* Announcements from the operator (strikes, holiday timetables, route changes) with their posters, a dot on the bell when there's a current one, and a full screen viewer with pinch to zoom
* Works offline: the timetables are built into the app, and the last announcements are cached
* Dark mode and light mode, or follow the system, with a dark map too
* Greek and English, switchable from Settings
* An animated splash screen that draws a route, its stops and a bus while the timetables load

## How the code is laid out

```
app/src/main/
  assets/
    stops.json           every stop with its name in Greek and English and its coordinates
    lines.json           every line, its colour and its stops in order for each direction
    timetables.json      departure times per line, direction and day type
    meta.json            summer timetable period and fixed public holidays

  java/com/kastoriadevs/astikoktel/app/
    AstikaApp.kt         creates the app's repositories once, sets up the map tile cache
    MainActivity.kt      theme, splash and navigation

    data/
      Models.kt          the data types the JSON files are read into
      TransitRepository.kt  reads the JSON files from assets
      Announcements.kt   downloads the announcements feed and keeps a copy on the device
      PrefsRepository.kt favourites and theme, saved with DataStore
      LocationClient.kt  asks for the current location once

    domain/
      Schedule.kt        works out the day type, the season, holidays and Easter, and the next departures
      Geo.kt             distances and the nearest stops

    ui/
      home/              the home screen: search, where to go, favourites, current announcements
      lines/ line/       the list of lines and a single line with its timetable and stops
      stop/              a single stop with its next departures
      map/               the map, the line filter and the stop card
      announcements/     the announcements list and the image viewer
      settings/          language, theme and about
      splash/            the animated splash screen
      components/        shared pieces: line badges, stop cards, departure rows, headers
      navigation/        the bottom bar and the routes between screens
      theme/             the colours, taken from the app icon, for light and dark

  res/values/            English text
  res/values-el/         Greek text

app/src/test/            unit tests for the timetable logic and the announcements

data/kastoria_bus_data.csv    the raw dataset the JSON files in assets were built from
```

## Running it yourself

You'll need Android Studio, which comes with the Android SDK and the JDK the build uses.

```bash
git clone https://github.com/Kastoria-Dev-Core/astiko-ktel-kastorias-app.git
```

Open the folder in Android Studio, wait for the Gradle sync to finish, and press Run with a phone or emulator selected.

To run the unit tests:

```bash
./gradlew test
```

To build an APK for testing:

```bash
./gradlew assembleDebug
```

## Updating the timetables

The lines, stops and timetables are the four JSON files in `app/src/main/assets/`, so updating them means editing those files and merging the change. The new release is built and published on its own.

* A stop has an `id`, a `name` in `el` and `en`, and `lat`/`lng`
* A line has its `directions`, and each direction lists its stops in order with an `offset`, the minutes after the bus leaves the first stop
* A timetable lists the `departures` from the first stop as `"HH:MM"` for one line, direction, `service` (`weekday`, `saturday` or `sunday`) and `season` (`winter` or `summer`). If a line has no summer timetable, the winter one is used all year
* `meta.json` sets the summer period and the fixed holidays. The holidays that move with Easter are calculated by the app

## Announcements

The announcements come from the operator's website. A small Vercel Cron job, `astiko-ktel-announcements`, reads the site once a day and serves them as a JSON feed, which the app downloads when it opens. No app update is needed when a new announcement is posted.

The app reads the feed from the address in `announcementsUrl`. To point it somewhere else, set it in `gradle.properties`:

```properties
announcementsUrl=https://your-project.vercel.app/api/announcements?kind=service
```

## Releases

`.github/workflows/release.yml` runs on every push to `main`. It runs the tests, builds the APK and publishes it as a release named after the last version tag and the build number, for example `v1.0.0 build 7`. Pushing a tag like `v1.1.0` publishes an official version.

The APK is signed with the release key stored in the repository secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`). The keystore itself is never committed. Keep a backup of it outside the repo, because without it the app can't be updated anymore.

Pull requests are checked by `.github/workflows/build.yml`, which runs the tests and attaches a debug APK to the run.

## Disclaimer

This is an unofficial app. It isn't affiliated with Αστικό ΚΤΕΛ Καστοριάς. Always confirm the times at the stop.

## Credits

Built by [Ioannis Michadasis](https://github.com/michadasis/), [Giorgos Gasparis](https://github.com/GasparisGiorgos) and [Konstantinos Theologou](https://github.com/the0099), as [Kastoria Dev Core](https://github.com/Kastoria-Dev-Core).

Timetables and announcements from [Αστικό ΚΤΕΛ Καστοριάς](https://astikoktelkastorias.gr/). Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.
