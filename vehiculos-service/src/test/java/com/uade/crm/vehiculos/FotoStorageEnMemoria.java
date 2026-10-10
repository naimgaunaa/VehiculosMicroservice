package com.uade.crm.vehiculos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Primary;

import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.ArchivoFoto;
import com.uade.crm.vehiculos.domain.port.FotoStorage;

@Primary
public final class FotoStorageEnMemoria implements FotoStorage {

    public static final String BASE = "https://storage.ficticio.local/vehiculos-fotos/";

    private final Map<String, ArchivoFoto> objetos = new LinkedHashMap<>();
    private final List<String> eliminadas = new ArrayList<>();
    private boolean fallarSubida;
    private boolean fallarBorrado;

    public void fallarSubida() {
        fallarSubida = true;
    }

    public void fallarBorrado() {
        fallarBorrado = true;
    }

    public void reiniciar() {
        objetos.clear();
        eliminadas.clear();
        fallarSubida = false;
        fallarBorrado = false;
    }

    public void precargar(String url, ArchivoFoto archivo) {
        objetos.put(url, archivo);
    }

    public Map<String, ArchivoFoto> objetos() {
        return objetos;
    }

    public List<String> eliminadas() {
        return eliminadas;
    }

    @Override
    public String guardar(UUID vehiculoId, ArchivoFoto archivo) {
        if (fallarSubida) {
            throw new AlmacenamientoNoDisponibleException("Storage falso: subida rechazada");
        }
        String url = BASE + vehiculoId + "." + archivo.extension();
        objetos.put(url, archivo);
        return url;
    }

    @Override
    public void eliminar(String fotoUrl) {
        if (fallarBorrado) {
            throw new AlmacenamientoNoDisponibleException("Storage falso: borrado rechazado");
        }
        eliminadas.add(fotoUrl);
        objetos.remove(fotoUrl);
    }
}
