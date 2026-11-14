#!/usr/bin/env bash
# Vacía storage_cleanup_queue: borra los objetos de Storage y después las filas.
# Lo usa .github/workflows/cleanup-storage.yml con la service role key.
set -euo pipefail

url="${SUPABASE_URL:-}"
key="${SUPABASE_SERVICE_ROLE_KEY:-}"

if [[ -z "$url" || -z "$key" ]]; then
    echo "::warning::SUPABASE_URL o SUPABASE_SERVICE_ROLE_KEY no están configurados; se omite la limpieza de Storage."
    exit 0
fi

url="${url%/}"
api="$url/rest/v1/storage_cleanup_queue"
storage="$url/storage/v1/object/wallpapers"
headers=(-H "apikey: $key" -H "Authorization: Bearer $key")

queue="$(curl -fsS "$api?select=id,path&order=id.asc&limit=100" "${headers[@]}")"
count="$(jq 'length' <<<"$queue")"

if [[ "$count" == "0" ]]; then
    echo "No hay objetos pendientes."
    exit 0
fi

deleted_ids=()
failed_ids=()
while IFS=$'\t' read -r id path; do
    [[ -z "$id" || -z "$path" ]] && continue
    code="$(curl -sS -o /dev/null -w '%{http_code}' \
        -X DELETE "$storage/$path" "${headers[@]}" || true)"
    case "$code" in
        2*|404)
            deleted_ids+=("$id")
            ;;
        *)
            echo "::warning::No se pudo borrar $path (HTTP $code)."
            failed_ids+=("$id")
            ;;
    esac
done < <(jq -r '.[] | [.id, .path] | @tsv' <<<"$queue")

# Los que fallaron quedan en la cola con el intento contado, para poder ver
# cuáles se repiten.
if [[ ${#failed_ids[@]} -gt 0 ]]; then
    ids="$(IFS=,; echo "${failed_ids[*]}")"
    payload="$(jq -n --argjson ids "[$ids]" '{p_ids: $ids}')"
    curl -sS -o /dev/null -X POST "$url/rest/v1/rpc/fail_storage_cleanup" \
        "${headers[@]}" -H "Content-Type: application/json" \
        --data-binary "$payload" \
        || echo "::warning::No se pudieron registrar los intentos fallidos."
fi

if [[ ${#deleted_ids[@]} -eq 0 ]]; then
    echo "No se borró ningún objeto."
    exit 0
fi

ids="$(IFS=,; echo "${deleted_ids[*]}")"
curl -fsS -X DELETE "$api?id=in.($ids)" "${headers[@]}"
echo "Objetos limpiados: ${#deleted_ids[@]}"
