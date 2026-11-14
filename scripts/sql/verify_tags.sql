-- Pruebas de comportamiento de las etiquetas.
-- Cada bloque es una sola sentencia: si una comprobación falla, no queda nada
-- guardado. Al terminar borra sus propios datos.
-- La última sentencia devuelve una fila de control para el workflow.

do $$
declare
    v_id uuid;
    v_tags text[];
    v_label text := 'zz verificación de escritura';
begin
    -- Al guardar, cada etiqueta toma la escritura canónica y las variantes
    -- solo de mayúsculas no se duplican.
    insert into public.wallpapers (title, storage_path, tags)
    values (
        'Verificación de etiquetas',
        '__checks__/tag-casing.png',
        array['Cleopatra', 'cleopatra', v_label]
    )
    returning id, tags into v_id, v_tags;

    if v_tags is distinct from array['Cleopatra', v_label] then
        raise exception 'Etiquetas inesperadas al crear: %', v_tags;
    end if;

    -- El filtro por la clave en minúsculas encuentra el fondo.
    if not exists (
        select 1
        from public.wallpapers
        where id = v_id
          and tags_lower @> array['cleopatra']
    ) then
        raise exception 'El filtro por minúsculas no encontró el fondo';
    end if;

    -- Una actualización con otra capitalización vuelve a la forma canónica.
    update public.wallpapers
    set tags = array['CLEOPATRA']
    where id = v_id
    returning tags into v_tags;

    if v_tags is distinct from array['Cleopatra'] then
        raise exception 'Etiquetas inesperadas al actualizar: %', v_tags;
    end if;

    -- Las etiquetas compuestas se separan y no dejan repetidos.
    if public.normalize_wallpaper_tags(
        array['#playa #sol', '#PLAYA']
    ) <> array['playa', 'sol'] then
        raise exception 'La normalización no separa las etiquetas compuestas';
    end if;

    -- La limpieza conserva la escritura de una etiqueta que sigue en uso.
    perform public.prune_tag_labels();
    if not exists (
        select 1 from public.tag_labels where tag = 'cleopatra'
    ) then
        raise exception 'La limpieza borró una etiqueta en uso';
    end if;

    delete from public.wallpapers where id = v_id;
    delete from public.tag_labels where tag = lower(v_label);
end;
$$;

do $$
declare
    v_admin uuid;
    v_category uuid;
    v_id uuid;
    v_tags text[];
    v_old text := 'zz prueba renombrado';
    v_case text := 'ZZ Prueba Renombrado';
    v_new text := 'zz prueba renombrada';
begin
    select user_id into v_admin from public.admins limit 1;
    if v_admin is null then
        raise notice 'Sin administrador: se omite la prueba de renombrado';
        return;
    end if;

    select id into v_category from public.categories order by position, name limit 1;

    if v_category is not null then
        insert into public.tag_categories (tag, display_tag, category_id)
        values (lower(v_old), v_old, v_category)
        on conflict (tag) do update set display_tag = excluded.display_tag;
    end if;

    insert into public.wallpapers (title, storage_path, tags)
    values ('Renombrado de etiquetas', '__checks__/tag-rename.png', array[v_old])
    returning id into v_id;

    -- La prueba se ejecuta como el administrador configurado.
    perform set_config(
        'request.jwt.claims',
        json_build_object('sub', v_admin)::text,
        true
    );
    perform set_config('request.jwt.claim.sub', v_admin::text, true);

    if public.is_admin() then
        perform set_config('dannx.rename_test', 'aplicado', false);

        -- Un cambio solo de mayúsculas también se aplica.
        perform public.rename_wallpaper_tag(v_old, v_case);

        select tags into v_tags
        from public.wallpapers
        where id = v_id;

        if v_tags is distinct from array[v_case] then
            raise exception 'El renombrado de escritura no se aplicó: %', v_tags;
        end if;

        if not exists (
            select 1
            from public.tag_labels
            where tag = lower(v_case)
              and label = v_case
        ) then
            raise exception 'La escritura canónica no siguió el renombrado';
        end if;

        if v_category is not null and not exists (
            select 1
            from public.tag_categories
            where tag = lower(v_case)
              and display_tag = v_case
        ) then
            raise exception 'La regla no siguió el cambio de escritura';
        end if;

        -- Un renombrado con otra palabra se lleva también la regla.
        perform public.rename_wallpaper_tag(v_case, v_new);

        select tags into v_tags
        from public.wallpapers
        where id = v_id;

        if v_tags is distinct from array[v_new] then
            raise exception 'El renombrado de palabra no se aplicó: %', v_tags;
        end if;

        if not exists (
            select 1
            from public.tag_labels
            where tag = lower(v_new)
              and label = v_new
        ) then
            raise exception 'La escritura canónica no siguió el renombrado';
        end if;

        if v_category is not null and not exists (
            select 1
            from public.tag_categories
            where tag = lower(v_new)
              and display_tag = v_new
        ) then
            raise exception 'La regla no siguió el renombrado';
        end if;
    else
        perform set_config('dannx.rename_test', 'omitido', false);
        raise notice 'Sin sesión de administración: se omite el renombrado';
    end if;

    delete from public.wallpapers where id = v_id;
    delete from public.tag_labels
    where tag in (lower(v_old), lower(v_case), lower(v_new));
    delete from public.tag_categories
    where tag in (lower(v_old), lower(v_case), lower(v_new));
end;
$$;

select
    'etiquetas verificadas' as result,
    coalesce(current_setting('dannx.rename_test', true), 'sin datos') as renombrado;
