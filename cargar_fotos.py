import json
import os
import sys
import tempfile
from pathlib import Path

import requests
from PIL import Image

BASE_URL = "https://vehiculosmicroservice.onrender.com"
RAIZ = Path(__file__).resolve().parent
ENTRADA = RAIZ / "vehiculos_cargados.json"
FOTOS = RAIZ / "fotos"
MAXIMO = 2 * 1024 * 1024
MIME = {"jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}


def tipo_real(ruta):
    with open(ruta, "rb") as f:
        cabecera = f.read(12)
    if cabecera.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    if cabecera.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if cabecera[:4] == b"RIFF" and cabecera[8:12] == b"WEBP":
        return "webp"
    return None


def comprimir(ruta, destino):
    with Image.open(ruta) as original:
        imagen = original.copy()
    con_alfa = imagen.mode in ("RGBA", "LA") or "transparency" in imagen.info
    formato, extension = ("WEBP", "webp") if con_alfa else ("JPEG", "jpeg")
    if formato == "JPEG":
        imagen = imagen.convert("RGB")
    salida = destino / f"{ruta.stem}.{extension}"
    escala, calidad = 1.0, 85
    while True:
        tamanio = (max(1, int(imagen.width * escala)), max(1, int(imagen.height * escala)))
        imagen.resize(tamanio, Image.LANCZOS).save(salida, formato, quality=calidad)
        if salida.stat().st_size <= MAXIMO:
            return salida, extension
        if calidad > 70:
            calidad -= 5
        else:
            escala *= 0.85


def error_de(respuesta):
    try:
        cuerpo = respuesta.json()
    except ValueError:
        cuerpo = {}
    return {
        "status": respuesta.status_code,
        "codigo": cuerpo.get("codigo"),
        "correlationId": cuerpo.get("correlationId") or respuesta.headers.get("X-Correlation-Id"),
        "detalle": cuerpo.get("detail"),
    }


def main():
    clave = os.environ.get("X_API_KEY")
    if not clave:
        print("Falta la variable de entorno X_API_KEY.")
        return 1

    vehiculos = json.loads(ENTRADA.read_text(encoding="utf-8"))
    requests.get(f"{BASE_URL}/vehiculos", timeout=90).raise_for_status()

    resultados = []
    avisos = []
    with tempfile.TemporaryDirectory() as temporal:
        temporal = Path(temporal)
        for v in vehiculos:
            fila = {"numero": v["numero"], "id": v["id"], "marca": v["marca"], "modelo": v["modelo"], "archivo": None}
            resultados.append(fila)
            carpeta = FOTOS / v["carpeta"]
            archivos = sorted(p for p in carpeta.iterdir() if p.is_file() and not p.name.startswith(".")) if carpeta.is_dir() else []
            if not archivos:
                fila["error"] = "carpeta vacía, salteado"
                avisos.append(f"{v['numero']}: carpeta {v['carpeta']} vacía, se salteó")
                continue
            elegido = archivos[0]
            fila["archivo"] = elegido.name
            if len(archivos) > 1:
                avisos.append(f"{v['numero']}: {len(archivos)} archivos, se usó {elegido.name}")

            tipo = tipo_real(elegido)
            if tipo is None:
                fila["error"] = "no es JPEG, PNG ni WebP por contenido"
                continue

            a_subir = elegido
            if elegido.stat().st_size > MAXIMO:
                a_subir, tipo = comprimir(elegido, temporal)
                fila["archivo"] = f"{elegido.name} (comprimido a {a_subir.stat().st_size // 1024} KB)"
                avisos.append(f"{v['numero']}: {elegido.name} superaba 2 MB, se subió una copia comprimida")

            with open(a_subir, "rb") as f:
                respuesta = requests.put(
                    f"{BASE_URL}/vehiculos/{v['id']}/foto",
                    headers={"X-Api-Key": clave},
                    files={"archivo": (a_subir.name, f, MIME[tipo])},
                    timeout=120,
                )
            fila["status"] = respuesta.status_code
            if respuesta.status_code == 200:
                fila["fotoUrl"] = respuesta.json().get("fotoUrl")
                print(f"{v['numero']} subida: {fila['archivo']}")
                continue
            fila["error"] = error_de(respuesta)
            print(f"{v['numero']} falló: {json.dumps(fila['error'], ensure_ascii=False)}")
            if respuesta.status_code == 503:
                print("503 ALMACENAMIENTO_NO_DISPONIBLE: se frena. Revisar VEHICULOS_FOTOS_MODO en Render.")
                break

    print()
    for fila in resultados:
        if fila.get("status") == 200:
            actual = requests.get(f"{BASE_URL}/vehiculos/{fila['id']}", timeout=60).json()
            fila["verificado"] = bool(actual.get("fotoUrl"))
            fila["fotoUrl"] = actual.get("fotoUrl")
        print(json.dumps(fila, ensure_ascii=False))
    for aviso in avisos:
        print("AVISO", aviso)
    return 0 if all(f.get("verificado") for f in resultados) else 3


if __name__ == "__main__":
    sys.exit(main())
