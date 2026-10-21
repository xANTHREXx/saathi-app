-- Reports: read-only views over telemetry, crashes and community data, for the dashboard's Table Editor
-- (schema "reports") or the SQL editor. The schema is not exposed to the API: anon and authenticated
-- get nothing. Views run as the caller (security_invoker), so they never widen access.
-- Days are India time (Asia/Kolkata). Raw events are deleted after 30 days; reports.daily_history keeps the daily counts (no install ids) for good.

create schema if not exists reports;
revoke all on schema reports from public, anon, authenticated;

-- One row per day from the raw events (last 30 days, while they exist).
create or replace view reports.daily with (security_invoker = true) as
with e as (
  select (occurred_at at time zone 'Asia/Kolkata')::date as day, install_id, event_name, properties from public.app_events
), c as (
  select (crashed_at at time zone 'Asia/Kolkata')::date as day, count(*) as crashes, count(distinct install_id) as crash_installs
  from public.app_crashes group by 1
), r as (
  select (to_timestamp(t) at time zone 'Asia/Kolkata')::date as day, count(*) as rides_counted from public.vehicle_rides where not sample group by 1
), p as (
  select (to_timestamp(t) at time zone 'Asia/Kolkata')::date as day, count(*) as reports_added from public.community_points where not sample group by 1
), d as (
  select day from e union select day from c union select day from r union select day from p
)
select
  d.day,
  count(distinct e.install_id)                                                          as active_installs,
  count(*) filter (where e.event_name = 'trip_started')                                 as trips,
  count(*) filter (where e.event_name = 'trip_started' and e.properties->>'auto_detected' = 'true') as auto_trips,
  count(*) filter (where e.event_name = 'checkin_triggered')                            as checkins,
  count(*) filter (where e.event_name = 'checkin_answered')                             as checkins_answered,
  count(*) filter (where e.event_name = 'checkin_timed_out')                            as checkins_timed_out,
  count(*) filter (where e.event_name = 'alert_sent')                                   as alerts,
  count(*) filter (where e.event_name = 'plate_scanned')                                as plate_scans,
  count(*) filter (where e.event_name = 'plate_scanned' and e.properties->>'success' = 'true') as plate_reads,
  count(*) filter (where e.event_name = 'update_prompted')                              as updates_prompted,
  count(*) filter (where e.event_name = 'update_completed')                             as updates_completed,
  coalesce(max(c.crashes), 0)                                                           as crashes,
  coalesce(max(c.crash_installs), 0)                                                    as crash_installs,
  coalesce(max(r.rides_counted), 0)                                                     as rides_counted,
  coalesce(max(p.reports_added), 0)                                                     as reports_added
from d
left join e on e.day = d.day
left join c on c.day = d.day
left join r on r.day = d.day
left join p on p.day = d.day
group by d.day
order by d.day desc;

-- Headline numbers, last 7 and 30 days.
create or replace view reports.overview with (security_invoker = true) as
select
  (select count(distinct install_id) from public.app_events where occurred_at > now() - interval '7 days')  as installs_active_7d,
  (select count(distinct install_id) from public.app_events where occurred_at > now() - interval '30 days') as installs_active_30d,
  (select count(*) from public.app_events where event_name = 'trip_started' and occurred_at > now() - interval '7 days') as trips_7d,
  (select count(*) from public.app_events where event_name = 'checkin_triggered' and occurred_at > now() - interval '7 days') as checkins_7d,
  (select count(*) from public.app_events where event_name = 'alert_sent' and occurred_at > now() - interval '7 days') as alerts_7d,
  (select count(*) from public.app_crashes where crashed_at > now() - interval '7 days') as crashes_7d,
  (select count(distinct install_id) from public.app_crashes where crashed_at > now() - interval '7 days') as crash_installs_7d,
  (select count(*) from public.vehicle_rides where not sample) as rides_counted_total,
  (select count(distinct plate_key) from public.vehicle_rides where not sample) as vehicles_known,
  (select count(*) from public.community_points where not sample) as reports_total,
  (select version_name from public.app_releases order by version_code desc limit 1) as latest_release;

-- Check-ins: how often they fire, why, and how often she answers. A high answer rate on one reason
-- means that check is crying wolf.
create or replace view reports.checkins with (security_invoker = true) as
select
  coalesce(properties->>'reason', 'unknown') as reason,
  count(*) as triggered_30d
from public.app_events
where event_name = 'checkin_triggered' and occurred_at > now() - interval '30 days'
group by 1
union all
select '(all) answered', count(*) from public.app_events
where event_name = 'checkin_answered' and occurred_at > now() - interval '30 days'
union all
select '(all) timed out', count(*) from public.app_events
where event_name = 'checkin_timed_out' and occurred_at > now() - interval '30 days'
order by 2 desc;

-- Alerts by how she asked for help.
create or replace view reports.alerts with (security_invoker = true) as
select
  coalesce(properties->>'reason', 'unknown') as reason,
  count(*) as alerts_30d,
  round(avg((properties->>'contacts_count')::numeric), 1) as avg_contacts,
  count(*) filter (where properties->>'offline' = 'true') as sent_without_live_link
from public.app_events
where event_name = 'alert_sent' and occurred_at > now() - interval '30 days'
group by 1
order by 2 desc;

-- Trips by mode, and how many started on their own from a ride-app notification.
create or replace view reports.trips with (security_invoker = true) as
select
  coalesce(properties->>'mode', 'unknown') as mode,
  count(*) as trips_30d,
  count(*) filter (where properties->>'auto_detected' = 'true') as auto_detected
from public.app_events
where event_name = 'trip_started' and occurred_at > now() - interval '30 days'
group by 1
order by 2 desc;

-- Crashes grouped by type and version: what to fix first.
create or replace view reports.crashes with (security_invoker = true) as
select
  exception_type,
  version_name,
  count(*) as crashes,
  count(distinct install_id) as installs_hit,
  min(crashed_at) as first_seen,
  max(crashed_at) as last_seen,
  (array_agg(left(message, 200) order by crashed_at desc))[1] as latest_message
from public.app_crashes
group by 1, 2
order by installs_hit desc, crashes desc;

-- Which versions are in use (installs seen in the last 7 days), for update adoption.
create or replace view reports.versions with (security_invoker = true) as
select version_code, count(distinct install_id) as installs_7d
from public.app_events
where occurred_at > now() - interval '7 days'
group by 1
order by 1 desc;

-- Daily counts kept after the raw events expire. No install ids, so nothing here identifies anyone.
create table if not exists reports.daily_history (
  day date primary key,
  active_installs int not null, trips int not null, auto_trips int not null,
  checkins int not null, checkins_answered int not null, checkins_timed_out int not null,
  alerts int not null, plate_scans int not null, plate_reads int not null,
  updates_prompted int not null, updates_completed int not null,
  crashes int not null, crash_installs int not null,
  rides_counted int not null, reports_added int not null,
  rolled_up_at timestamptz not null default now()
);
alter table reports.daily_history enable row level security;

create or replace function reports.rollup() returns void
language sql set search_path = '' as $$
  insert into reports.daily_history (day, active_installs, trips, auto_trips, checkins, checkins_answered,
    checkins_timed_out, alerts, plate_scans, plate_reads, updates_prompted, updates_completed,
    crashes, crash_installs, rides_counted, reports_added, rolled_up_at)
  select day, active_installs, trips, auto_trips, checkins, checkins_answered, checkins_timed_out, alerts,
    plate_scans, plate_reads, updates_prompted, updates_completed, crashes, crash_installs,
    rides_counted, reports_added, now()
  from reports.daily
  where day >= (now() at time zone 'Asia/Kolkata')::date - 3 and day < (now() at time zone 'Asia/Kolkata')::date  -- finished days only; re-done a few times in case of late batches
  on conflict (day) do update set
    active_installs = excluded.active_installs, trips = excluded.trips, auto_trips = excluded.auto_trips,
    checkins = excluded.checkins, checkins_answered = excluded.checkins_answered,
    checkins_timed_out = excluded.checkins_timed_out, alerts = excluded.alerts,
    plate_scans = excluded.plate_scans, plate_reads = excluded.plate_reads,
    updates_prompted = excluded.updates_prompted, updates_completed = excluded.updates_completed,
    crashes = excluded.crashes, crash_installs = excluded.crash_installs,
    rides_counted = excluded.rides_counted, reports_added = excluded.reports_added,
    rolled_up_at = excluded.rolled_up_at;
$$;
revoke all on function reports.rollup() from public, anon, authenticated;

-- Before the 03:23 events cleanup, so a day is always rolled up while its events still exist.
select cron.schedule('saathi-reports-rollup', '11 3 * * *', 'select reports.rollup()');

revoke all on all tables in schema reports from public, anon, authenticated;
