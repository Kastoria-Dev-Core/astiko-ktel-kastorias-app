package com.kastoriadevs.astikoktel.app.ui.navigation

import kotlinx.serialization.Serializable

// The screens, as type-safe Navigation Compose destinations. Ids are Line.id and Stop.id.

/** Favourites, search, stops nearby and "Where do you want to go?"; the start destination */
@Serializable
object HomeRoute

/** Every line */
@Serializable
object LinesRoute

/** The map, showing everything or one line, optionally with a stop's card open */
@Serializable
data class MapRoute(
    /** Show only this line; null for all */
    val lineId: String? = null,
    /** Open this stop's card and centre on it */
    val stopId: String? = null,
)

/** Language, theme, voice and About */
@Serializable
object SettingsRoute

/** The operator's announcements */
@Serializable
object AnnouncementsRoute

/** About the app and the operator's contact details */
@Serializable
object AboutRoute

/** A line's timetable */
@Serializable
data class LineRoute(
    /** The line to show */
    val lineId: String,
    /** The direction to show first; null for the first one */
    val dirId: String? = null,
)

/** A stop's next departures */
@Serializable
data class StopRoute(
    /** The stop to show */
    val stopId: String,
)

/** A line's stops in order, with when the next bus gets to each */
@Serializable
data class RouteStopsRoute(
    /** The line to show */
    val lineId: String,
    /** The direction to show first; null for the first one */
    val dirId: String? = null,
)
