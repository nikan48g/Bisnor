package com.hnn.bisnor.data.repository

import android.content.Context
import com.hnn.bisnor.data.model.Episode
import com.hnn.bisnor.data.model.HomeFeedResponse
import com.hnn.bisnor.data.model.HomeSection
import com.hnn.bisnor.data.model.MediaItem
import com.hnn.bisnor.data.model.Season
import com.hnn.bisnor.data.model.StreamLink
import com.hnn.bisnor.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    private val networkClient = NetworkClient(context)

    suspend fun getHomeFeed(): Result<HomeFeedResponse> = withContext(Dispatchers.IO) {
        try {
            val response = networkClient.apiService.getHomeFeed()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // Fallback to rich curated mock data if network endpoint is unreachable/mock
                Result.success(getCuratedSampleHomeFeed())
            }
        } catch (e: Exception) {
            // Return curated high-quality sample data on network/offline fallback
            Result.success(getCuratedSampleHomeFeed())
        }
    }

    suspend fun searchMedia(query: String, filterGenre: String? = null): Result<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            try {
                val response = networkClient.apiService.searchMedia(query = query, genre = filterGenre)
                if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                    Result.success(response.body()!!)
                } else {
                    val allItems = getAllSampleItems()
                    val filtered = allItems.filter {
                        (it.title.contains(query, ignoreCase = true) ||
                                (it.originalTitle?.contains(query, ignoreCase = true) == true) ||
                                it.actors.any { a -> a.contains(query, ignoreCase = true) }) &&
                                (filterGenre == null || filterGenre == "همه" || it.genres.contains(filterGenre))
                    }
                    Result.success(filtered)
                }
            } catch (e: Exception) {
                val allItems = getAllSampleItems()
                val filtered = allItems.filter {
                    (it.title.contains(query, ignoreCase = true) ||
                            (it.originalTitle?.contains(query, ignoreCase = true) == true) ||
                            it.actors.any { a -> a.contains(query, ignoreCase = true) }) &&
                            (filterGenre == null || filterGenre == "همه" || it.genres.contains(filterGenre))
                }
                Result.success(filtered)
            }
        }

    suspend fun getMediaDetail(mediaId: String): Result<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val response = networkClient.apiService.getMediaDetail(mediaId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val found = getAllSampleItems().find { it.id == mediaId }
                    ?: getCuratedSampleHomeFeed().featured.first()
                Result.success(found)
            }
        } catch (e: Exception) {
            val found = getAllSampleItems().find { it.id == mediaId }
                ?: getCuratedSampleHomeFeed().featured.first()
            Result.success(found)
        }
    }

    private fun getAllSampleItems(): List<MediaItem> {
        val feed = getCuratedSampleHomeFeed()
        val list = mutableListOf<MediaItem>()
        list.addAll(feed.featured)
        feed.sections.forEach { list.addAll(it.items) }
        return list.distinctBy { it.id }
    }

    fun getCuratedSampleHomeFeed(): HomeFeedResponse {
        val dune2 = MediaItem(
            id = "dune-part-two",
            title = "تل‌ماسه: بخش دوم",
            originalTitle = "Dune: Part Two",
            poster = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            backdrop = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5200FR.jpg",
            type = "movie",
            rating = "8.6",
            year = "2024",
            genres = listOf("اکشن", "علمی تخیلی", "ماجراجویی"),
            storyline = "پل اتریدیس با چانی و فرمن‌ها متحد می‌شود در حالی که به دنبال انتقام از توطئه‌گرانی است که خانواده‌اش را نابود کردند. او در مواجهه با انتخابی میان عشق زندگی‌اش و سرنوشت جهان شناخته‌شده، تلاش می‌کند از آینده‌ای وحشتناک که فقط او می‌تواند پیش‌بینی کند، جلوگیری کند.",
            actors = listOf("تیموتی شالامی", "زندایا", "ربکا فرگوسن", "خاویر باردم"),
            director = "دنی ویلنوو",
            country = "آمریکا",
            duration = "۱۶۶ دقیقه",
            isDubbed = true,
            playLinks = listOf(
                StreamLink("1080p Full HD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "دوبله فارسی دو زبانه", "2.8 GB"),
                StreamLink("720p HD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "دوبله فارسی", "1.4 GB"),
                StreamLink("480p SD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", "زیرنویس فارسی چسبیده", "850 MB")
            )
        )

        val oppenheimer = MediaItem(
            id = "oppenheimer-2023",
            title = "اوپنهایمر",
            originalTitle = "Oppenheimer",
            poster = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
            backdrop = "https://image.tmdb.org/t/p/original/rM5Y09xC99XNI1xKOX7aqz254bO.jpg",
            type = "movie",
            rating = "8.9",
            year = "2023",
            genres = listOf("بیوگرافی", "درام", "تاریخی"),
            storyline = "داستان فیزیکدان نظری آمریکایی، جی. رابرت اوپنهایمر، ملقب به پدر بمب اتمی و رهبر پروژه منهتن در طول جنگ جهانی دوم که به توسعه اولین سلاح‌های هسته‌ای انجامید.",
            actors = listOf("کیلین مورفی", "امیلی بلانت", "مت دیمون", "رابرت داونی جونیور"),
            director = "کریستوفر نولان",
            country = "آمریکا و بریتانیا",
            duration = "۱۸۰ دقیقه",
            isDubbed = true,
            playLinks = listOf(
                StreamLink("1080p Full HD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", "دوبله اختصاصی بیسنور", "3.1 GB"),
                StreamLink("720p HD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "زیرنویس فارسی چسبیده", "1.6 GB")
            )
        )

        val interstellar = MediaItem(
            id = "interstellar-2014",
            title = "میان‌ستاره‌ای",
            originalTitle = "Interstellar",
            poster = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
            backdrop = "https://image.tmdb.org/t/p/original/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
            type = "movie",
            rating = "8.7",
            year = "2014",
            genres = listOf("علمی تخیلی", "درام", "ماجراجویی"),
            storyline = "در حالی که بقای بشر روی زمین به خطر افتاده، گروهی از فضانوردان از طریق یک کرم‌چاله در نزدیکی زحل سفر می‌کنند تا سیاره‌ای جدید و قابل سکونت برای انسان‌ها پیدا کنند.",
            actors = listOf("متیو مک‌کانهی", "آن هاتاوی", "جسیکا چستین", "مایکل کین"),
            director = "کریستوفر نولان",
            country = "آمریکا",
            duration = "۱۶۹ دقیقه",
            isDubbed = true,
            playLinks = listOf(
                StreamLink("1080p BluRay", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "دوبله فارسی", "2.5 GB")
            )
        )

        val severance = MediaItem(
            id = "severance-series",
            title = "جداسازی",
            originalTitle = "Severance",
            poster = "https://image.tmdb.org/t/p/w500/815bZ3kQj42h3j5HkLw7j2h9Z.jpg",
            backdrop = "https://image.tmdb.org/t/p/original/bKxiLRP0Qm2Jw4sE1aG74j5s0b7.jpg",
            type = "series",
            rating = "8.7",
            year = "2022–2025",
            genres = listOf("درام", "رازآلود", "علمی تخیلی"),
            storyline = "مارک تیمی از کارمندان اداره لومن را هدایت می‌کند که خاطراتشان از طریق جراحی بین کار و زندگی شخصی تفکیک شده است. وقتی یک همکار مرموز ظاهر می‌شود، سفری برای کشف حقیقت آغاز می‌گردد.",
            actors = listOf("آدام اسکات", "پاتریشا آرکت", "جان تورتورو", "کریستوفر واکن"),
            director = "بن استیلر",
            country = "آمریکا",
            duration = "فصل ۱ و ۲",
            isDubbed = true,
            seasons = listOf(
                Season(
                    seasonNumber = 1,
                    title = "فصل اول",
                    episodes = (1..9).map { epNum ->
                        Episode(
                            id = "sev-s1-e$epNum",
                            episodeNumber = epNum,
                            title = "قسمت $epNum: اخبار تازه از لومن",
                            thumbnail = "https://image.tmdb.org/t/p/w500/bKxiLRP0Qm2Jw4sE1aG74j5s0b7.jpg",
                            playLinks = listOf(
                                StreamLink("1080p", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", "دوبله فارسی", "950 MB"),
                                StreamLink("720p", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", "زیرنویس چسبیده", "520 MB")
                            )
                        )
                    }
                ),
                Season(
                    seasonNumber = 2,
                    title = "فصل دوم (جدید)",
                    episodes = (1..10).map { epNum ->
                        Episode(
                            id = "sev-s2-e$epNum",
                            episodeNumber = epNum,
                            title = "قسمت $epNum: بیداری در دنیای واقعی",
                            thumbnail = "https://image.tmdb.org/t/p/w500/bKxiLRP0Qm2Jw4sE1aG74j5s0b7.jpg",
                            playLinks = listOf(
                                StreamLink("1080p", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4", "دوبله فارسی", "1.1 GB")
                            )
                        )
                    }
                )
            )
        )

        val arcane = MediaItem(
            id = "arcane-series",
            title = "آرکین",
            originalTitle = "Arcane",
            poster = "https://image.tmdb.org/t/p/w500/fqldf2t8ztc9aiwn397rWWcfGgC.jpg",
            backdrop = "https://image.tmdb.org/t/p/original/uDgy6hyPd82kOHh6I95FLtLnj6p.jpg",
            type = "series",
            rating = "9.0",
            year = "2021–2024",
            genres = listOf("انیمیشن", "اکشن", "ماجراجویی", "علمی تخیلی"),
            storyline = "در میان تنش‌های روزافزون میان دو شهر پیلتوور و زان، دو خواهر در دو سوی متضاد جنگی بر سر فناوری‌های جادویی و باورهای متناقض قرار می‌گیرند.",
            actors = listOf("هیلی استاینفلد", "الا پورنل", "کیتی لیونگ"),
            country = "آمریکا و فرانسه",
            duration = "۲ فصل کامل",
            isDubbed = true,
            seasons = listOf(
                Season(
                    seasonNumber = 1,
                    title = "فصل اول",
                    episodes = (1..9).map { epNum ->
                        Episode(
                            id = "arc-s1-e$epNum",
                            episodeNumber = epNum,
                            title = "قسمت $epNum: خواهران پیلتوور",
                            thumbnail = "https://image.tmdb.org/t/p/w500/uDgy6hyPd82kOHh6I95FLtLnj6p.jpg",
                            playLinks = listOf(
                                StreamLink("1080p 60fps", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "دوبله اختصاصی", "850 MB")
                            )
                        )
                    }
                )
            )
        )

        val insideOut2 = MediaItem(
            id = "inside-out-2",
            title = "درون و بیرون ۲",
            originalTitle = "Inside Out 2",
            poster = "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
            backdrop = "https://image.tmdb.org/t/p/original/xg270UXie0vgEG5OD8797BAi3lc.jpg",
            type = "movie",
            rating = "7.8",
            year = "2024",
            genres = listOf("انیمیشن", "خانوادگی", "ماجراجویی", "کمدی"),
            storyline = "رایلی اکنون یک نوجوان است و مرکز فرماندهی احساسات او دستخوش تخریب ناگهانی می‌شود تا فضایی برای احساسات کاملاً غیرمنتظره جدید، به ویژه اضطراب باز شود!",
            actors = listOf("امی پولر", "مایا هاوک", "کنسینگتون تالمن"),
            director = "کلسی مان",
            country = "آمریکا (پیکسار)",
            duration = "۹۶ دقیقه",
            isDubbed = true,
            playLinks = listOf(
                StreamLink("1080p Full HD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "دوبله فارسی گلوری", "1.8 GB")
            )
        )

        return HomeFeedResponse(
            featured = listOf(dune2, severance, oppenheimer, arcane),
            sections = listOf(
                HomeSection(title = "جدیدترین فیلم‌های سینمایی", type = "movie", items = listOf(dune2, oppenheimer, insideOut2, interstellar)),
                HomeSection(title = "سریال‌های داغ و پرطرفدار", type = "series", items = listOf(severance, arcane)),
                HomeSection(title = "برترین آثار با دوبله فارسی", type = "movie", items = listOf(interstellar, dune2, insideOut2)),
                HomeSection(title = "انیمیشن‌های برتر خانوادگی", type = "movie", items = listOf(insideOut2, arcane))
            )
        )
    }
}
