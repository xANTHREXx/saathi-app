-- Reports API: one function that returns the whole owner's dashboard as a single jsonb object, called by the
-- edge function's GET /reports (which checks a secret bearer token first). Real data (want_test = false) or test
-- phones only (true). Days are India time (Asia/Kolkata); the window is the last `days` days including today.
--
-- Counts (kpis and funnels) are summed from the per-day rows, so they reach past the 30-day event retention through
-- reports.daily_history. Distinct installs, the breakdowns and the crash table read the raw rows, so they only cover
-- what is still kept: events 30 days, crashes 60 days. kpis.installs_window_days says how many days that is.

create or replace function public.saathi_reports(want_test boolean, days int)
returns jsonb
language sql stable security definer set search_path = ''
as $$
  with w as (
    select n, today, today - (n - 1) as from_day,
      ((today - (n - 1))::timestamp at time zone 'Asia/Kolkata') as from_ts,
      ((today + 1)::timestamp at time zone 'Asia/Kolkata') as to_ts
    from (
      select greatest(1, least(coalesce(days, 30), 365)) as n, (now() at time zone 'Asia/Kolkata')::date as today
    ) x
  ),
  -- Days still in the raw tables.
  live as (
    select f.day, f.active_installs, f.trips, f.auto_trips, f.checkins, f.checkins_answered, f.checkins_timed_out,
      f.alerts, f.plate_scans, f.plate_reads, f.updates_prompted, f.updates_completed, f.crashes, f.crash_installs,
      f.rides_counted, f.reports_added
    from reports.daily_for(want_test) f, w
    where f.day between w.from_day and w.today
  ),
  -- Older real days from the kept daily counts. A day in `live` always wins. Test data is never rolled up.
  hist as (
    select h.day, h.active_installs::bigint, h.trips::bigint, h.auto_trips::bigint, h.checkins::bigint,
      h.checkins_answered::bigint, h.checkins_timed_out::bigint, h.alerts::bigint, h.plate_scans::bigint,
      h.plate_reads::bigint, h.updates_prompted::bigint, h.updates_completed::bigint, h.crashes::bigint,
      h.crash_installs::bigint, h.rides_counted::bigint, h.reports_added::bigint
    from reports.daily_history h, w
    where not want_test and h.day between w.from_day and w.today and h.day not in (select day from live)
  ),
  merged as (select * from live union all select * from hist),
  -- One row for every day in the window, zeros where nothing happened, so charts get a continuous axis.
  daily as (
    select g.day,
      coalesce(m.active_installs, 0) as active_installs, coalesce(m.trips, 0) as trips,
      coalesce(m.auto_trips, 0) as auto_trips, coalesce(m.checkins, 0) as checkins,
      coalesce(m.checkins_answered, 0) as checkins_answered, coalesce(m.checkins_timed_out, 0) as checkins_timed_out,
      coalesce(m.alerts, 0) as alerts, coalesce(m.plate_scans, 0) as plate_scans,
      coalesce(m.plate_reads, 0) as plate_reads, coalesce(m.updates_prompted, 0) as updates_prompted,
      coalesce(m.updates_completed, 0) as updates_completed, coalesce(m.crashes, 0) as crashes,
      coalesce(m.crash_installs, 0) as crash_installs, coalesce(m.rides_counted, 0) as rides_counted,
      coalesce(m.reports_added, 0) as reports_added
    from (select w.from_day + i as day from w, generate_series(0, w.n - 1) as i) g
    left join merged m on m.day = g.day
  ),
  tot as (
    select
      coalesce(sum(trips), 0)::bigint as trips, coalesce(sum(auto_trips), 0)::bigint as auto_trips,
      coalesce(sum(checkins), 0)::bigint as checkins, coalesce(sum(checkins_answered), 0)::bigint as checkins_answered,
      coalesce(sum(checkins_timed_out), 0)::bigint as checkins_timed_out, coalesce(sum(alerts), 0)::bigint as alerts,
      coalesce(sum(plate_scans), 0)::bigint as plate_scans, coalesce(sum(plate_reads), 0)::bigint as plate_reads,
      coalesce(sum(updates_prompted), 0)::bigint as updates_prompted,
      coalesce(sum(updates_completed), 0)::bigint as updates_completed, coalesce(sum(crashes), 0)::bigint as crashes,
      coalesce(sum(rides_counted), 0)::bigint as rides_counted, coalesce(sum(reports_added), 0)::bigint as reports_added
    from daily
  ),
  -- Raw rows inside the window, for distinct counts and breakdowns.
  ev as (
    select e.install_id, e.version_code, e.event_name, e.properties
    from public.app_events e, w
    where e.test = want_test and e.occurred_at >= w.from_ts and e.occurred_at < w.to_ts
  ),
  cr as (
    select c.install_id, c.exception_type, c.version_name, c.message, c.crashed_at
    from public.app_crashes c, w
    where c.test = want_test and c.crashed_at >= w.from_ts and c.crashed_at < w.to_ts
  )
  select jsonb_build_object(
    'generated_at', now(),
    'days', w.n,
    'test', want_test,
    'kpis', jsonb_build_object(
      'active_installs', (select count(distinct install_id) from ev),
      'installs_window_days', least(w.n, 30),
      'trips', t.trips,
      'auto_trips', t.auto_trips,
      'checkins', t.checkins,
      'checkins_answered', t.checkins_answered,
      'checkins_timed_out', t.checkins_timed_out,
      'alerts', t.alerts,
      'plate_scans', t.plate_scans,
      'plate_reads', t.plate_reads,
      'updates_prompted', t.updates_prompted,
      'updates_completed', t.updates_completed,
      'crashes', t.crashes,
      'crash_installs', (select count(distinct install_id) from cr),
      'rides_counted', t.rides_counted,
      'reports_added', t.reports_added,
      'vehicles_known', (
        select count(distinct v.plate_key) from public.vehicle_rides v where not v.sample and v.test = want_test
      )
    ),
    'daily', (select coalesce(jsonb_agg(to_jsonb(d) order by d.day), '[]'::jsonb) from daily d),
    'funnels', jsonb_build_array(
      jsonb_build_object('name', 'Check-ins', 'steps', jsonb_build_array(
        jsonb_build_object('label', 'Check-ins fired', 'value', t.checkins),
        jsonb_build_object('label', 'Answered', 'value', t.checkins_answered),
        jsonb_build_object('label', 'Timed out (alert sent)', 'value', t.checkins_timed_out)
      )),
      jsonb_build_object('name', 'Trips to alerts', 'steps', jsonb_build_array(
        jsonb_build_object('label', 'Trips', 'value', t.trips),
        jsonb_build_object('label', 'Check-ins fired', 'value', t.checkins),
        jsonb_build_object('label', 'Timed out', 'value', t.checkins_timed_out),
        jsonb_build_object('label', 'Alerts sent', 'value', t.alerts)
      )),
      jsonb_build_object('name', 'Plate photos', 'steps', jsonb_build_array(
        jsonb_build_object('label', 'Scanned', 'value', t.plate_scans),
        jsonb_build_object('label', 'Plate read', 'value', t.plate_reads)
      )),
      jsonb_build_object('name', 'Updates', 'steps', jsonb_build_array(
        jsonb_build_object('label', 'Prompted', 'value', t.updates_prompted),
        jsonb_build_object('label', 'Installed', 'value', t.updates_completed)
      )),
      jsonb_build_object('name', 'Community', 'steps', jsonb_build_array(
        jsonb_build_object('label', 'Trips', 'value', t.trips),
        jsonb_build_object('label', 'Rides counted', 'value', t.rides_counted)
      ))
    ),
    'breakdowns', jsonb_build_object(
      'alerts_by_reason', (
        select coalesce(jsonb_agg(jsonb_build_object('reason', s.reason, 'count', s.c, 'avg_contacts', s.avg_contacts, 'offline', s.offline)
          order by s.c desc, s.reason), '[]'::jsonb)
        from (
          select coalesce(properties->>'reason', 'unknown') as reason, count(*) as c,
            round(avg(case when jsonb_typeof(properties->'contacts_count') = 'number' then (properties->>'contacts_count')::numeric end), 1) as avg_contacts,
            count(*) filter (where properties->>'offline' = 'true') as offline
          from ev where event_name = 'alert_sent' group by 1
        ) s
      ),
      'checkins_by_reason', (
        select coalesce(jsonb_agg(jsonb_build_object('reason', s.reason, 'count', s.c) order by s.c desc, s.reason), '[]'::jsonb)
        from (
          select coalesce(properties->>'reason', 'unknown') as reason, count(*) as c
          from ev where event_name = 'checkin_triggered' group by 1
        ) s
      ),
      'trips_by_mode', (
        select coalesce(jsonb_agg(jsonb_build_object('mode', s.mode, 'count', s.c, 'auto_detected', s.auto_detected) order by s.c desc, s.mode), '[]'::jsonb)
        from (
          select coalesce(properties->>'mode', 'unknown') as mode, count(*) as c,
            count(*) filter (where properties->>'auto_detected' = 'true') as auto_detected
          from ev where event_name = 'trip_started' group by 1
        ) s
      ),
      'installs_by_version', (
        select coalesce(jsonb_agg(jsonb_build_object('version_code', s.version_code, 'installs', s.installs) order by s.version_code desc), '[]'::jsonb)
        from (
          select version_code, count(distinct install_id) as installs from ev group by 1
        ) s
      )
    ),
    'crashes', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'exception_type', s.exception_type, 'version_name', s.version_name, 'crashes', s.crashes,
        'installs_hit', s.installs_hit, 'first_seen', s.first_seen, 'last_seen', s.last_seen,
        'latest_message', s.latest_message) order by s.installs_hit desc, s.crashes desc), '[]'::jsonb)
      from (
        select exception_type, version_name, count(*) as crashes, count(distinct install_id) as installs_hit,
          min(crashed_at) as first_seen, max(crashed_at) as last_seen,
          (array_agg(left(message, 200) order by crashed_at desc))[1] as latest_message
        from cr group by 1, 2
        order by installs_hit desc, crashes desc
        limit 20
      ) s
    )
  )
  from w, tot t;
$$;

-- This function lives in the public schema, which the API exposes, so without this revoke anyone with the
-- public anon key could call it through /rest/v1/rpc. Only the edge function (service_role) may.
revoke all on function public.saathi_reports(boolean, int) from public, anon, authenticated;
grant execute on function public.saathi_reports(boolean, int) to service_role;
