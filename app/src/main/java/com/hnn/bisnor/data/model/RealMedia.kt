package com.hnn.bisnor.data.model

import java.io.Serializable

data class RealMedia(
    val id: Int = 0,
    val type: String = "movie", // "movie" or "serie"
    val title: String = "",
    val description: String = "",
    val year: Int = 0,
    val imdb: Double = 0.0,
    val rating: Double = 0.0,
    val duration: String? = null,
    val image: String = "",
    val cover: String = "",
    val genres: List<RealGenre> = emptyList(),
    val sources: List<RealSource> = emptyList(),
    val country: List<RealCountry> = emptyList()
) : Serializable

data class RealGenre(
    val id: Int = 0,
    val title: String = ""
) : Serializable

data class RealCountry(
    val id: Int = 0,
    val title: String = "",
    val image: String = ""
) : Serializable

data class RealSource(
    val id: Int = 0,
    val quality: String = "",
    val type: String = "",
    val url: String = ""
) : Serializable

data class RealSeason(
    val id: Int = 0,
    val title: String = "",
    val episodes: List<RealEpisode> = emptyList()
) : Serializable

data class RealEpisode(
    val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val duration: String? = null,
    val image: String = "",
    val sources: List<RealSource> = emptyList()
) : Serializable
