#!/bin/bash
# Screenshots of the main screens on the Android emulator, in Greek and English, light and dark, for the
# README. Used by .github/workflows/screenshots.yml, inside the emulator it starts.
#
#   scripts/screenshots.sh <apk> <output folder>
#
# MainActivity opens straight on a screen with the "screen" extra (see AstikaNavHost), the language is
# set by the app itself with the lang extra, and dark mode with `cmd uimode`, since the app follows the system theme.

set -u
APK="$1"
OUT="$2"
PKG=com.kastoriadevs.astikoktel.app
mkdir -p "$OUT"

adb install -r "$APK"
# Location allowed and set to the Αφετηρία, so "near me" works
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION || true
adb emu geo fix 21.2619 40.5214 || true
# A tidy status bar
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4 -e datatype none > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null

shot() {
  local name="$1" screen="$2"
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/.MainActivity" -e screen "$screen" > /dev/null
  # The splash, the timetables, the map tiles
  sleep 9
  adb exec-out screencap -p > "$OUT/$name.png" && echo "$name"
}

# The app sets its own language with -e lang (MainActivity), like picking it in Settings
lang() {
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/.MainActivity" -e lang "$1" > /dev/null
  sleep 9
}

lang el
adb shell cmd uimode night no
shot el-home home
shot el-stop stop:16001
shot el-line line:04
shot el-route route:04
shot el-lines lines
shot el-map map
shot el-map-line mapline:04
shot el-announcements announcements
shot el-settings settings
adb shell cmd uimode night yes
shot el-home-dark home
shot el-stop-dark stop:16001
shot el-map-dark map
adb shell cmd uimode night no
lang en
shot en-home home
exit 0
