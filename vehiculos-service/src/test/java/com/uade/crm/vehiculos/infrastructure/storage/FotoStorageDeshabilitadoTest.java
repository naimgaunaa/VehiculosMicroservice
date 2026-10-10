package com.uade.crm.vehiculos.infrastructure.storage;

import static com.uade.crm.vehiculos.FotosDePrueba.png;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.ArchivoFoto;

class FotoStorageDeshabilitadoTest {

    private final FotoStorageDeshabilitado storage = new FotoStorageDeshabilitado();

    @Test
    void guardarRechazaLaCarga() {
        assertThatThrownBy(() -> storage.guardar(UUID.randomUUID(), ArchivoFoto.de(png(100))))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class)
                .hasMessageContaining("deshabilitado");
    }

    @Test
    void eliminarRechazaElBorrado() {
        assertThatThrownBy(() -> storage.eliminar("https://proyecto.supabase.co/storage/v1/object/public/b/x.png"))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class);
    }
}
