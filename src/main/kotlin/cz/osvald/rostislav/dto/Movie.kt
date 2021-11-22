package cz.osvald.rostislav.dto

import java.time.LocalDateTime

data class Movie(
    val id: Int,
    val name: String,
    val createdAt: String,
    val viewedAt: String?
)
