-- Saathi backend tables. Only the `api` Edge Function (service role) reads or writes them:
-- RLS is on with no policies and public roles get no grants, so nothing is exposed through the REST API.

create table public.live_shares (
  token   text primary key check (token ~ '^[0-9a-f]{32}$'),
  name    text not null,
  plate   text,
  last    jsonb,
  events  jsonb not null default '[]'::jsonb,
  touched timestamptz not null default now()
);

-- Anonymous: place rounded to ~110 m, 3-hour band, kind, HMAC keys. No names, no phone numbers, no plates.
create table public.community_points (
  id        bigint generated always as identity primary key,
  lat       double precision not null,
  lng       double precision not null,
  t         bigint not null,
  band      smallint not null,
  kind      text not null,
  plate_key text,
  account   text not null,
  case_key  text,
  sample    boolean not null default false
);
create index community_points_plate on public.community_points (plate_key, t) where plate_key is not null;
create index community_points_mine on public.community_points (account, case_key);

-- One row per ride a Saathi user took in a vehicle, so the next woman can see "13 rides with Saathi users".
create table public.vehicle_rides (
  id        bigint generated always as identity primary key,
  plate_key text not null,
  account   text not null,
  t         bigint not null,
  case_key  text,
  sample    boolean not null default false
);
create index vehicle_rides_plate on public.vehicle_rides (plate_key, t);
create index vehicle_rides_mine on public.vehicle_rides (account, case_key);

alter table public.live_shares enable row level security;
alter table public.community_points enable row level security;
alter table public.vehicle_rides enable row level security;
revoke all on public.live_shares, public.community_points, public.vehicle_rides from anon, authenticated;
-- Explicit, so it works on projects created with "Automatically expose new tables" off (no default grants).
grant select, insert, update, delete on public.live_shares, public.community_points, public.vehicle_rides to service_role;
grant usage, select on all sequences in schema public to service_role;

-- Housekeeping: live links die an hour after the last update; ride rows are only counted for 90 days.
create extension if not exists pg_cron with schema extensions;
select cron.schedule('saathi-live-cleanup', '7 * * * *',
  $$delete from public.live_shares where touched < now() - interval '1 hour'$$);
select cron.schedule('saathi-rides-cleanup', '17 3 * * *',
  $$delete from public.vehicle_rides where not sample and t < extract(epoch from now() - interval '90 days')$$);
