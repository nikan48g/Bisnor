create table if not exists public.telegram_release_bot_publications (
  tag_name text primary key,
  status text not null default 'pending'
    check (status in ('pending', 'completed', 'failed')),
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.telegram_release_bot_publications enable row level security;
revoke all on public.telegram_release_bot_publications from anon, authenticated;

comment on table public.telegram_release_bot_publications is
  'Server-only idempotency ledger for Telegram release publication.';

insert into public.telegram_release_bot_publications (tag_name, status)
values ('v5.1.5', 'completed')
on conflict (tag_name) do nothing;
