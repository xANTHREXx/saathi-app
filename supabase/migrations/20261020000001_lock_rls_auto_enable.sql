-- Supabase's "automatic RLS" option adds public.rls_auto_enable(), callable by anyone through the REST API.
-- It only needs to run as an event trigger, so nobody outside the database should be able to call it.
do $$
begin
  if exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public' and p.proname = 'rls_auto_enable') then
    revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
  end if;
end $$;
