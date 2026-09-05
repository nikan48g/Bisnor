package com.hnn.bisnor.data.network

import com.hnn.bisnor.data.model.HomeFeedResponse
import com.hnn.bisnor.data.model.MediaItem
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface IranflixApiService {

    @GET("api/v1/home")
    suspend fun getHomeFeed(): Response<HomeFeedResponse>

    @GET("api/v1/movies/latest")
    suspend fun getLatestMovies(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<List<MediaItem>>

    @GET("api/v1/series/popular")
    suspend fun getPopularSeries(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<List<MediaItem>>

    @GET("api/v1/media/{id}")
    suspend fun getMediaDetail(
        @Path("id") mediaId: String
    ): Response<MediaItem>

    @GET("api/v1/search")
    suspend fun searchMedia(
        @Query("q") query: String,
        @Query("type") type: String? = null,
        @Query("genre") genre: String? = null
    ): Response<List<MediaItem>>
}
