-- Pruebas de comportamiento de las etiquetas.
-- Se ejecuta dentro de una transacción que se descarta: no deja datos.
-- Si alguna comprobación falla, la consulta devuelve error y el workflow corta.

begin;

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
    if public.normalize_wallpaper_tags(array['#playa #sol', '#PLAYA']) <> array['playa', 'sol'] then
        raise exception 'La normalización no separa las etiquetas compuestas';
    end if;

    delete from public.wallpapers where id = v_id;
    delete from public.tag_labels where tag = lower(v_label);
end;
$$;

rollback;
