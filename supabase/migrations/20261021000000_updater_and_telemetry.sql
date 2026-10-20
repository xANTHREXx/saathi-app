-- Saathi: In-App Updater & Telemetry tables

-- 1. App Releases
create table if not exists public.app_releases (
  version_code   integer primary key,
  version_name   text    not null,
  apk_url        text    not null check (apk_url like 'https://%'),
  apk_sha256     text    not null check (apk_sha256 ~ '^[0-9a-f]{64}$'), -- lowercase hex SHA-256 of the APK file
  changelog      text    not null default '',
  is_mandatory   boolean not null default false,
  created_at     timestamptz not null default now()
);

alter table public.app_releases enable row level security;
revoke all on public.app_releases from anon, authenticated;
grant select, insert, update, delete on public.app_releases to service_role;

-- 2. App Crashes
create table if not exists public.app_crashes (
  id             bigint generated always as identity primary key,
  install_id     text    not null,
  version_code   integer not null,
  version_name   text    not null,
  os_version     integer not null,
  device_model   text    not null,
  exception_type text    not null,
  message        text,
  stack_trace    text    not null,
  crashed_at     timestamptz not null,
  received_at    timestamptz not null default now()
);

create index if not exists app_crashes_time on public.app_crashes (crashed_at);

alter table public.app_crashes enable row level security;
revoke all on public.app_crashes from anon, authenticated;
grant select, insert, delete on public.app_crashes to service_role;

-- 3. App Events
create table if not exists public.app_events (
  id             bigint generated always as identity primary key,
  install_id     text    not null,
  version_code   integer not null,
  event_name     text    not null,
  properties     jsonb   not null default '{}'::jsonb,
  occurred_at    timestamptz not null,
  received_at    timestamptz not null default now()
);

create index if not exists app_events_name_time on public.app_events (event_name, occurred_at);

alter table public.app_events enable row level security;
revoke all on public.app_events from anon, authenticated;
grant select, insert, delete on public.app_events to service_role;

-- 4. Auto-purge cron jobs (safe if pg_cron is available)
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    -- Unschedule only if present (cron.unschedule raises for unknown jobs), so re-runs stay idempotent.
    if exists (select 1 from cron.job where jobname = 'saathi-events-cleanup') then
      perform cron.unschedule('saathi-events-cleanup');
    end if;
    if exists (select 1 from cron.job where jobname = 'saathi-crashes-cleanup') then
      perform cron.unschedule('saathi-crashes-cleanup');
    end if;
    perform cron.schedule('saathi-events-cleanup', '23 3 * * *',
      'delete from public.app_events where received_at < now() - interval ''30 days''');
    perform cron.schedule('saathi-crashes-cleanup', '29 3 * * *',
      'delete from public.app_crashes where received_at < now() - interval ''60 days''');
  end if;
exception when others then
  raise notice 'pg_cron not active; skipping cron registration';
end;
$$;
