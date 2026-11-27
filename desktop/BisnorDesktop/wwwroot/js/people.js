/**
 * Bisnor web/desktop – local credit index.
 *
 * Providers only search titles, so a person's name alone rarely returns their films.
 * Every list payload and every opened detail screen registers its cast/crew here; the
 * search rail and the person dialog then answer instantly, and the film a person was
 * opened from always stays in their works. Credits (not works) are persisted so a
 * Persian name search still works after a restart.
 */
(function () {
    "use strict";

    const STORAGE_KEY = "bisnor_people_index_v1";

    function normalize(value) {
        let text = String(value || "").toLocaleLowerCase();
        text = text
            .replace(/\u064a/g, "\u06cc")
            .replace(/\u0643/g, "\u06a9")
            .replace(/\u200c|\u200e|\u200f/g, " ");
        if (text.normalize) text = text.normalize("NFKD");
        text = text.replace(/[\u0300-\u036f]/g, "");
        text = text.replace(/[^\p{L}\p{N}]+/gu, " ");
        return text.replace(/\s+/g, " ").trim();
    }

    function isPlausibleName(value) {
        const name = String(value || "").trim();
        if (name.length < 2 || name.length > 60) return false;
        if (/\d/.test(name)) return false;
        if (name.split(" ").filter(Boolean).length > 6) return false;
        const lower = name.toLocaleLowerCase();
        if (lower.startsWith("به ") || lower.includes(" همچون ") || lower.includes(" هنرمندانی")) return false;
        return true;
    }

    class PeopleIndex {
        constructor() {
            this.buckets = new Map();
            this.saveTimer = null;
            this.load();
        }

        load() {
            try {
                const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
                if (!Array.isArray(raw)) return;
                raw.forEach(entry => {
                    const name = String(entry.name || "").trim();
                    const nameFa = String(entry.nameFa || "").trim();
                    if (!isPlausibleName(name || nameFa)) return;
                    const key = normalize(name || nameFa);
                    if (!key) return;
                    const bucket = this.buckets.get(key) || { name: name || nameFa, nameFa: "", photo: "", role: "actor", character: "", works: new Map() };
                    if (!bucket.name) bucket.name = name;
                    if (nameFa) bucket.nameFa = nameFa;
                    if (!bucket.photo && entry.photo) bucket.photo = entry.photo;
                    if (bucket.role === "actor" && entry.role) bucket.role = entry.role;
                    this.buckets.set(key, bucket);
                });
            } catch (_) {
                // A broken index must never block search.
            }
        }

        scheduleSave() {
            if (this.saveTimer) return;
            this.saveTimer = setTimeout(() => {
                this.saveTimer = null;
                this.save();
            }, 2500);
        }

        save() {
            try {
                const payload = [];
                this.buckets.forEach(bucket => {
                    if (!bucket.name && !bucket.nameFa) return;
                    payload.push({
                        name: bucket.name,
                        nameFa: bucket.nameFa,
                        photo: bucket.photo,
                        role: bucket.role
                    });
                });
                localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
            } catch (_) {
                // best effort
            }
        }

        remember(media) {
            if (!media || !media.title || !Array.isArray(media.people)) return;
            media.people.forEach(person => {
                const rawName = String(person.name || person.nameFa || "").trim();
                if (!isPlausibleName(rawName)) return;
                const key = normalize(rawName);
                if (!key) return;
                const bucket = this.buckets.get(key) || { name: "", nameFa: "", photo: "", role: "actor", character: "", works: new Map() };
                if (!bucket.name && person.name) bucket.name = String(person.name).trim();
                if (person.nameFa) bucket.nameFa = String(person.nameFa).trim();
                if (person.photo) bucket.photo = person.photo;
                if (person.character) bucket.character = person.character;
                if (bucket.role === "actor" && person.role && person.role !== "actor") bucket.role = person.role;
                bucket.works.set(String(media.id), media);
                this.buckets.set(key, bucket);
            });
            this.scheduleSave();
        }

        rememberAll(items) {
            (items || []).forEach(item => this.remember(item));
        }

        search(query, limit = 10) {
            const words = normalize(query);
            if (!words) return [];
            const tokens = words.split(" ").filter(Boolean);
            const results = [];
            this.buckets.forEach(bucket => {
                const haystack = normalize(`${bucket.name} ${bucket.nameFa}`);
                if (!haystack || !tokens.every(token => haystack.includes(token))) return;
                const score = haystack === words ? 0 : haystack.startsWith(words) ? 1 : 2;
                results.push({ score, bucket });
            });
            results.sort((a, b) => a.score - b.score || b.bucket.works.size - a.bucket.works.size);
            return results.slice(0, limit).map(({ bucket }) => this.toEntry(bucket));
        }

        findBucket(name) {
            const key = normalize(name);
            if (!key) return null;
            const direct = this.buckets.get(key);
            if (direct) return direct;
            // Persian aliases are stored on the English-name bucket.
            for (const bucket of this.buckets.values()) {
                if (normalize(bucket.nameFa) === key || normalize(`${bucket.name} ${bucket.nameFa}`) === key) return bucket;
            }
            return null;
        }

        works(name) {
            const bucket = this.findBucket(name);
            return bucket ? Array.from(bucket.works.values()) : [];
        }

        entry(name) {
            const bucket = this.findBucket(name);
            return bucket ? this.toEntry(bucket) : null;
        }

        photo(name) {
            const bucket = this.findBucket(name);
            return bucket ? bucket.photo : "";
        }

        toEntry(bucket) {
            const works = Array.from(bucket.works.values()).sort((a, b) =>
                (Number(b.year) || 0) - (Number(a.year) || 0) ||
                String(a.title || "").localeCompare(String(b.title || ""), "fa")
            );
            return {
                name: bucket.name || bucket.nameFa,
                nameFa: bucket.nameFa,
                photo: bucket.photo,
                role: bucket.role,
                character: bucket.character,
                works
            };
        }
    }

    window.BisnorPeople = { normalize, isPlausibleName, PeopleIndex };
    window.bisnorPeople = new PeopleIndex();
})();
