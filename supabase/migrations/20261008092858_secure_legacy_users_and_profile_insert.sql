create policy profiles_insert_own
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = user_id);

alter table public.users enable row level security;
revoke all privileges on table public.users from anon, authenticated;

create index if not exists users_claimed_by_idx
on public.users (claimed_by);
