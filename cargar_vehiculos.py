import json
import os
import re
import sys
import unicodedata
from pathlib import Path

import requests

BASE_URL = "https://vehiculosmicroservice.onrender.com"
RAIZ = Path(__file__).resolve().parent
SALIDA = RAIZ / "vehiculos_cargados.json"
FOTOS = RAIZ / "fotos"

COROLLA = {"marca": "Toyota", "modelo": "Corolla", "version": "XEI CVT", "anio": 2021}

VEHICULOS = [
    {"marca": "Volkswagen", "modelo": "Amarok", "version": "Highline 2.0 TDI 180cv 4x4 AT", "anio": 2019, "kilometraje": 98000, "precio": 35500000, "disponible": True, "caja": "AUTOMATICA"},
    {"marca": "Toyota", "modelo": "Hilux", "version": "SRV 2.8 TDI 4x4 AT", "anio": 2020, "kilometraje": 85000, "precio": 42000000, "disponible": True, "caja": "AUTOMATICA"},
    {"marca": "Ford", "modelo": "Ranger", "version": "XLT 3.2 TDCi 4x4 MT", "anio": 2017, "kilometraje": 140000, "precio": 28000000, "disponible": False, "caja": "MANUAL"},
    {"marca": "Fiat", "modelo": "Toro", "version": "Freedom 2.0 TD 4x4 AT9", "anio": 2019, "kilometraje": 105000, "precio": 24500000, "disponible": True, "caja": "AUTOMATICA"},
    {"marca": "Volkswagen", "modelo": "Gol Trend", "version": "Trendline 1.6", "anio": 2015, "kilometraje": 112000, "precio": 10200000, "disponible": False, "caja": "MANUAL"},
    {"marca": "Fiat", "modelo": "Cronos", "version": "Drive 1.3 GSE", "anio": 2024, "kilometraje": 15000, "precio": 21500000, "disponible": True, "caja": "MANUAL"},
    {"marca": "Peugeot", "modelo": "208", "version": "Allure 1.6 AT", "anio": 2023, "kilometraje": 28000, "precio": 25000000, "disponible": True, "caja": "AUTOMATICA"},
    {"marca": "Chevrolet", "modelo": "Onix", "version": "LT 1.2", "anio": 2020, "kilometraje": 60000, "precio": 15500000, "disponible": True, "caja": "MANUAL"},
    {"marca": "Toyota", "modelo": "Etios", "version": "XLS 1.5", "anio": 2018, "kilometraje": 75000, "precio": 13200000, "disponible": True, "caja": "MANUAL"},
    {"marca": "Ford", "modelo": "Ka", "version": "SE 1.5", "anio": 2016, "kilometraje": 95000, "precio": 11000000, "disponible": True, "caja": "MANUAL"},
    {"marca": "Nissan", "modelo": "Kicks", "version": "Exclusive 1.6 CVT", "anio": 2019, "kilometraje": 62000, "precio": 21000000, "disponible": True, "caja": "AUTOMATICA"},
    {"marca": "Chevrolet", "modelo": "Tracker", "version": "Premier 1.2 Turbo AT", "anio": 2021, "kilometraje": 45000, "precio": 25500000, "disponible": True, "caja": "AUTOMATICA"},
    {"marca": "Jeep", "modelo": "Renegade", "version": "Sport 1.8 AT", "anio": 2018, "kilometraje": 80000, "precio": 19500000, "disponible": True, "caja": "AUTOMATICA"},
    {"marca": "Ford", "modelo": "EcoSport", "version": "Titanium 1.6", "anio": 2013, "kilometraje": 130000, "precio": 11500000, "disponible": False, "caja": "MANUAL"},
]


def carpeta(numero, marca, modelo, anio):
    texto = unicodedata.normalize("NFKD", f"{marca} {modelo} {anio}").encode("ascii", "ignore").decode()
    texto = re.sub(r"[^a-z0-9]+", "-", texto.lower()).strip("-")
    return f"{numero:02d}-{texto}"


def registro(numero, id_, datos):
    return {
        "numero": f"{numero:02d}",
        "id": id_,
        "marca": datos["marca"],
        "modelo": datos["modelo"],
        "version": datos["version"],
        "anio": datos["anio"],
        "carpeta": carpeta(numero, datos["marca"], datos["modelo"], datos["anio"]),
    }


def guardar(registros):
    SALIDA.write_text(json.dumps(registros, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


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
        "errores": cuerpo.get("errores"),
    }


def main():
    if SALIDA.exists():
        print(f"{SALIDA.name} ya existe: no se crean vehículos.")
        return 0

    clave = os.environ.get("X_API_KEY")
    if not clave:
        print("Falta la variable de entorno X_API_KEY.")
        return 1

    existentes = requests.get(f"{BASE_URL}/vehiculos", timeout=90)
    existentes.raise_for_status()
    coincidencias = [v for v in existentes.json() if all(v.get(k) == COROLLA[k] for k in COROLLA)]
    if len(coincidencias) != 1:
        print(f"Se esperaba un Corolla XEI CVT 2021 y hay {len(coincidencias)}. No se crea nada.")
        return 2

    registros = [registro(0, coincidencias[0]["id"], COROLLA)]
    guardar(registros)

    fallidos = []
    for numero, datos in enumerate(VEHICULOS, start=1):
        respuesta = requests.post(f"{BASE_URL}/vehiculos", json=datos, headers={"X-Api-Key": clave}, timeout=60)
        if respuesta.status_code == 201:
            registros.append(registro(numero, respuesta.json()["id"], datos))
            guardar(registros)
            print(f"{numero:02d} creado: {datos['marca']} {datos['modelo']} {datos['anio']}")
        else:
            fallo = {"numero": f"{numero:02d}", "marca": datos["marca"], "modelo": datos["modelo"], **error_de(respuesta)}
            fallidos.append(fallo)
            print(f"{numero:02d} falló: {json.dumps(fallo, ensure_ascii=False)}")

    for r in registros:
        (FOTOS / r["carpeta"]).mkdir(parents=True, exist_ok=True)

    if fallidos:
        print(f"\nFallaron {len(fallidos)}:")
        for f in fallidos:
            print(json.dumps(f, ensure_ascii=False))
        return 3
    print(f"\nCreados {len(registros) - 1} vehículos.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
