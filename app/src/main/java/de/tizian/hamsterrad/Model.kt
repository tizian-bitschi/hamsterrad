package de.tizian.hamsterrad

/** Ein Rahmen des Bands, so wie es ihn schickt. */
data class Live(
    val speedKmh: Double,
    val timeS: Int,
    val distM: Int,
    val kcal: Double,
    val hr: Int,
)

/** Laufende Session, relativ zur Startmarke. */
data class Now(
    val seconds: Int,
    val distanceM: Int,
    val speedKmh: Double,
    val hr: Int,
    val kcal: Double,
)

data class Session(
    val start: Long,
    val end: Long,
    val seconds: Int,
    val distanceM: Int,
    val kcal: Double,
    val hrAvg: Int,
    val hrMax: Int,
)
