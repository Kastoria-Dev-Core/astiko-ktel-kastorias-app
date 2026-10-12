package com.michadasis.astikaktel.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object LinesRoute

@Serializable
data class MapRoute(val lineId: String? = null, val stopId: String? = null)

@Serializable
object SettingsRoute

@Serializable
data class LineRoute(val lineId: String, val dirId: String? = null)

@Serializable
data class StopRoute(val stopId: String)
