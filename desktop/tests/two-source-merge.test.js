const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const wwwroot = path.join(repositoryRoot, "desktop", "BisnorDesktop", "wwwroot", "js");

const stored = new Map();
const context = {
    window: {},
    console,
    AbortSignal,
    setTimeout,
    clearTimeout,
    localStorage: {
        getItem: key => (stored.has(key) ? stored.get(key) : null),
        setItem: (key, value) => stored.set(key, String(value)),
        removeItem: key => stored.delete(key)
    }
};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(wwwroot, "providers.js"), "utf8"), context);
vm.runInContext(fs.readFileSync(path.join(wwwroot, "data.js"), "utf8"), context);

const { BisnorMerge } = context.window;
const service = context.window.mediaService;

function iranflix(id, title, overrides = {}) {
    return {
        id,
        type: "movie",
        title,
        description: `${title} توضیح فارسی`,
        year: 2024,
        imdb: 7.1,
        image: `ir-${id}.jpg`,
        cover: `ir-${id}.jpg`,
        sources: [{ id: 1, quality: "1080p", type: "mp4", url: `https://ir/${id}.mp4`, origin: "iranflix" }],
        genres: [],
        country: [],
        people: [],
        ...overrides
    };
}

function nextMovie(nmId, title, overrides = {}) {
    return {
        id: `nm_${nmId}`,
        nmId,
        source: "next",
        ids: { next: nmId },
        type: "movie",
        title,
        description: "",
        year: 2024,
        imdb: 6.5,
        image: `nm-${nmId}.jpg`,
        cover: `nm-${nmId}.jpg`,
        sources: [],
        genres: [],
        country: [],
        people: [],
        ...overrides
    };
}

async function run() {
    assert.equal(service.sourceMode, "both", "both sources are the default mode");

    // ---------------------------------------------------------------- matching
    assert.equal(
        BisnorMerge.normalizeTitle("The  Pretenders (2024)"),
        BisnorMerge.normalizeTitle("the pretenders"),
        "case, spacing and bracketed years must normalise away"
    );
    assert.equal(BisnorMerge.normalizeTitle("Don't Look Back in Anger"), "don t look back in anger");

    const index = new BisnorMerge.MatchIndex([iranflix(10, "Dont Look Back in Anger")]);
    assert.equal(index.find(nextMovie(5, "Don't Look Back in Anger")).id, 10);
    assert.equal(index.find(nextMovie(6, "Dont Look Back in Anger", { type: "serie" })), null, "a series must never match a movie");
    assert.equal(index.find(nextMovie(7, "Completely Different", { year: 1999 })), null);

    // ------------------------------------------------------- collection merging
    const iranflixList = [iranflix(1, "Dont Look Back in Anger"), iranflix(2, "Only On IranFlix")];
    const nextList = [
        nextMovie(5, "Dont Look Back in Anger", {
            sources: [{ id: 9, quality: "720p", type: "mp4", url: "https://nm/5.mp4", origin: "next" }]
        }),
        nextMovie(6, "Only On Next Movie")
    ];

    service.setSourceMode("both");
    const merged = service.mergeCollections(iranflixList, nextList);
    assert.equal(merged.length, 3, "two matching titles plus one Next-only title");

    const pair = merged.find(item => item.ids && item.ids.iranflix === 1);
    assert.ok(pair, "the matched title must keep both ids");
    assert.equal(pair.source, "both");
    assert.equal(pair.ids.next, 5);
    assert.equal(pair.title, "Dont Look Back in Anger");
    assert.match(pair.description, /توضیح فارسی/, "IranFlix description wins");
    assert.equal(pair.image, "ir-1.jpg", "IranFlix poster wins");
    assert.equal(pair.nmSources.length, 1, "Next links stay available");
    assert.equal(merged.filter(item => item.title === "Dont Look Back in Anger").length, 1, "one film appears once");

    const nextOnly = merged.find(item => item.id === "nm_6");
    assert.ok(nextOnly, "a Next-only title keeps its own id");
    assert.equal(nextOnly.source, "next");

    // Hydration combines both link sets without duplicates.
    pair.sources = service.dedupeSources([...pair.sources, ...pair.nmSources]);
    assert.equal(pair.sources.length, 2);
    assert.deepEqual(pair.sources.map(source => source.origin), ["iranflix", "next"]);

    // Same URL from both providers is stored once.
    const duplicate = service.dedupeSources([
        { url: "https://cdn/x.mp4?token=1" },
        { url: "https://cdn/x.mp4?token=2" }
    ]);
    assert.equal(duplicate.length, 1);

    // ------------------------------------------------------------ source modes
    service.getNextList = async () => nextList;
    service.setSourceMode("iranflix");
    const iranflixOnly = await service.combine("movie", 0, async () => iranflixList);
    assert.equal(iranflixOnly.length, 2);
    assert.ok(iranflixOnly.every(item => !String(item.id).startsWith("nm_")));

    service.setSourceMode("next");
    const nextOnlyMode = await service.combine("movie", 0, async () => iranflixList);
    assert.equal(nextOnlyMode.length, 2);
    assert.ok(nextOnlyMode.every(item => String(item.id).startsWith("nm_")), "IranFlix must not be called in Next-only mode");

    service.setSourceMode("both");
    const combined = await service.combine("movie", 0, async () => iranflixList);
    assert.equal(combined.length, 3, "both providers contribute one merged list");

    // ------------------------------------------------------- season/episode merge
    const iranflixSeasons = [{
        id: 100, number: 1, title: "فصل 1",
        episodes: [
            { id: 1, number: 1, title: "قسمت 1", sources: [{ url: "https://ir/e1.mp4", origin: "iranflix" }] },
            { id: 2, number: 2, title: "قسمت 2", sources: [{ url: "https://ir/e2.mp4", origin: "iranflix" }] }
        ]
    }];
    const nextSeasons = [{
        id: 200, number: 1, title: "Season 1",
        episodes: [
            { id: 21, number: 1, title: "Episode 1", sources: [{ url: "https://nm/e1.mp4", origin: "next" }] },
            { id: 22, number: 2, title: "Episode 2", sources: [{ url: "https://nm/e2.mp4", origin: "next" }] },
            { id: 23, number: 3, title: "Episode 3", sources: [{ url: "https://nm/e3.mp4", origin: "next" }] }
        ]
    }];
    const seasons = service.mergeSeasons(iranflixSeasons, nextSeasons);
    assert.equal(seasons.length, 1, "the same season merges into one");
    assert.equal(seasons[0].episodes.length, 3, "all episodes from both providers are kept");
    assert.equal(seasons[0].episodes[1].sources.length, 2, "an overlapping episode offers both links");
    assert.equal(seasons[0].episodes[2].sources[0].origin, "next", "Next-only episodes are kept");

    // Numbering is read from real season/episode titles.
    assert.equal(service.parseIndex("فصل ۳ - کیفیت 1080p"), 3);
    assert.equal(service.parseIndex("Season 2 720p"), 2);
    assert.equal(service.parseIndex("قسمت دوم بدون عدد"), 2);

    const parsedSeasons = service.next.toSeasonList({
        seasons: [{
            id: 1,
            title: "فصل 1",
            episodes: [
                { id: 11, title: "قسمت 1", links: [{ url: "https://nm/e1.mp4", quality: "1080p", type: "mp4" }] },
                { id: 12, title: "قسمت 2", links: [] }
            ]
        }]
    });
    assert.equal(parsedSeasons.length, 1);
    assert.equal(parsedSeasons[0].episodes.length, 1, "episodes without links are dropped");
    assert.equal(parsedSeasons[0].episodes[0].number, 1);

    // ------------------------------------- on-demand Next resolution for details
    service.setSourceMode("both");
    service.next.search = async title => (title === "Dont Look Back in Anger"
        ? [{ id: 5, title: "Don't Look Back in Anger", type: "movie", year: 2024 }]
        : []);
    service.next.details = async () => ({});
    service.next.links = async () => ({
        movie_links: [{ id: 9, quality: "720p", type: "mp4", url: "https://nm/5.mp4" }]
    });
    const unmatched = iranflix(1, "Dont Look Back in Anger");
    assert.equal(unmatched.ids, undefined, "an unmatched row starts without a Next id");
    const hydrated = await service.hydrateMedia(unmatched);
    assert.equal(hydrated.ids.next, 5, "the Next copy is found through search");
    assert.equal(hydrated.source, "both");
    assert.equal(hydrated.sources.filter(source => source.origin === "next").length, 1,
        "the detail page now offers the Next Movie links too");

    console.log("Two-source matching, merging, source modes and season/episode merge tests passed.");
}

run().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
