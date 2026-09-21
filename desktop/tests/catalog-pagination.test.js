const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const catalogClient = fs.readFileSync(
    path.join(repositoryRoot, "tizen-tv", "js", "data.js"),
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

    console.log("Catalog pagination parity test passed.");
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
