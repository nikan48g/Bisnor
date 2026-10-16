# Bisnor PWA Worker

Serves the shared Windows/PWA HTML assets and proxies only allowlisted IranFlix catalog endpoints. It never proxies video streams.

Before the first deploy, set the API key as a Cloudflare secret (never commit it):

```powershell
npm install
npx wrangler secret put IRANFLIX_API_KEY
npm run check
```

Then deploy only after choosing the production hostname:

```powershell
npm run deploy
```

After Cloudflare returns the final Worker/custom-domain URL, put it in
`site/config.js` as `window.BISNOR_WEB_APP_URL`. The GitHub Pages hub keeps the
Web button disabled until this value is deliberately configured, so previews
never point users at a made-up production address.
