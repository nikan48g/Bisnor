# Bisnor Telegram release bot

Owner-only Telegram publisher hosted as a Supabase Edge Function.

Required production secrets (set them directly in Supabase, never commit them):

- `TELEGRAM_BOT_TOKEN`: a freshly rotated BotFather token.
- `TELEGRAM_WEBHOOK_SECRET`: a random value containing only `A-Z`, `a-z`, `0-9`, `_`, or `-`.

After deployment, register this webhook with Telegram using the same secret:

`https://flhchqkuubuubzxyktnp.supabase.co/functions/v1/telegram-release-bot`

Send one authenticated `GET` request to that URL with the header
`X-Telegram-Bot-Api-Secret-Token` set to `TELEGRAM_WEBHOOK_SECRET`. The function
registers its own URL with Telegram and drops updates that predate the setup.

The bot accepts messages only from Telegram user `5997592961`. Send `/new`, then
the announcement text. The editor supports custom URL buttons, file delivery, a
preview, an optional two-button release template, and explicit confirmation
before posting to `@Bisnor`.

The Telegram download button is added only when file delivery is enabled and an
APK was uploaded successfully. Its URL points to that APK message in the channel.

Announcement text supports safe Markdown-style bold (`**text**` or `*text*`) and
links (`[label](https://example.com)`), converted to Telegram HTML before sending.
