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
    val country: List<RealCountry> = emptyList(),
    val classification: String? = null,
    val people: List<RealPerson> = emptyList(),
    // "iranflix", "next" or "both" for titles matched in both catalogues.
    val provider: String = "iranflix",
    val nextId: Int = 0,
    val iranflixId: Int = 0,
    // Playback links of the Next Movie copy, kept aside so the IranFlix row stays primary.
    val nextSources: List<RealSource> = emptyList()
) : Serializable {
    val isSeries: Boolean
        get() = type.startsWith("seri", ignoreCase = true)
}

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
    val url: String = "",
    val origin: String = ""
) : Serializable

data class RealSeason(
    val id: Int = 0,
    val title: String = "",
    val episodes: List<RealEpisode> = emptyList(),
    val number: Int = 0
) : Serializable

/** A real credit parsed from the provider payload or its description block. */
data class RealPerson(
    val name: String = "",
    val role: String = "actor", // actor | director | writer
    val photo: String = ""
) : Serializable

data class RealEpisode(
    val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val duration: String? = null,
    val image: String = "",
    val sources: List<RealSource> = emptyList(),
    val number: Int = 0
) : Serializable
