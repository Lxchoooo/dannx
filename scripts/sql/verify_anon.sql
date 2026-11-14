-- Comprueba que el rol anónimo no pueda escribir en las tablas principales.
-- Cada intento se espera que falle: si alguno funciona, la sentencia levanta un
-- error y la petición completa se revierte, así que no queda ningún dato.
-- La última sentencia devuelve una fila de control para el workflow.

do $$
declare
    v_anon uuid;
    v_wallpaper uuid;
    v_collection uuid;
    v_blocked boolean;
begin
    -- Referencias válidas para que el rechazo sea por permisos y no por claves.
    select id into v_wallpaper from public.wallpapers order by created_at limit 1;
    select id into v_collection from public.collections order by created_at limit 1;
    select id into v_anon from auth.users order by created_at limit 1;

    execute 'set local role anon';

    -- Fondos
    v_blocked := false;
    begin
        insert into public.wallpapers (title, storage_path)
        values ('zz verificación de permisos', '__checks__/permisos.png');
    exception
        when insufficient_privilege then v_blocked := true;
    end;
    if not v_blocked then
        raise exception 'El rol anónimo pudo insertar en wallpapers';
    end if;

    -- Colecciones
    if v_anon is not null then
        v_blocked := false;
        begin
            insert into public.collections (owner_id, name)
            values (v_anon, 'zz verificación de permisos');
        exception
            when insufficient_privilege then v_blocked := true;
        end;
        if not v_blocked then
            raise exception 'El rol anónimo pudo insertar en collections';
        end if;
    else
        raise notice 'Sin usuarios: se omite la prueba de colecciones';
    end if;

    -- Ítems de colecciones
    if v_collection is not null and v_wallpaper is not null then
        v_blocked := false;
        begin
            insert into public.collection_items (collection_id, wallpaper_id)
            values (v_collection, v_wallpaper);
        exception
            when insufficient_privilege then v_blocked := true;
        end;
        if not v_blocked then
            raise exception 'El rol anónimo pudo insertar en collection_items';
        end if;
    else
        raise notice 'Sin colecciones o fondos: se omite la prueba de ítems';
    end if;

    -- Archivos
    v_blocked := false;
    begin
        insert into storage.objects (bucket_id, name)
        values ('wallpapers', '__checks__/permisos.png');
    exception
        when insufficient_privilege then v_blocked := true;
    end;
    if not v_blocked then
        raise exception 'El rol anónimo pudo insertar en storage.objects';
    end if;

    -- Borrado: no debe afectar ninguna fila.
    if v_wallpaper is not null then
        delete from public.wallpapers where id = v_wallpaper;
        if found then
            raise exception 'El rol anónimo pudo borrar un fondo';
        end if;
    end if;
end;
$$;

select 'escrituras anónimas bloqueadas' as result;
