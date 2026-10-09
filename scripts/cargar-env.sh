#!/usr/bin/env bash
# Carga infra/.env como variables de entorno de la sesión actual.
# Uso: source scripts/cargar-env.sh [ruta/al/.env]
# Hay que usar `source` (o `.`): si se ejecuta como proceso aparte, las variables se pierden al terminar.
# No imprime valores: solo los nombres cargados.

_cargar_env() {
    local archivo="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/infra/.env}"
    local linea nombre valor cargadas=0

    if [ ! -f "$archivo" ]; then
        echo "No existe $archivo. Copiá infra/.env.example a infra/.env y completalo." >&2
        return 1
    fi

    while IFS= read -r linea || [ -n "$linea" ]; do
        linea="${linea%$'\r'}"
        case "$linea" in
            ''|'#'*|[[:space:]]*'#'*) continue ;;
        esac
        linea="${linea#export }"
        if [[ "$linea" != *=* ]]; then
            continue
        fi
        nombre="${linea%%=*}"
        valor="${linea#*=}"
        nombre="${nombre//[[:space:]]/}"
        if [[ ! "$nombre" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
            echo "Línea ignorada: nombre de variable inválido." >&2
            continue
        fi
        if [[ "$valor" =~ ^\"(.*)\"$ ]] || [[ "$valor" =~ ^\'(.*)\'$ ]]; then
            valor="${BASH_REMATCH[1]}"
        fi
        export "$nombre=$valor"
        echo "  $nombre"
        cargadas=$((cargadas + 1))
    done < "$archivo"

    echo "Cargadas $cargadas variables desde $archivo"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    echo "Este script se tiene que cargar con: source scripts/cargar-env.sh" >&2
    exit 1
fi

_cargar_env "$@"
_cargar_env_estado=$?
unset -f _cargar_env
return "$_cargar_env_estado" 2>/dev/null
