create policy legacy_users_no_direct_access
on public.users
for all
to public
using (false)
with check (false);
