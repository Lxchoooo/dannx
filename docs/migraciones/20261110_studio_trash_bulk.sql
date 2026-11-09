-- Papelera y operaciones en lote atómicas para el Estudio.
-- Es idempotente. Ejecutalo después de 20261108_estudio.sql.
--
-- La papelera conserva la fila y la imagen durante 30 días. Una tarea diaria
-- de pg_cron mueve las rutas de Storage a storage_cleanup_queue y elimina las
-- filas vencidas. El workflow cleanup-storage.yml vacía esa cola.

alter table public.wallpapers
    add column if not exists deleted_at timestamptz;

alter table public.wallpapers
    add column if not exists deleted_by uuid references auth.users(id)
        on delete set null;

create index if not exists wallpapers_deleted_idx
    on public.wallpapers(deleted_at);

-- El feed público y los usuarios autenticados que no son admin dejan de ver
-- los fondos eliminados. El Estudio sigue viéndolos porque is_admin() es true.
drop policy if exists "wallpapers_public_read" on public.wallpapers;
create policy "wallpapers_public_read"
    on public.wallpapers
    for select
    using (
        (deleted_at is null and archived_at is null)
        or public.is_admin()
    );

-- Cola de limpieza de Storage. La llena la purga automática; la procesa el
-- workflow con la service role key. Si todavía no está configurada, las rutas
-- quedan acá sin afectar a la app.
create table if not exists public.storage_cleanup_queue (
    id bigserial primary key,
    path text not null,
    created_at timestamptz not null default now(),
    attempts int not null default 0
);

create index if not exists storage_cleanup_queue_created_idx
    on public.storage_cleanup_queue(created_at asc);

alter table public.storage_cleanup_queue enable row level security;

drop policy if exists "storage_cleanup_admin_read" on public.storage_cleanup_queue;
create policy "storage_cleanup_admin_read"
    on public.storage_cleanup_queue
    for select
    to authenticated
    using (public.is_admin());

grant select on public.storage_cleanup_queue to authenticated;

-- Marca o desmarca fondos como destacados. A las filas nuevas les asigna una
-- posición al final del carrusel en la misma transacción.
create or replace function public.bulk_set_featured(
    p_ids uuid[],
    p_featured boolean
)
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    next_position int;
begin
    if not public.is_admin() then
        raise exception 'Solo administradores pueden editar fondos.'
            using errcode = '42501';
    end if;

    if p_featured then
        select coalesce(max(featured_position), -1) + 1
        into next_position
        from public.wallpapers;

        with candidates as (
            select
                id,
                row_number() over (order by created_at, id) as row_position
            from public.wallpapers
            where id = any(p_ids)
              and deleted_at is null
              and not featured
        )
        update public.wallpapers as w
        set featured_position = (next_position + candidates.row_position - 1)::int
        from candidates
        where w.id = candidates.id;

        update public.wallpapers
        set featured = true
        where id = any(p_ids)
          and deleted_at is null;
    else
        update public.wallpapers
        set featured = false
        where id = any(p_ids)
          and deleted_at is null;
    end if;
end;
$$;

-- Archiva o desarchiva fondos en una sola sentencia.
create or replace function public.bulk_set_archived(
    p_ids uuid[],
    p_archived boolean
)
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then
        raise exception 'Solo administradores pueden editar fondos.'
            using errcode = '42501';
    end if;

    update public.wallpapers
    set archived_at = case when p_archived then now() else null end
    where id = any(p_ids)
      and deleted_at is null;
end;
$$;

-- Mueve fondos a una categoría. El trigger de categorías sincroniza el texto
-- heredado de wallpapers.category.
create or replace function public.bulk_set_category(
    p_ids uuid[],
    p_category_id uuid
)
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then
        raise exception 'Solo administradores pueden editar fondos.'
            using errcode = '42501';
    end if;

    if p_category_id is null
        or not exists (select 1 from public.categories where id = p_category_id)
    then
        raise exception 'La categoría no existe.'
            using errcode = '22023';
    end if;

    update public.wallpapers
    set category_id = p_category_id
    where id = any(p_ids)
      and deleted_at is null;
end;
$$;

-- Borrado lógico: la fila y la imagen siguen disponibles para restaurar.
create or replace function public.soft_delete_wallpapers(p_ids uuid[])
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then
        raise exception 'Solo administradores pueden eliminar fondos.'
            using errcode = '42501';
    end if;

    update public.wallpapers
    set deleted_at = now(),
        deleted_by = auth.uid()
    where id = any(p_ids)
      and deleted_at is null;
end;
$$;

create or replace function public.restore_wallpapers(p_ids uuid[])
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then
        raise exception 'Solo administradores pueden restaurar fondos.'
            using errcode = '42501';
    end if;

    update public.wallpapers
    set deleted_at = null,
        deleted_by = null
    where id = any(p_ids)
      and deleted_at is not null;
end;
$$;

-- Reordena el carrusel sin mandar un update por fondo.
create or replace function public.reorder_featured(p_ids uuid[])
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then
        raise exception 'Solo administradores pueden ordenar destacados.'
            using errcode = '42501';
    end if;

    with ordered as (
        select
            candidate.id,
            (min(candidate.position) - 1)::int as new_position
        from unnest(p_ids) with ordinality as candidate(id, position)
        group by candidate.id
    )
    update public.wallpapers as w
    set featured_position = ordered.new_position
    from ordered
    where w.id = ordered.id
      and w.featured
      and w.deleted_at is null;
end;
$$;

-- Purga automática. Primero guarda las rutas de Storage y después elimina las
-- filas vencidas. No toca storage.objects directamente: el workflow se encarga
-- de borrar los archivos con la service role key.
create or replace function public.purge_expired_wallpapers(p_days int default 30)
returns int
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    purged int;
begin
    insert into public.storage_cleanup_queue (path)
    select expired.path
    from (
        select coalesce(
            nullif(btrim(w.storage_path), ''),
            case
                when w.image_url like '%/object/public/wallpapers/%'
                then substring(w.image_url from '/object/public/wallpapers/(.*)$')
                else null
            end
        ) as path
        from public.wallpapers as w
        where w.deleted_at is not null
          and w.deleted_at < now() - make_interval(
              days => greatest(coalesce(p_days, 30), 1)
          )
    ) as expired
    where expired.path is not null;

    delete from public.wallpapers
    where deleted_at is not null
      and deleted_at < now() - make_interval(
          days => greatest(coalesce(p_days, 30), 1)
      );

    get diagnostics purged = row_count;
    return purged;
end;
$$;

revoke all on function public.bulk_set_featured(uuid[], boolean) from public;
revoke all on function public.bulk_set_archived(uuid[], boolean) from public;
revoke all on function public.bulk_set_category(uuid[], uuid) from public;
revoke all on function public.soft_delete_wallpapers(uuid[]) from public;
revoke all on function public.restore_wallpapers(uuid[]) from public;
revoke all on function public.reorder_featured(uuid[]) from public;
revoke all on function public.purge_expired_wallpapers(int) from public;

grant execute on function public.bulk_set_featured(uuid[], boolean) to authenticated;
grant execute on function public.bulk_set_archived(uuid[], boolean) to authenticated;
grant execute on function public.bulk_set_category(uuid[], uuid) to authenticated;
grant execute on function public.soft_delete_wallpapers(uuid[]) to authenticated;
grant execute on function public.restore_wallpapers(uuid[]) to authenticated;
grant execute on function public.reorder_featured(uuid[]) to authenticated;

-- pg_cron puede no estar disponible en un proyecto local. Si está, programa la
-- purga todos los días a las 04:15 UTC.
do $$
begin
    create extension if not exists pg_cron;
exception
    when others then
        raise notice 'pg_cron no disponible: %', sqlerrm;
end;
$$;

do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        if exists (
            select 1 from cron.job where jobname = 'purge-expired-wallpapers'
        ) then
            perform cron.unschedule('purge-expired-wallpapers');
        end if;
        perform cron.schedule(
            'purge-expired-wallpapers',
            '15 4 * * *',
            'select public.purge_expired_wallpapers();'
        );
    end if;
end;
$$;

notify pgrst, 'reload schema';
