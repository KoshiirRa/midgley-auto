package net.n2yti.midgley.auto.data.repository

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null,
    val isCached: Boolean = false,
    val cacheAgeHours: Double = 0.0
) {
    class Success<T>(data: T, isCached: Boolean = false, cacheAgeHours: Double = 0.0) : Resource<T>(data, null, isCached, cacheAgeHours)
    class Error<T>(message: String, data: T? = null, isCached: Boolean = false, cacheAgeHours: Double = 0.0) : Resource<T>(data, message, isCached, cacheAgeHours)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}
