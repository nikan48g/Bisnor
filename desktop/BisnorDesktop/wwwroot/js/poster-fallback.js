/**
 * Bisnor Desktop - Poster Fallback Generator
 * Generates instant, high-quality, lightweight SVG posters with gradient themes, movie titles, and badges.
 * Guarantees that even if external CDNs or IMDb are slow or filtered, cards NEVER show broken image icons.
 */

const GENRE_PALETTES = {
    "action": ["#1A102F", "#3b1d60", "#ff4b4b"],
    "drama": ["#121826", "#213555", "#4f709c"],
    "scifi": ["#0b192c", "#1e3e62", "#00adb5"],
    "crime": ["#1a120b", "#3c2a21", "#d5ac4e"],
    "anime": ["#2b1055", "#7597de", "#ff7597"],
    "comedy": ["#1a2a1a", "#2f5d34", "#f4a261"],
    "horror": ["#190000", "#3a0007", "#8b0000"],
    "default": ["#14141E", "#232338", "#FFB86B"]
};

function getPosterFallbackSvg(title, genre = "فیلم", year = "2024", rating = "8.5") {
    const cleanTitle = (title || "سینمای بیسنور").replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    let palette = GENRE_PALETTES.default;
    const gStr = String(genre);
    if (gStr.includes("اکشن")) palette = GENRE_PALETTES.action;
    else if (gStr.includes("درام")) palette = GENRE_PALETTES.drama;
    else if (gStr.includes("علمی")) palette = GENRE_PALETTES.scifi;
    else if (gStr.includes("جنایی")) palette = GENRE_PALETTES.crime;
    else if (gStr.includes("انیمه") || gStr.includes("انیمیشن")) palette = GENRE_PALETTES.anime;
    else if (gStr.includes("کمدی")) palette = GENRE_PALETTES.comedy;
    else if (gStr.includes("ترسناک")) palette = GENRE_PALETTES.horror;

    const [c1, c2, accent] = palette;

    const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 450" width="300" height="450">
        <defs>
            <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="${c1}" />
                <stop offset="60%" stop-color="${c2}" />
                <stop offset="100%" stop-color="#0a0a0f" />
            </linearGradient>
            <linearGradient id="accentGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stop-color="${accent}" />
                <stop offset="100%" stop-color="#FFB86B" />
            </linearGradient>
            <radialGradient id="glow" cx="50%" cy="30%" r="60%">
                <stop offset="0%" stop-color="${accent}" stop-opacity="0.35" />
                <stop offset="100%" stop-color="${accent}" stop-opacity="0" />
            </radialGradient>
        </defs>
        
        <!-- Background -->
        <rect width="300" height="450" fill="url(#bgGrad)" />
        <rect width="300" height="450" fill="url(#glow)" />
        
        <!-- Ambient Grid / Pattern -->
        <circle cx="150" cy="180" r="85" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="1.5" stroke-dasharray="4 4" />
        <circle cx="150" cy="180" r="55" fill="none" stroke="rgba(255,255,255,0.09)" stroke-width="1" />
        
        <!-- Film Silhouette -->
        <g transform="translate(115, 145) scale(1.4)" fill="${accent}" opacity="0.85">
            <path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/>
        </g>

        <!-- Brand Ribbon -->
        <rect x="20" y="24" width="70" height="24" rx="12" fill="rgba(0,0,0,0.5)" stroke="${accent}" stroke-width="1" />
        <text x="55" y="40" fill="${accent}" font-family="Vazirmatn, sans-serif" font-size="11" font-weight="bold" text-anchor="middle">بیسنور</text>

        <!-- Rating Pill -->
        <rect x="215" y="24" width="65" height="24" rx="12" fill="rgba(0,0,0,0.6)" stroke="#FFC107" stroke-width="1" />
        <text x="247" y="40" fill="#FFC107" font-family="Vazirmatn, sans-serif" font-size="11" font-weight="bold" text-anchor="middle">★ ${rating}</text>

        <!-- Bottom Content Card -->
        <rect x="0" y="320" width="300" height="130" fill="rgba(10,10,15,0.85)" />
        <line x1="20" y1="320" x2="280" y2="320" stroke="url(#accentGrad)" stroke-width="2" />
        
        <!-- Genre & Year -->
        <text x="150" y="348" fill="rgba(255,255,255,0.65)" font-family="Vazirmatn, sans-serif" font-size="12" text-anchor="middle">${genre} • سال ${year}</text>

        <!-- Title -->
        <text x="150" y="385" fill="#FFFFFF" font-family="Vazirmatn, sans-serif" font-size="15" font-weight="bold" text-anchor="middle">${cleanTitle.length > 25 ? cleanTitle.substring(0, 24) + '...' : cleanTitle}</text>

        <!-- Quality Sub -->
        <text x="150" y="415" fill="${accent}" font-family="Vazirmatn, sans-serif" font-size="11" text-anchor="middle">کیفیت اصلی • پخش مستقیم</text>
    </svg>
    `.trim();

    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

window.getPosterFallbackSvg = getPosterFallbackSvg;
