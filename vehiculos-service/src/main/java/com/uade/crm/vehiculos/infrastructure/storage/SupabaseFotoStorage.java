package com.uade.crm.vehiculos.infrastructure.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.uade.crm.vehiculos.config.VehiculosProperties;
import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.ArchivoFoto;
import com.uade.crm.vehiculos.domain.port.FotoStorage;

class SupabaseFotoStorage implements FotoStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabaseFotoStorage.class);

    private final RestClient cliente;
    private final String bucket;
    private final String prefijoPublico;

    SupabaseFotoStorage(RestClient.Builder builder, VehiculosProperties propiedades) {
        String base = propiedades.supabaseUrl().replaceAll("/+$", "") + "/storage/v1/object";
        this.bucket = propiedades.bucketFotos();
        this.prefijoPublico = base + "/public/" + bucket + "/";
        this.cliente = builder
                .baseUrl(base)
                .defaultHeader("apikey", propiedades.supabaseClaveSecreta())
                .build();
    }

    @Override
    public String guardar(UUID vehiculoId, ArchivoFoto archivo) {
        String nombre = vehiculoId + "." + archivo.extension();
        try {
            cliente.post()
                    .uri("/{bucket}/{nombre}", bucket, nombre)
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(archivo.tipo().contentType()))
                    .body(archivo.bytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw falla("subir", nombre, e);
        }
        return prefijoPublico + nombre;
    }

    @Override
    public void eliminar(String fotoUrl) {
        Optional<String> nombre = nombreDelObjeto(fotoUrl);
        if (nombre.isEmpty()) {
            return;
        }
        try {
            cliente.method(HttpMethod.DELETE)
                    .uri("/{bucket}", bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", List.of(nombre.get())))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw falla("eliminar", nombre.get(), e);
        }
    }

    private Optional<String> nombreDelObjeto(String fotoUrl) {
        if (fotoUrl == null || !fotoUrl.startsWith(prefijoPublico)) {
            return Optional.empty();
        }
        String nombre = fotoUrl.substring(prefijoPublico.length());
        return nombre.isEmpty() || nombre.contains("/") ? Optional.empty() : Optional.of(nombre);
    }

    private static AlmacenamientoNoDisponibleException falla(String operacion, String nombre, RestClientException e) {
        if (e instanceof RestClientResponseException respuesta) {
            log.warn("Supabase Storage respondió {} al {} {}: {}", respuesta.getStatusCode().value(), operacion,
                    nombre, respuesta.getResponseBodyAsString());
        } else {
            log.warn("Supabase Storage no respondió al {} {}: {}", operacion, nombre, e.getMessage());
        }
        return new AlmacenamientoNoDisponibleException("El almacenamiento de fotos no está disponible", e);
    }
}
