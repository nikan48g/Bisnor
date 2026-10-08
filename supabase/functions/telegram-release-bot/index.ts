const OWNER_ID = 5997592961;
const CHANNEL = "@Bisnor";
const REPOSITORY = "nikan48g/Bisnor";
const WATERMARK =
  'بیسنور، تماشا بدون مرز <a href="https://web.telegram.org/k/assets/img/emoji/1f48e.png">💎</a> | <a href="https://t.me/Bisnor">@Bisnor</a>';

type ButtonStyle = "danger" | "primary" | "success";
type DraftButton = { text: string; url: string; style?: ButtonStyle };
type Draft = {
  owner_id: number;
  mode: "ready" | "awaiting_button";
  body: string;
  buttons: DraftButton[];
  include_files: boolean;
  use_release_template: boolean;
};

const token = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
const webhookSecret = Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? "";
const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";

function secretKey(): string {
  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (legacy) return legacy;
  const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}");
  return keys.default ?? "";
}

const adminKey = secretKey();
const telegram = (method: string) => `https://api.telegram.org/bot${token}/${method}`;

async function tg(method: string, payload: Record<string, unknown>) {
  const response = await fetch(telegram(method), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
  const result = await response.json();
  if (!result.ok) throw new Error(`Telegram ${method}: ${result.description}`);
  return result.result;
}

async function rest(path: string, init: RequestInit = {}) {
  const authHeaders: Record<string, string> = { apikey: adminKey };
  if (!adminKey.startsWith("sb_secret_")) authHeaders.authorization = `Bearer ${adminKey}`;
  return fetch(`${supabaseUrl}/rest/v1/${path}`, {
    ...init,
    headers: {
      ...authHeaders,
      "content-type": "application/json",
      ...(init.headers ?? {}),
    },
  });
}

async function isProcessed(updateId: number) {
  const response = await rest(
    `telegram_release_bot_updates?update_id=eq.${updateId}&select=update_id`,
  );
  const rows = await response.json();
  return rows.length > 0;
}

async function markProcessed(updateId: number) {
  await rest("telegram_release_bot_updates", {
    method: "POST",
    headers: { Prefer: "resolution=ignore-duplicates,return=minimal" },
    body: JSON.stringify({ update_id: updateId }),
  });
}

async function getDraft(): Promise<Draft> {
  const response = await rest(
    `telegram_release_bot_state?owner_id=eq.${OWNER_ID}&select=*`,
  );
  const rows = await response.json();
  return rows[0] ?? {
    owner_id: OWNER_ID,
    mode: "ready",
    body: "",
    buttons: [],
    include_files: false,
    use_release_template: false,
  };
}

async function saveDraft(patch: Partial<Draft>) {
  const current = await getDraft();
  const response = await rest("telegram_release_bot_state?on_conflict=owner_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify({ ...current, ...patch, updated_at: new Date().toISOString() }),
  });
  if (!response.ok) throw new Error(`Draft save failed: ${await response.text()}`);
}

const escapeHtml = (value: string) =>
  value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

function formatTelegramText(value: string) {
  const pattern = /\*\*([^*\n]+)\*\*|\*([^*\n]+)\*|\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g;
  let output = "";
  let cursor = 0;
  for (const match of value.matchAll(pattern)) {
    const index = match.index ?? 0;
    output += escapeHtml(value.slice(cursor, index));
    if (match[1] || match[2]) {
      output += `<b>${escapeHtml(match[1] ?? match[2])}</b>`;
    } else {
      const label = escapeHtml(match[3]);
      const url = escapeHtml(match[4]).replaceAll('"', "&quot;");
      output += `<a href="${url}">${label}</a>`;
    }
    cursor = index + match[0].length;
  }
  return output + escapeHtml(value.slice(cursor));
}

async function latestRelease() {
  const response = await fetch(`https://api.github.com/repos/${REPOSITORY}/releases/latest`, {
    headers: { accept: "application/vnd.github+json", "user-agent": "Bisnor-Telegram-Bot" },
  });
  if (!response.ok) throw new Error(`GitHub latest release: ${response.status}`);
  return response.json();
}

function editorKeyboard(draft: Draft) {
  return {
    inline_keyboard: [
      [
        { text: "➕ افزودن دکمه", callback_data: "add_button", style: "primary" },
        { text: "🧹 حذف دکمه‌های دستی", callback_data: "clear_buttons" },
      ],
      [{
        text: draft.use_release_template ? "🎨 قالب ریلیس: روشن" : "🎨 قالب ریلیس: خاموش",
        callback_data: "toggle_template",
        style: draft.use_release_template ? "success" : "primary",
      }],
      [{
        text: draft.include_files ? "📎 ارسال فایل‌ها: روشن" : "📎 ارسال فایل‌ها: خاموش",
        callback_data: "toggle_files",
        style: draft.include_files ? "success" : undefined,
      }],
      [
        { text: "✅ تأیید و انتشار", callback_data: "publish", style: "success" },
        { text: "❌ لغو", callback_data: "cancel", style: "danger" },
      ],
    ],
  };
}

async function preview(chatId: number, draft: Draft) {
  const release = await latestRelease();
  const keyboard = [
    ...(draft.use_release_template ? [[
      { text: "دانلود از گیت‌هاب", url: release.html_url, style: "danger" },
    ]] : []),
    ...draft.buttons.map((button) => [button]),
  ];
  await tg("sendMessage", {
    chat_id: chatId,
    text: `${formatTelegramText(draft.body)}\n\n${WATERMARK}`,
    parse_mode: "HTML",
    link_preview_options: { is_disabled: true },
    reply_markup: { inline_keyboard: keyboard },
  });
  await tg("sendMessage", {
    chat_id: chatId,
    text: "پیش‌نمایش بالا آماده است. انتشار فقط بعد از تأیید شما انجام می‌شود.",
    reply_markup: editorKeyboard(draft),
  });
}

async function uploadAsset(asset: { name: string; browser_download_url: string }) {
  const fileResponse = await fetch(asset.browser_download_url);
  if (!fileResponse.ok) throw new Error(`Download ${asset.name}: ${fileResponse.status}`);
  const form = new FormData();
  form.append("chat_id", CHANNEL);
  form.append("document", new Blob([await fileResponse.arrayBuffer()]), asset.name);
  form.append("caption", `${escapeHtml(asset.name)}\n\n${WATERMARK}`);
  form.append("parse_mode", "HTML");
  const response = await fetch(telegram("sendDocument"), { method: "POST", body: form });
  const result = await response.json();
  if (!result.ok) throw new Error(`Telegram sendDocument: ${result.description}`);
  return result.result.message_id as number;
}

async function publish(chatId: number, draft: Draft) {
  if (!draft.body.trim()) throw new Error("متن پست خالی است.");
  const release = await latestRelease();
  let telegramUrl: string | undefined;
  if (draft.include_files) {
    const assets = release.assets.filter((asset: { name: string; size: number }) =>
      /\.(apk|exe)$/i.test(asset.name) && asset.size <= 50 * 1024 * 1024
    );
    for (const asset of assets) {
      const messageId = await uploadAsset(asset);
      if (/\.apk$/i.test(asset.name)) telegramUrl = `https://t.me/Bisnor/${messageId}`;
    }
  }
  await tg("sendMessage", {
    chat_id: CHANNEL,
    text: `${formatTelegramText(draft.body)}\n\n${WATERMARK}`,
    parse_mode: "HTML",
    link_preview_options: { is_disabled: true },
    reply_markup: {
      inline_keyboard: [
        ...(draft.use_release_template ? [[
          { text: "دانلود از گیت‌هاب", url: release.html_url, style: "danger" },
          ...(telegramUrl ? [{ text: "دانلود از تلگرام", url: telegramUrl, style: "primary" }] : []),
        ]] : []),
        ...draft.buttons.map((button) => [button]),
      ],
    },
  });
  await saveDraft({ body: "", buttons: [], mode: "ready" });
  await tg("sendMessage", { chat_id: chatId, text: "✅ پست با موفقیت در @Bisnor منتشر شد." });
}

async function handleMessage(message: any) {
  if (message.from?.id !== OWNER_ID || message.chat?.type !== "private") return;
  const chatId = message.chat.id;
  const text = (message.text ?? "").trim();
  const draft = await getDraft();
  if (text === "/start" || text === "/new") {
    await saveDraft({ body: "", buttons: [], include_files: false, use_release_template: false, mode: "ready" });
    await tg("sendMessage", {
      chat_id: chatId,
      text: "متن پست را بفرستید. سپس می‌توانید دکمه دلخواه اضافه کنید، ارسال APK/Windows را روشن کنید و بعد از پیش‌نمایش تأیید بزنید.",
    });
    return;
  }
  if (draft.mode === "awaiting_button") {
    const [label, url, requestedStyle = "primary"] = text.split("|").map((part: string) => part.trim());
    const style = ["danger", "primary", "success"].includes(requestedStyle)
      ? requestedStyle as ButtonStyle
      : "primary";
    if (!label || !/^https:\/\//i.test(url ?? "")) {
      await tg("sendMessage", { chat_id: chatId, text: "فرمت درست: عنوان دکمه | https://example.com | primary" });
      return;
    }
    const next = { ...draft, mode: "ready" as const, buttons: [...draft.buttons, { text: label, url, style }] };
    await saveDraft(next);
    await preview(chatId, next);
    return;
  }
  if (!text || text.startsWith("/")) return;
  const next = { ...draft, body: text, mode: "ready" as const };
  await saveDraft(next);
  await preview(chatId, next);
}

async function handleCallback(query: any) {
  if (query.from?.id !== OWNER_ID) return;
  const chatId = query.message?.chat?.id;
  if (!chatId) return;
  await tg("answerCallbackQuery", { callback_query_id: query.id });
  const draft = await getDraft();
  if (query.data === "add_button") {
    await saveDraft({ mode: "awaiting_button" });
    await tg("sendMessage", { chat_id: chatId, text: "دکمه را بفرستید:\nعنوان | https://example.com | primary\nرنگ‌ها: primary (آبی)، danger (قرمز)، success (سبز)" });
  } else if (query.data === "clear_buttons") {
    const next = { ...draft, buttons: [] };
    await saveDraft(next);
    await preview(chatId, next);
  } else if (query.data === "toggle_files") {
    const next = { ...draft, include_files: !draft.include_files };
    await saveDraft(next);
    await preview(chatId, next);
  } else if (query.data === "toggle_template") {
    const next = { ...draft, use_release_template: !draft.use_release_template };
    await saveDraft(next);
    await preview(chatId, next);
  } else if (query.data === "cancel") {
    await saveDraft({ body: "", buttons: [], include_files: false, use_release_template: false, mode: "ready" });
    await tg("sendMessage", { chat_id: chatId, text: "پیش‌نویس لغو شد." });
  } else if (query.data === "publish") {
    await publish(chatId, draft);
  }
}

Deno.serve(async (request) => {
  if (!token || !webhookSecret || !adminKey) return new Response("Not configured", { status: 503 });
  if (request.headers.get("x-telegram-bot-api-secret-token") !== webhookSecret) {
    return new Response("Unauthorized", { status: 401 });
  }
  if (request.method === "GET") {
    await tg("setWebhook", {
      url: `${supabaseUrl}/functions/v1/telegram-release-bot`,
      secret_token: webhookSecret,
      allowed_updates: ["message", "callback_query"],
      drop_pending_updates: true,
    });
    return Response.json({ configured: true });
  }
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  try {
    const update = await request.json();
    if (await isProcessed(update.update_id)) return new Response("ok");
    if (update.message) await handleMessage(update.message);
    if (update.callback_query) await handleCallback(update.callback_query);
    await markProcessed(update.update_id);
    return new Response("ok");
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    return new Response("failed", { status: 500 });
  }
});
