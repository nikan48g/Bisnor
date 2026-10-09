alter table public.telegram_release_bot_state
  add column if not exists use_release_template boolean not null default false;
