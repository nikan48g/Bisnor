-- Short watchlist share links: the worker stores the compressed list once and
-- hands out /s/<code> instead of a multi-kilobyte ?data= URL.
create table if not exists public.shared_lists (
    code text primary key check (code ~ '^[A-Za-z0-9]{8}$'),
    payload text not null check (length(payload) between 1 and 65536),
    created_at timestamptz not null default now()
);

alter table public.shared_lists enable row level security;

-- Anyone holding a code may read that list; nobody can list, edit or delete.
create policy "read shared list by code" on public.shared_lists
    for select to anon, authenticated using (true);

create policy "create shared list" on public.shared_lists
    for insert to anon, authenticated with check (true);

revoke update, delete on public.shared_lists from anon, authenticated;
