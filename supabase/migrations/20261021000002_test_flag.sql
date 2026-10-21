-- Test phones: the app sends x-saathi-test: 1 from a test phone (debug builds, or the switch in the demo panel),
-- and the backend tags every row it writes with test = true. Reports and real users never see those rows.
-- reports.daily_test shows only the test rows, so a test run can be checked on its own.

alter table public.app_events       add column if not exists test boolean not null default false;
alter table public.app_crashes      add column if not exists test boolean not null default false;
alter table public.vehicle_rides    add column if not exists test boolean not null default false;
alter table public.community_points add column if not exists test boolean not null default false;

-- One row per day from the raw events, for real data (want_test = false) or test data only (true).
create or replace function reports.daily_for(want_test boolean)
returns table (
  day date, active_installs bigint, trips bigint, auto_trips bigint, checkins bigint, checkins_answered bigint,
  checkins_timed_out bigint, alerts bigint, plate_scans bigint, plate_reads bigint, updates_prompted bigint,
  updates_completed bigint, crashes bigint, crash_installs bigint, rides_counted bigint, reports_added bigint
)
language sql stable set search_path = '' as $$
  with e as (
    select (occurred_at at time zone 'Asia/Kolkata')::date as day, install_id, event_name, properties
    from public.app_events where test = want_test
  ), c as (
    select (crashed_at at time zone 'Asia/Kolkata')::date as day, count(*) as crashes, count(distinct install_id) as crash_installs
    from public.app_crashes where test = want_test group by 1
  ), r as (
    select (to_timestamp(t) at time zone 'Asia/Kolkata')::date as day, count(*) as rides_counted
    from public.vehicle_rides where not sample and test = want_test group by 1
  ), p as (
    select (to_timestamp(t) at time zone 'Asia/Kolkata')::date as day, count(*) as reports_added
    from public.community_points where not sample and test = want_test group by 1
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
$$;
revoke all on function reports.daily_for(boolean) from public, anon, authenticated;

-- Real data only. reports.rollup() reads this, so test runs never reach daily_history.
create or replace view reports.daily with (security_invoker = true) as
select * from reports.daily_for(false) order by day desc;

-- Test phones only.
create or replace view reports.daily_test with (security_invoker = true) as
select * from reports.daily_for(true) order by day desc;

-- Headline numbers, last 7 and 30 days.
create or replace view reports.overview with (security_invoker = true) as
select
  (select count(distinct install_id) from public.app_events where not test and occurred_at > now() - interval '7 days')  as installs_active_7d,
  (select count(distinct install_id) from public.app_events where not test and occurred_at > now() - interval '30 days') as installs_active_30d,
  (select count(*) from public.app_events where not test and event_name = 'trip_started' and occurred_at > now() - interval '7 days') as trips_7d,
  (select count(*) from public.app_events where not test and event_name = 'checkin_triggered' and occurred_at > now() - interval '7 days') as checkins_7d,
  (select count(*) from public.app_events where not test and event_name = 'alert_sent' and occurred_at > now() - interval '7 days') as alerts_7d,
  (select count(*) from public.app_crashes where not test and crashed_at > now() - interval '7 days') as crashes_7d,
  (select count(distinct install_id) from public.app_crashes where not test and crashed_at > now() - interval '7 days') as crash_installs_7d,
  (select count(*) from public.vehicle_rides where not sample and not test) as rides_counted_total,
  (select count(distinct plate_key) from public.vehicle_rides where not sample and not test) as vehicles_known,
  (select count(*) from public.community_points where not sample and not test) as reports_total,
  (select version_name from public.app_releases order by version_code desc limit 1) as latest_release;

-- Check-ins: how often they fire, why, and how often she answers. A high answer rate on one reason
-- means that check is crying wolf.
create or replace view reports.checkins with (security_invoker = true) as
select
  coalesce(properties->>'reason', 'unknown') as reason,
  count(*) as triggered_30d
from public.app_events
where not test and event_name = 'checkin_triggered' and occurred_at > now() - interval '30 days'
group by 1
union all
select '(all) answered', count(*) from public.app_events
where not test and event_name = 'checkin_answered' and occurred_at > now() - interval '30 days'
union all
select '(all) timed out', count(*) from public.app_events
where not test and event_name = 'checkin_timed_out' and occurred_at > now() - interval '30 days'
order by 2 desc;

-- Alerts by how she asked for help.
create or replace view reports.alerts with (security_invoker = true) as
select
  coalesce(properties->>'reason', 'unknown') as reason,
  count(*) as alerts_30d,
  round(avg((properties->>'contacts_count')::numeric), 1) as avg_contacts,
  count(*) filter (where properties->>'offline' = 'true') as sent_without_live_link
from public.app_events
where not test and event_name = 'alert_sent' and occurred_at > now() - interval '30 days'
group by 1
order by 2 desc;

-- Trips by mode, and how many started on their own from a ride-app notification.
create or replace view reports.trips with (security_invoker = true) as
select
  coalesce(properties->>'mode', 'unknown') as mode,
  count(*) as trips_30d,
  count(*) filter (where properties->>'auto_detected' = 'true') as auto_detected
from public.app_events
where not test and event_name = 'trip_started' and occurred_at > now() - interval '30 days'
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
where not test
group by 1, 2
order by installs_hit desc, crashes desc;

-- Which versions are in use (installs seen in the last 7 days), for update adoption.
create or replace view reports.versions with (security_invoker = true) as
select version_code, count(distinct install_id) as installs_7d
from public.app_events
where not test and occurred_at > now() - interval '7 days'
group by 1
order by 1 desc;

revoke all on all tables in schema reports from public, anon, authenticated;
