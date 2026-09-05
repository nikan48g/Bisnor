package com.hnn.bisnor.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MediaItem(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("original_title", alternate = ["originalTitle", "fa_name"])
    val originalTitle: String? = null,

    @SerializedName("poster", alternate = ["poster_url", "image", "cover"])
    val poster: String = "",

    @SerializedName("backdrop", alternate = ["backdrop_url", "banner", "landscape_image"])
    val backdrop: String? = null,

    @SerializedName("type")
    val type: String = "movie", // "movie" or "series"

    @SerializedName("rating", alternate = ["imdb_rate", "rate", "imdb"])
    val rating: String = "0.0",

    @SerializedName("year", alternate = ["release_date", "publish_date"])
    val year: String = "",

    @SerializedName("genres", alternate = ["genre", "categories"])
    val genres: List<String> = emptyList(),

    @SerializedName("storyline", alternate = ["description", "plot", "summary", "overview"])
    val storyline: String = "",

    @SerializedName("actors", alternate = ["cast", "stars"])
    val actors: List<String> = emptyList(),

    @SerializedName("director")
    val director: String? = null,

    @SerializedName("country")
    val country: String? = null,

    @SerializedName("duration", alternate = ["runtime"])
    val duration: String? = null,

    @SerializedName("is_dubbed", alternate = ["has_dubbed", "dubbed"])
    val isDubbed: Boolean = false,

    @SerializedName("seasons")
    val seasons: List<Season>? = null,

    @SerializedName("play_links", alternate = ["links", "sources", "download_links"])
    val playLinks: List<StreamLink> = emptyList()
) : Serializable

data class Season(
    @SerializedName("season_number", alternate = ["season", "id"])
    val seasonNumber: Int = 1,

    @SerializedName("title")
    val title: String = "فصل اول",

    @SerializedName("episodes")
    val episodes: List<Episode> = emptyList()
) : Serializable

data class Episode(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("episode_number", alternate = ["episode"])
    val episodeNumber: Int = 1,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("thumbnail", alternate = ["image"])
    val thumbnail: String? = null,

    @SerializedName("play_links", alternate = ["links", "sources"])
    val playLinks: List<StreamLink> = emptyList()
) : Serializable

data class StreamLink(
    @SerializedName("quality", alternate = ["resolution", "name", "label"])
    val quality: String = "1080p",

    @SerializedName("url", alternate = ["link", "file", "stream_url", "direct_link"])
    val url: String = "",

    @SerializedName("type", alternate = ["audio_type"])
    val type: String = "زیرنویس فارسی چسبیده",

    @SerializedName("size")
    val size: String? = null
) : Serializable

data class HomeFeedResponse(
    @SerializedName("featured_banners", alternate = ["sliders", "featured", "hero"])
    val featured: List<MediaItem> = emptyList(),

    @SerializedName("sections")
    val sections: List<HomeSection> = emptyList()
)

data class HomeSection(
    @SerializedName("title")
    val title: String = "",

    @SerializedName("type")
    val type: String = "movie",

    @SerializedName("items")
    val items: List<MediaItem> = emptyList()
)
