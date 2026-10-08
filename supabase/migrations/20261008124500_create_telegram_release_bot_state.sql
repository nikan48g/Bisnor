create table if not exists public.telegram_release_bot_state (
  owner_id bigint primary key,
  mode text not null default 'ready' check (mode in ('ready', 'awaiting_button')),
  body text not null default '',
  buttons jsonb not null default '[]'::jsonb check (jsonb_typeof(buttons) = 'array'),
  include_files boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.telegram_release_bot_updates (
  update_id bigint primary key,
  received_at timestamptz not null default now()
);

alter table public.telegram_release_bot_state enable row level security;
alter table public.telegram_release_bot_updates enable row level security;

revoke all on public.telegram_release_bot_state from anon, authenticated;
revoke all on public.telegram_release_bot_updates from anon, authenticated;

comment on table public.telegram_release_bot_state is
  'Private draft state for the owner-only Bisnor Telegram release bot.';
comment on table public.telegram_release_bot_updates is
  'Telegram webhook update ids used to make delivery idempotent.';
