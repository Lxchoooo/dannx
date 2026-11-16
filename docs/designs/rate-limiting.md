# Rate limiting: what is actually exposed

*   **Status:** design decision; guards applied 2026-11-16
*   **Date:** 2026-11-15

---

## Short version

Rate limiting **writes** would protect almost nothing in DannX: anonymous users
cannot write at all. The exposure that matters is **reads**, and a per-row
trigger is the wrong tool for it. Recommendation: measure first, set a
`statement_timeout` for the anonymous role, and only build a token bucket if the
metrics show abuse.

## Who can do what

The `anon` key ships inside every APK, so it is public by design. It is worth
being precise about what it can do:

| Path | Granted to | Guard |
| --- | --- | --- |
| `select` on `wallpapers`, `categories`, `tag_categories`, `collections`, `collection_items`, `app_settings` | `anon`, `authenticated` | Public catalogue; no rate limit. |
| `postgres_functions`: `wallpaper_search_text`, `wallpaper_tag_labels`, `wallpaper_tags_valid`, `is_admin` | `anon`, `authenticated` | Read-only or pure helpers. |
| `insert`/`update`/`delete` on every table | `authenticated` only | `is_admin()`, which reads `public.admins`. |
| Storage `wallpapers` bucket (write) | `authenticated` only | `is_admin()`. |

Email sign-ups are disabled in the Supabase project, so a stranger with the
`anon` key cannot become `authenticated`. There is no anon-callable function that
writes: the download counter lives in `Prefs` on the device, not in a column.

Conclusion: **a write limiter would only throttle the admin**, or someone who
already stole the admin session. That is a narrow threat, and the damage from a
stolen admin token is not "too many writes per minute".

## What is really exposed

Reads. Every installed APK carries a key that can paginate the whole catalogue.
The realistic risks are:

*   Egress and CPU on the free plan (`Cloudflare`/Supabase quotas), which shows
    up as the project being paused or throttled for real users.
*   Expensive queries: the search RPC sorts a text expression over the full
    table, and filters by tag arrays. A caller can repeat it in a loop.

The app itself already bounds its share: 30 wallpapers per page, a 400-item
cache and no background polling. The gap is that nothing stops a third party
from doing the same with `curl`.

## Options

| Option | Effort | What it buys | Cost |
| --- | --- | --- | --- |
| `statement_timeout` for `anon` | 1 migration | Kills runaway queries instead of letting them burn CPU. | Long but legitimate searches get cut; needs a value measured on real data. |
| Postgres token bucket (trigger or RPC) | 1 migration + tests | Caps writes per window. | Only covers writes, which are already admin-only. Adds a write per write. |
| Supabase plan limits / spend cap | Dashboard | Hard ceiling on usage. | Does not stop a burst that hurts users before the cap. |
| Edge Function proxy with per-IP limits | New service | The only place where per-IP limits make sense. | Another deploy target, and it breaks the app's direct PostgREST calls. |
| Cloudflare in front of the API | Dashboard | Bot rules and caching for public reads. | DNS and a policy decision; the API is `*.supabase.co`. |

## Recommendation

1.  **Do not add a write limiter.** It protects a path that only the admin can
    reach. A trigger on every write would also slow the bulk operations the
    Studio already does (tagging N wallpapers one update at a time).
2.  **Measure first**, then decide:

    ```sql
    -- Extensión ya disponible en Supabase.
    create extension if not exists pg_stat_statements;

    select calls,
           round(total_exec_time::numeric, 1) as total_ms,
           round(mean_exec_time::numeric, 1) as mean_ms,
           rows,
           left(query, 80) as query
    from pg_stat_statements
    order by total_exec_time desc
    limit 20;
    ```

    If the search RPC dominates, the fix is an index or a cheaper expression,
    not a limiter.
3.  If abuse appears, start with the cheapest real guard:

    ```sql
    -- Borrador, NO aplicado.
    alter role anon set statement_timeout = '3s';
    alter role authenticated set statement_timeout = '10s';
    ```

    Three seconds is generous for a page of 30 rows and short enough that a
    loop of heavy searches gets cut. Tune it with the numbers from step 2.

## If a token bucket is ever needed

Only for the writes that a future feature might expose to `authenticated`
non-admin users (for example, public collections). Sketch, not applied:

```sql
create table if not exists public.write_budget (
    bucket text not null,
    window_start timestamptz not null,
    used int not null default 0,
    primary key (bucket, window_start)
);

alter table public.write_budget enable row level security;
revoke all on public.write_budget from anon, authenticated;

create or replace function public.consume_write_budget(
    p_bucket text,
    p_limit int,
    p_window interval
) returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_window timestamptz := date_trunc('minute', now());
    allowed boolean;
begin
    insert into public.write_budget (bucket, window_start, used)
    values (p_bucket, current_window, 1)
    on conflict (bucket, window_start)
        do update set used = public.write_budget.used + 1
    returning used <= p_limit into allowed;

    return coalesce(allowed, false);
end;
$$;

revoke all on function public.consume_write_budget(text, int, interval)
    from public;
```

The trigger that uses it must raise a typed error the app can show
(`raise exception 'write budget exhausted' using errcode = 'P0001'`), and the
cleanup job must delete windows older than a few minutes.

## Decision

Applied in two steps (2026-11-16):

1.  **No write limiter.** The design above stands: a limiter would only throttle
    the admin or a stolen admin session, and it would slow bulk Studio edits.
2.  **Query guards** (`docs/migraciones/20261121_query_guards.sql`, applied via
    run [`43788499666`](https://github.com/Lxchoooo/dannx/actions/runs/43788499666)):

    ```sql
    create extension if not exists pg_stat_statements;
    alter role anon set statement_timeout = '3s';
    alter role authenticated set statement_timeout = '10s';
    ```

## Measurement (2026-11-16)

Runs [`43788970041`](https://github.com/Lxchoooo/dannx/actions/runs/43788970041)
(top by total time) and
[`43789167877`](https://github.com/Lxchoooo/dannx/actions/runs/43789167877)
(catalogue queries, by calls). The queries are repeatable:
`supabase/pg_stat_measure.sql` and `supabase/pg_stat_measure_app.sql`.

*   The **most expensive** statements by total time are Supabase's own
    introspection (dashboard and auth catalog: `pg_timezone_names`, extension
    checks, replication helpers), not the app.
*   **Catalogue reads** (feed pages, collections, collection items, tag
    categories, app settings): 0.1–0.6 ms mean per statement across hundreds of
    calls; total under 150 ms each.
*   The heaviest app RPC is `purge_expired_wallpapers` (2 calls, 127.6 ms mean)
    and it is admin-only.
*   The search RPC does not appear in the top 20 by total time, so the design's
    fear about its sort expression is not confirmed by the data.

Conclusion: the 3 s `anon` timeout sits three orders of magnitude above real
usage, so it kills runaway loops without touching legitimate traffic. The
measurement does not justify an index or a cheaper search expression for now;
re-run the measurement files after catalogue growth or a new expensive feature.
