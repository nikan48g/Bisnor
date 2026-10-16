const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const catalogClient = fs.readFileSync(
    path.join(repositoryRoot, "desktop", "BisnorDesktop", "wwwroot", "js", "data.js"),
    "utf8"
);
const desktopController = fs.readFileSync(
    path.join(repositoryRoot, "desktop", "BisnorDesktop", "wwwroot", "js", "app.js"),
    "utf8"
);

const context = {
    window: {},
    console,
    AbortSignal,
    setTimeout,
    clearTimeout
};
vm.createContext(context);
vm.runInContext(catalogClient, context);

const service = context.window.mediaService;
const calls = [];

function stub(kind) {
    return async (page = 0) => {
        calls.push(`${kind}:${page}`);
        const item = { id: `${kind}-${page}` };
        service.mergeIntoCatalog([item]);
        return [item];
    };
}

service.getTopImdb = stub("imdb");
service.getLatestMovies = stub("movie");
service.getPopularSeries = stub("series");

(async () => {
    const catalog = await service.getExploreCatalog();

    assert.deepEqual(calls, [
        "imdb:0", "imdb:1", "imdb:2", "imdb:3",
        "movie:0", "movie:1", "movie:2",
        "series:0", "series:1", "series:2"
    ]);
    assert.equal(catalog.length, 10);

    await service.getExploreCatalog();
    assert.equal(calls.length, 10, "the hydrated catalog must not refetch every visit");

    let searchPath = "";
    service.fetchEndpoint = async (path) => {
        searchPath = path;
        return {
            posters: [{
                id: 999,
                type: "serie",
                title: "One Piece",
                description: "Anime series",
                genres: [{ id: 3, title: "انیمه" }],
                sources: []
            }]
        };
    };
    const searchResults = await service.search("one piece");
    assert.equal(searchPath, "/api/search/one%20piece/{API_KEY}/");
    assert.equal(searchResults[0].title, "One Piece");
    const credited = service.cleanMedia({
        id: 1000,
        title: "Credit test",
        description: "کارگردان: کریستوفر نولان\nبازیگران: کیلین مورفی، امیلی بلانت",
        genres: [],
        sources: []
    });
    assert.equal(
        JSON.stringify(credited.people.map(person => [person.name, person.role])),
        JSON.stringify([["کریستوفر نولان", "director"], ["کیلین مورفی", "actor"], ["امیلی بلانت", "actor"]])
    );
    assert.match(desktopController, /episode-card/);
    assert.match(desktopController, /playNextWebEpisode/);
    assert.doesNotMatch(desktopController, /class="episode-cover"/);
    assert.match(desktopController, /player: "auto"/);
    assert.match(desktopController, /openPersonProfile/);
    assert.match(desktopController, /renderExplorePeople/);
    assert.match(desktopController, /createPersonCard/);
    assert.match(desktopController, /explore-people-results/);
    assert.match(desktopController, /event\.stopPropagation\(\)/);
    assert.match(desktopController, /renderForYou/);
    assert.match(desktopController, /beforeinstallprompt/);
    assert.match(desktopController, /bisnor_pwa_taste_v1/);
    assert.match(desktopController, /Add to Home Screen/);
    assert.match(desktopController, /applyPlatformIdentity/);

    console.log("Catalog, people profiles, For You, episode UI and player routing tests passed.");
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
