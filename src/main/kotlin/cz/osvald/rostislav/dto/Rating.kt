package cz.osvald.rostislav.dto

data class Rating (
    val id: Int?,
    val value: Short,
    val movieId: Int,
    val userId: Int,
)