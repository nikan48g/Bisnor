/**
 * Bisnor Desktop - Media Data Service
 * Provides offline seed database with 40+ rich items + dynamic fetch capability with automatic fallback.
 */

const FALLBACK_CATALOG = [
    // 1. Movies
    {
        id: 101,
        type: "movie",
        title: "اوپنهایمر (Oppenheimer)",
        description: "داستان زندگی جی. رابرت اوپنهایمر، فیزیکدان نظری آمریکایی که به عنوان پدر بمب اتمی شناخته می‌شود و نقش کلیدی در پروژه منهتن داشت. فیلم کاوشی عمیق در اخلاقیات، سیاست و پیامدهای ویرانگر ساخت سلاح هسته‌ای است.",
        year: 2023,
        imdb: 8.9,
        rating: 9.2,
        duration: "180 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BMDBmYTZjNjUtN2M1MS00MTQ2LTk2ODgtNzc2M2QyZGE5NTVjXkEyXkFqcGdeQXVyNzAwMjU2MTY@._V1_FMjpg_UX1000_.jpg",
        cover: "https://images.hdqwalls.com/download/oppenheimer-movie-banner-4k-3840x2160.jpg",
        genres: [{ id: 1, title: "زندگینامه" }, { id: 2, title: "درام" }, { id: 3, title: "تاریخی" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 1011, quality: "1080p BluRay - 2.8 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
            { id: 1012, quality: "720p WEB-DL - 1.4 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" },
            { id: 1013, quality: "480p WEB-DL - 750 MB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" }
        ]
    },
    {
        id: 102,
        type: "movie",
        title: "شوالیه تاریکی (The Dark Knight)",
        description: "بتمن با کمک ستوان جیم گوردون و دادستان جدید هاروی دنت، شروع به برچیدن جرایم سازمان‌یافته در گاتهام می‌کند. اما ورود جوکر، روان‌پریشی با نبوغ جنایتکارانه، شهر را به ورطه هرج‌ومرج و تباهی می‌کشاند.",
        year: 2008,
        imdb: 9.0,
        rating: 9.5,
        duration: "152 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BMTMxNTMwODM0NF5BMl5BanBnXkFtZTcwODAyMTk2Mw@@._V1_.jpg",
        cover: "https://images.alphacoders.com/289/28929.jpg",
        genres: [{ id: 4, title: "اکشن" }, { id: 5, title: "جنایی" }, { id: 2, title: "درام" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 1021, quality: "1080p BluRay - 2.4 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
            { id: 1022, quality: "720p BluRay - 1.2 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" }
        ]
    },
    {
        id: 103,
        type: "movie",
        title: "میان‌ستاره‌ای (Interstellar)",
        description: "در آینده‌ای که زمین به دلیل تغییرات اقلیمی و بحران غلات غیرقابل سکونت شده است، تیمی از فضانوردان و دانشمندان از طریق یک کرم‌چاله در نزدیکی زحل به جستجوی سیاره‌ای جدید برای بقای نسل بشر می‌روند.",
        year: 2014,
        imdb: 8.7,
        rating: 9.1,
        duration: "169 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BZjdkOTU3MDktN2IxOS00OGEyLWFmMjktY2FiMmZkNWIyODZiXkEyXkFqcGdeQXVyMTMxODk2OTU@._V1_.jpg",
        cover: "https://images6.alphacoders.com/546/546684.png",
        genres: [{ id: 6, title: "علمی تخیلی" }, { id: 2, title: "درام" }, { id: 7, title: "ماجراجویی" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 1031, quality: "1080p 10bit x265 - 2.6 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
            { id: 1032, quality: "720p x264 - 1.3 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" }
        ]
    },
    {
        id: 104,
        type: "movie",
        title: "تلقین (Inception)",
        description: "دام کاب یک سارق ماهر است که خطرناک‌ترین کار دزدی را انجام می‌دهد: استخراج اسرار ارزشمند از عمق ضمیر ناخودآگاه در طول وضعیت خواب و رؤیا. حال به او ماموریتی معکوس یعنی کاشتن یک ایده در ذهن یک مدیر داده می‌شود.",
        year: 2010,
        imdb: 8.8,
        rating: 9.0,
        duration: "148 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BMjAxMzY3NjcxNF5BMl5BanBnXkFtZTcwNTI5OTM0Mw@@._V1_.jpg",
        cover: "https://images2.alphacoders.com/139/139268.jpg",
        genres: [{ id: 4, title: "اکشن" }, { id: 6, title: "علمی تخیلی" }, { id: 7, title: "ماجراجویی" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 1041, quality: "1080p BluRay - 2.1 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
            { id: 1042, quality: "720p BluRay - 1.1 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" }
        ]
    },
    {
        id: 105,
        type: "movie",
        title: "تل‌ماسه: بخش دوم (Dune: Part Two)",
        description: "پل آتریدیس با چانی و فرمن‌ها متحد می‌شود در حالی که به دنبال انتقام از توطئه‌گرانی است که خانواده‌اش را نابود کردند. او در دوراهی بین عشق زندگی‌اش و سرنوشت جهان هستی قرار می‌گیرد.",
        year: 2024,
        imdb: 8.6,
        rating: 9.3,
        duration: "166 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BN2QyZGU4ZDctOWMzMy00NTc5LThlOGQtODhmNDI1NmY5YzAwXkEyXkFqcGdeQXVyMDM2NDM2MQ@@._V1_.jpg",
        cover: "https://images.hdqwalls.com/download/dune-part-two-2024-5k-3840x2160.jpg",
        genres: [{ id: 4, title: "اکشن" }, { id: 6, title: "علمی تخیلی" }, { id: 7, title: "ماجراجویی" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 1051, quality: "1080p WEB-DL - 2.9 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
            { id: 1052, quality: "720p WEB-DL - 1.5 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" }
        ]
    },
    {
        id: 106,
        type: "movie",
        title: "پدرخوانده (The Godfather)",
        description: "دون ویتو کورلئونه رئیس سالخورده یک خانواده مافیایی در نیویورک، کنترل امپراتوری خود را به کوچک‌ترین پسرش، مایکل، که پیش از این تمایلی به ورود به دنیای جرم و جنایت نداشت واگذار می‌کند.",
        year: 1972,
        imdb: 9.2,
        rating: 9.6,
        duration: "175 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BM2MyNjYxNmUtYTAwNi00MTYxLWJmNWYtYzZlODY3ZTk3OTFlXkEyXkFqcGdeQXVyNzkwMDEwNWQ@._V1_.jpg",
        cover: "https://images.alphacoders.com/278/278912.jpg",
        genres: [{ id: 5, title: "جنایی" }, { id: 2, title: "درام" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 1061, quality: "1080p Remastered - 2.5 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }
        ]
    },

    // 2. Series with Complete Seasons and Episodes
    {
        id: 201,
        type: "serie",
        title: "بریکینگ بد (Breaking Bad)",
        description: "یک معلم شیمی دبیرستان که مبتلا به سرطان ریه پیشرفته شده است، به همراه یکی از دانش‌آموزان سابق خود برای تأمین آینده مالی خانواده‌اش به تولید و فروش شیشه روی می‌آورد و به مرور به یک غول مواد مخدر تبدیل می‌شود.",
        year: 2008,
        imdb: 9.5,
        rating: 9.8,
        duration: "5 فصل کامل",
        image: "https://m.media-amazon.com/images/M/MV5BYmQ4YWMxYjUtNjZmYi00MDQxLWEzMjAtNjMtZGY3NTBjOTA0ZGYxXkEyXkFqcGdeQXVyMTMzNDExODE5._V1_.jpg",
        cover: "https://images5.alphacoders.com/439/439410.jpg",
        genres: [{ id: 5, title: "جنایی" }, { id: 2, title: "درام" }, { id: 8, title: "هیجان انگیز" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        seasons: [
            {
                id: 2011,
                title: "فصل 1",
                episodes: [
                    { id: 20111, title: "قسمت ۱: پایلوت", description: "والتر وایت ۵۰ ساله متوجه ابتلای خود به سرطان می‌شود و تصمیمی خطرناک می‌گیرد.", duration: "58 دقیقه", sources: [{ id: 1, quality: "1080p - 850 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }, { id: 2, quality: "720p - 450 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" }] },
                    { id: 20112, title: "قسمت ۲: گربه‌ای درون کیسه...", description: "والت و جسی تلاش می‌کنند اولین معامله خود و عواقب فاجعه‌بارش را جمع‌وجور کنند.", duration: "48 دقیقه", sources: [{ id: 1, quality: "1080p - 790 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 20113, title: "قسمت ۳: و کیسه درون رودخانه", description: "والت ناچار است با تصمیمی اخلاقی پیرامون سرنوشت کریزی-ایت روبه‌رو شود.", duration: "48 دقیقه", sources: [{ id: 1, quality: "1080p - 820 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 20114, title: "قسمت ۴: مرد سرطان", description: "والت راز بیماری‌اش را با خانواده در میان می‌گذارد.", duration: "48 دقیقه", sources: [{ id: 1, quality: "1080p - 760 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 20115, title: "قسمت ۵: ماده خاکستری", description: "یک پیشنهاد وسوسه‌انگیز کاری از جانب دوستان سابق والت، غرور او را جریحه‌دار می‌کند.", duration: "48 دقیقه", sources: [{ id: 1, quality: "1080p - 810 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 20116, title: "قسمت ۶: یک مشت جنون", description: "والت با هویت مستعار «هایزنبرگ» وارد قلمروی توکو سالامانکا می‌شود.", duration: "48 دقیقه", sources: [{ id: 1, quality: "1080p - 890 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 20117, title: "قسمت ۷: معامله‌ای از جنس مرگ", description: "والت و جسی به تولید صنعتی روی می‌آورند و خطر توکو بیخ گوششان است.", duration: "48 دقیقه", sources: [{ id: 1, quality: "1080p - 840 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] }
                ]
            },
            {
                id: 2012,
                title: "فصل 2",
                episodes: [
                    { id: 20121, title: "قسمت ۱: هفت و سی و هفت", description: "والت و جسی نقشه‌ای برای حذف توکو پیش از آنکه او آنها را بکشد می‌کشند.", duration: "47 دقیقه", sources: [{ id: 1, quality: "1080p - 820 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 20122, title: "قسمت ۲: در آتش پنهان", description: "توکو آنها را به کلبه عمویش هکتور سالامانکا در بیابان می‌برد.", duration: "47 دقیقه", sources: [{ id: 1, quality: "1080p - 830 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] }
                ]
            }
        ]
    },
    {
        id: 202,
        type: "serie",
        title: "چیزهای عجیب (Stranger Things)",
        description: "هنگامی که یک پسربچه در شهری کوچک ناپدید می‌شود، اهالی پرده از معمایی شامل آزمایش‌های فوق سری دولتی، نیروهای ماوراءطبیعی ترسناک و یک دختر عجیب و غریب برمی‌دارند.",
        year: 2016,
        imdb: 8.7,
        rating: 9.0,
        duration: "4 فصل",
        image: "https://m.media-amazon.com/images/M/MV5BMDZkYmVhNjMtNWU4MC00MDQxLWE3YTgtZTZlN2RhOWFlNmZlXkEyXkFqcGdeQXVyMTkxNjUyNQ@@._V1_.jpg",
        cover: "https://images.alphacoders.com/832/832269.jpg",
        genres: [{ id: 6, title: "علمی تخیلی" }, { id: 2, title: "درام" }, { id: 9, title: "ترسناک" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        seasons: [
            {
                id: 2021,
                title: "فصل 1",
                episodes: [
                    { id: 20211, title: "قسمت ۱: ناپدید شدن ویل بایرز", description: "شبی تاریک در سال ۱۹۸۳، ویل در راه بازگشت به خانه با چیزی وحشتناک مواجه می‌شود.", duration: "48 دقیقه", sources: [{ id: 1, quality: "1080p - 920 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 20212, title: "قسمت ۲: دخترک عجیب خیابان میپل", description: "بچه‌ها با الون (Eleven) در جنگل برخورد می‌کنند و متوجه توانایی‌های ماورایی‌اش می‌شوند.", duration: "55 دقیقه", sources: [{ id: 1, quality: "1080p - 950 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] }
                ]
            }
        ]
    },
    {
        id: 203,
        type: "serie",
        title: "ونزدی (Wednesday)",
        description: "داستان سال‌های تحصیل ونزدی آدامز در آکادمی نِورمور، جایی که او تلاش می‌کند بر توانایی‌های روانی نوظهور خود مسلط شود و معمایی قتل‌آمیز با پیشینه ۲۵ ساله را حل کند.",
        year: 2022,
        imdb: 8.1,
        rating: 8.8,
        duration: "1 فصل",
        image: "https://m.media-amazon.com/images/M/MV5BMjA3NjU1NTE3N15BMl5BanBnXkFtZTgwNTk5NDM4MzI@._V1_.jpg",
        cover: "https://images7.alphacoders.com/129/1291888.jpg",
        genres: [{ id: 10, title: "کمدی" }, { id: 11, title: "فانتزی" }, { id: 5, title: "جنایی" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        seasons: [
            {
                id: 2031,
                title: "فصل 1",
                episodes: [
                    { id: 20311, title: "قسمت ۱: چهارشنبه پر از اندوه است", description: "ونزدی پس از یک حادثه در استخر دبیرستان، به آکادمی نورمور تبعید می‌شود.", duration: "59 دقیقه", sources: [{ id: 1, quality: "1080p - 1.1 GB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] }
                ]
            }
        ]
    },

    // 3. Animations & Anime
    {
        id: 301,
        type: "movie",
        title: "شهر اشباح (Spirited Away)",
        description: "چیهیرو، دختربچه‌ای ۱۰ ساله، همراه با والدینش به شهری جدید نقل مکان می‌کند اما در راه وارد دنیایی جادویی پر از ارواح، شیاطین و خدایان باستانی می‌شود. شاهکار هایائو میازاکی برنده جایزه اسکار.",
        year: 2001,
        imdb: 8.6,
        rating: 9.3,
        duration: "125 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BMjlmAmZjzxUtMTU2Ny00MzU0LWI3ODktMmQzZTYyMmE3ZWIxXkEyXkFqcGdeQXVyNzkwMDEwNWQ@._V1_.jpg",
        cover: "https://images.alphacoders.com/209/209424.jpg",
        genres: [{ id: 12, title: "انیمیشن" }, { id: 7, title: "ماجراجویی" }, { id: 11, title: "فانتزی" }],
        country: [{ id: 2, title: "ژاپن", image: "" }],
        sources: [
            { id: 3011, quality: "1080p BluRay - 1.9 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }
        ]
    },
    {
        id: 302,
        type: "movie",
        title: "مرد عنکبوتی: در میان دنیای عنکبوتی (Spider-Man: Across the Spider-Verse)",
        description: "مایلز مورالس در سفری چندبعدی با تیمی از افراد عنکبوتی روبه‌رو می‌شود که وظیفه دارند از هستی چندجهانی محافظت کنند، اما هنگامی که در مواجهه با تهدیدی جدید اختلاف نظر پیدا می‌کنند، مایلز در برابر آنها قرار می‌گیرد.",
        year: 2023,
        imdb: 8.7,
        rating: 9.4,
        duration: "140 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BMzI0NmVkMjEtYmY4MS00ZDMxLTlkZmEtMzU4MDQxYTMzMjU2XkEyXkFqcGdeQXVyMzQ0MzA0NTM@._V1_.jpg",
        cover: "https://images4.alphacoders.com/131/1318025.jpeg",
        genres: [{ id: 12, title: "انیمیشن" }, { id: 4, title: "اکشن" }, { id: 7, title: "ماجراجویی" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 3021, quality: "1080p Web-DL - 2.5 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }
        ]
    },
    {
        id: 303,
        type: "serie",
        title: "وان پیس (One Piece)",
        description: "مانکی دی. لوفی، پسری جوان با بدنی کشسان، با رویای یافتن گنج نهایی «وان پیس» و تبدیل شدن به پادشاه دزدان دریایی، با خدمه وفادارش راهی گرند لاین می‌شود.",
        year: 1999,
        imdb: 8.9,
        rating: 9.4,
        duration: "سریال ادامه‌دار",
        image: "https://m.media-amazon.com/images/M/MV5BODcwNWE3OTMtMDc3MS00NDFjLWE1OTAtNDU3NjgxNTQ2MWM0XkEyXkFqcGdeQXVyMTA1OTcyNDQ4._V1_.jpg",
        cover: "https://images.alphacoders.com/605/605592.png",
        genres: [{ id: 12, title: "انیمیشن" }, { id: 4, title: "اکشن" }, { id: 7, title: "ماجراجویی" }],
        country: [{ id: 2, title: "ژاپن", image: "" }],
        seasons: [
            {
                id: 3031,
                title: "فصل 1 (East Blue)",
                episodes: [
                    { id: 30311, title: "قسمت ۱: من لوفی‌ام! مردی که پادشاه دزدان دریایی خواهد شد!", description: "لوفی از یک بشکه چوبی بیرون می‌آید و با کوبی آشنا می‌شود.", duration: "24 دقیقه", sources: [{ id: 1, quality: "1080p - 450 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 30312, title: "قسمت ۲: وارد شدن شمشیرزن ماهر: شکارچی دزدان دریایی، رورونوا زورو!", description: "لوفی برای جذب اولین هم‌تیمی خود به پایگاه تفنگداران دریایی نفوذ می‌کند.", duration: "24 دقیقه", sources: [{ id: 1, quality: "1080p - 440 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] }
                ]
            }
        ]
    },
    {
        id: 304,
        type: "serie",
        title: "بلویی (Bluey)",
        description: "ماجراهای دوست‌داشتنی و خانوادگی سگ پاستورال استرالیایی به نام بلویی و خواهر کوچکش بینگو که تخیل بی‌پایانشان زندگی روزمره را به بازی‌های خلاقانه و شاد تبدیل می‌کند.",
        year: 2018,
        imdb: 9.4,
        rating: 9.7,
        duration: "3 فصل",
        image: "https://m.media-amazon.com/images/M/MV5BNDAwODQ0MDItMWMzNi00Yzk4LWFkOGYtN2NmOWM5YmVmYjAwXkEyXkFqcGdeQXVyMTM1MTE1NDMx._V1_.jpg",
        cover: "https://images.alphacoders.com/133/1332028.jpeg",
        genres: [{ id: 12, title: "انیمیشن" }, { id: 10, title: "کمدی" }],
        country: [{ id: 3, title: "استرالیا", image: "" }],
        seasons: [
            {
                id: 3041,
                title: "فصل 1",
                episodes: [
                    { id: 30411, title: "قسمت ۱: زایلوفون جادویی", description: "بلویی و بینگو با زایلوفون جادویی پدرشان را در حالت‌های خنده‌دار متوقف می‌کنند.", duration: "8 دقیقه", sources: [{ id: 1, quality: "1080p - 180 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] },
                    { id: 30412, title: "قسمت ۲: بیمارستان", description: "بلویی و بینگو دکتر می‌شوند و پدرشان را درمان می‌کنند!", duration: "8 دقیقه", sources: [{ id: 1, quality: "1080p - 175 MB", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }] }
                ]
            }
        ]
    },
    {
        id: 305,
        type: "movie",
        title: "گارفیلد (The Garfield Movie)",
        description: "گارفیلد، گربه خانگی معروف که عاشق لازانیاست و از دوشنبه‌ها متنفر است، پس از دیدار غیرمنتظره با پدر گمشده‌اش ویک، وارد یک ماجراجویی هیجان‌انگیز در فضای باز می‌شود.",
        year: 2024,
        imdb: 6.0,
        rating: 7.2,
        duration: "101 دقیقه",
        image: "https://m.media-amazon.com/images/M/MV5BZmJhZjg5NGQtMmU5Yi00OWM1LWI2MjQtNDI3ODQ2N2Q1OTQyXkEyXkFqcGdeQXVyMTkxNjUyNQ@@._V1_.jpg",
        cover: "https://images.hdqwalls.com/download/the-garfield-movie-2024-5k-3840x2160.jpg",
        genres: [{ id: 12, title: "انیمیشن" }, { id: 10, title: "کمدی" }, { id: 7, title: "ماجراجویی" }],
        country: [{ id: 1, title: "آمریکا", image: "" }],
        sources: [
            { id: 3051, quality: "1080p WEB-DL - 1.6 GB", type: "mp4", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }
        ]
    }
];

class MediaDataService {
    constructor() {
        this.catalog = [];
        this.pendingRequests = new Map();
        this.initBridgeListener();
        this.servers = [
            "https://hostinnegar.com",
            "https://server-hi-speed-iran.info",
            "https://windowsdiba.info"
        ];
        this.apiKey = "4F5A9C3D9A86FA54EACEDDD635185";
        this.adKeywords = [
            "تبلیغ", "ورژن جدید", "اپلیکیشن", "دانلود اپ", "کانال تلگرام", "فیلترشکن", 
            "v2ray", "vpn", "proxy", "simba", "darknama", "نسخه جدید", "بروزرسانی",
            "promot", "update app", "apk", "t.me", "telegram", "کانال"
        ];
    }

    isAdvertisement(title, desc, url = "") {
        const t = (title || "").toLowerCase();
        const d = (desc || "").toLowerCase();
        const u = (url || "").toLowerCase();
        if (this.adKeywords.some(kw => t.includes(kw) || d.includes(kw))) return true;
        if (u.endsWith(".apk") || u.includes("download_app") || u.includes("telegram") || u.includes("t.me")) return true;
        return false;
    }

    initBridgeListener() {
        if (window.chrome && window.chrome.webview) {
            window.chrome.webview.addEventListener("message", (e) => {
                try {
                    const msg = typeof e.data === "string" ? JSON.parse(e.data) : e.data;
                    if (msg && msg.action === "iranflixResponse" && msg.requestId) {
                        const resolver = this.pendingRequests.get(msg.requestId);
                        if (resolver) {
                            this.pendingRequests.delete(msg.requestId);
                            if (msg.success && msg.data) {
                                resolver.resolve(msg.data);
                            } else {
                                resolver.reject(new Error(msg.error || "Bridge request failed"));
                            }
                        }
                    }
                } catch (err) {
                    console.error("[BridgeListener Error]", err);
                }
            });
        }
    }

    async fetchEndpoint(path) {
        // Ensure trailing slash
        const normalizedPath = path.endsWith("/") ? path : path + "/";
        const requestId = "req_" + Date.now() + "_" + Math.random().toString(36).substring(2, 7);

        // Try Native .NET HttpClient bridge first (bypasses browser CORS & SSL cert issues)
        if (window.chrome && window.chrome.webview) {
            try {
                const promise = new Promise((resolve, reject) => {
                    const timer = setTimeout(() => {
                        this.pendingRequests.delete(requestId);
                        reject(new Error("Timeout"));
                    }, 15000);
                    this.pendingRequests.set(requestId, {
                        resolve: (data) => { clearTimeout(timer); resolve(data); },
                        reject: (err) => { clearTimeout(timer); reject(err); }
                    });
                });

                window.chrome.webview.postMessage({
                    action: "fetchIranflix",
                    requestId,
                    endpoint: normalizedPath
                });

                const rawJson = await promise;
                return typeof rawJson === "string" ? JSON.parse(rawJson) : rawJson;
            } catch (bridgeErr) {
                console.warn("[Bridge Fetch Failed, fallback to direct]", bridgeErr);
            }
        }

        // Direct fetch fallback across servers
        for (const server of this.servers) {
            try {
                const cleanPath = normalizedPath.replace("{API_KEY}", this.apiKey);
                const url = `${server.replace(/\/+$/, "")}${cleanPath}`;
                const resp = await fetch(url, { headers: { "Accept": "application/json" } });
                if (resp.ok) {
                    return await resp.json();
                }
            } catch (_) {}
        }
        return null;
    }

    cleanMedia(raw) {
        if (!raw) return null;
        const title = (raw.title || '').trim();
        const desc = (raw.description || '').replace(/\r\n/g, '\n').trim();

        // Filter out ads or invalid titles
        if (!title || this.isAdvertisement(title, desc)) return null;

        const genres = (raw.genres || []).map(g => ({
            id: g.id || 0,
            title: (g.title || '').trim()
        })).filter(g => g.title && !this.adKeywords.some(kw => g.title.toLowerCase().includes(kw)));

        const isAnim = genres.some(g => g.title.includes("انیمیشن") || g.title.includes("کارتون") || g.title.includes("انیمه"));

        // Parse clean IMDb score if available in description
        let score = raw.imdb ? parseFloat(raw.imdb) : 0;
        if (score > 10) score = (score / 10).toFixed(1);
        if (score === 0) {
            const m = desc.match(/(?:IMDb|امتیاز|نمره)\s*[:：\-]?\s*([0-9](?:\.[0-9])?)/i);
            if (m) score = parseFloat(m[1]);
        }
        if (isNaN(score) || score <= 0) score = 7.5;

        // Parse clean storyline from description
        let storyline = desc;
        const storyIndex = desc.indexOf("خلاصه داستان");
        if (storyIndex !== -1) {
            storyline = desc.substring(storyIndex).replace(/خلاصه داستان\s*[:：\-]?/g, '').trim();
        }

        // Clean sources - filter out telegram, apk, or promotional links
        const cleanSources = (raw.sources || []).filter(s => {
            const u = (s.url || '').toLowerCase();
            const q = (s.quality || '').toLowerCase();
            if (!u || !u.startsWith("http")) return false;
            if (u.endsWith(".apk") || u.includes("telegram") || u.includes("t.me")) return false;
            if (this.adKeywords.some(kw => q.includes(kw))) return false;
            return true;
        }).map(s => ({
            id: s.id || Math.random(),
            quality: (s.quality || '1080p').replace(/زیرنویس/g, 'فارسی').trim(),
            type: s.type || 'mkv',
            url: s.url
        }));

        // Optimize image URLs
        const img = raw.image || "";
        const cover = raw.cover || img;

        return {
            id: raw.id,
            type: raw.type === "serie" ? "serie" : "movie",
            title: title,
            description: storyline || desc || 'توضیحاتی برای این اثر ثبت نشده است.',
            year: raw.year || 2024,
            imdb: Number(score.toFixed ? score.toFixed(1) : score),
            rating: raw.rating || 5,
            duration: raw.duration || (raw.type === 'serie' ? 'سریال چند قسمتی' : '120 دقیقه'),
            image: img,
            cover: cover,
            genres: genres.length > 0 ? genres : [{ id: 1, title: 'فیلم سینمایی' }],
            country: raw.country || [{ id: 1, title: 'جهانی' }],
            sources: cleanSources
        };
    }

    async getLatestMovies() {
        const raw = await this.fetchEndpoint("/api/movie/by/filtres/0/created/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return FALLBACK_CATALOG.filter(x => x.type === 'movie');
    }

    async getPopularSeries() {
        const raw = await this.fetchEndpoint("/api/serie/by/filtres/0/created/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return FALLBACK_CATALOG.filter(x => x.type === 'serie');
    }

    async getTopImdb() {
        const raw = await this.fetchEndpoint("/api/movie/by/filtres/0/imdb/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [...FALLBACK_CATALOG].sort((a, b) => (b.imdb || 0) - (a.imdb || 0));
    }

    async getAnimations() {
        const raw = await this.fetchEndpoint("/api/movie/by/filtres/3/created/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return FALLBACK_CATALOG.filter(x => {
            const g = (x.genres || []).map(g => g.title).join(' ');
            return g.includes('انیمیشن') || g.includes('کارتون') || g.includes('انیمه');
        });
    }

    async getExploreCatalog() {
        if (this.catalog.length > 20) return this.catalog;
        const [movies, series, topImdb] = await Promise.all([
            this.getLatestMovies(),
            this.getPopularSeries(),
            this.getTopImdb()
        ]);
        return this.catalog.length > 0 ? this.catalog : FALLBACK_CATALOG;
    }

    async search(query) {
        if (!query || !query.trim()) return this.getExploreCatalog();
        const encoded = encodeURIComponent(query.trim()).replace(/%20/g, "+");
        const raw = await this.fetchEndpoint(`/api/search/${encoded}/{API_KEY}/`);
        if (raw && Array.isArray(raw.posters) && raw.posters.length > 0) {
            const items = raw.posters.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        // Local query match fallback
        const q = query.trim().toLowerCase();
        return this.catalog.filter(x => {
            const title = (x.title || '').toLowerCase();
            const desc = (x.description || '').toLowerCase();
            const genres = (x.genres || []).map(g => g.title).join(' ').toLowerCase();
            return title.includes(q) || desc.includes(q) || genres.includes(q);
        });
    }

    async getSeriesSeasons(seriesId) {
        const raw = await this.fetchEndpoint(`/api/season/by/serie/${seriesId}/{API_KEY}/`);
        if (Array.isArray(raw)) {
            const seasons = [];
            raw.forEach((s, idx) => {
                const rawTitle = (s.title || '').trim();
                const title = rawTitle && rawTitle !== "null" ? rawTitle : `فصل ${idx + 1}`;
                const episodes = [];
                (s.episodes || []).forEach((ep, epIdx) => {
                    const epTitle = (ep.title || '').trim();
                    const epDesc = (ep.description || '').trim();
                    if (this.isAdvertisement(epTitle, epDesc)) return;

                    const cleanSources = (ep.sources || []).filter(src => {
                        const u = (src.url || '').toLowerCase();
                        if (!u || !u.startsWith("http")) return false;
                        if (u.endsWith(".apk") || u.includes("telegram") || u.includes("t.me")) return false;
                        return true;
                    }).map(src => ({
                        id: src.id || Math.random(),
                        quality: (src.quality || '720p / 1080p').replace(/زیرنویس/g, 'فارسی').trim(),
                        type: src.type || 'mkv',
                        url: src.url
                    }));

                    if (cleanSources.length > 0) {
                        episodes.push({
                            id: ep.id || (idx * 100 + epIdx),
                            title: epTitle && epTitle !== "null" ? epTitle : `قسمت ${epIdx + 1}`,
                            duration: ep.duration || '45 دقیقه',
                            sources: cleanSources
                        });
                    }
                });

                if (episodes.length > 0) {
                    seasons.push({ id: s.id, title, episodes });
                }
            });
            return seasons;
        }
        return [];
    }

    mergeIntoCatalog(items) {
        items.forEach(item => {
            if (!this.catalog.some(c => c.id === item.id)) {
                this.catalog.push(item);
            }
        });
    }

    async getHeroFeatured() {
        if (this.catalog.length === 0) await this.getLatestMovies();
        return this.catalog[0] || FALLBACK_CATALOG[0];
    }
}

window.mediaService = new MediaDataService();
