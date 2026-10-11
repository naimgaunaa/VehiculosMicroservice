package com.uade.crm.vehiculos.application;

import static com.uade.crm.vehiculos.FotosDePrueba.DOS_MB;
import static com.uade.crm.vehiculos.FotosDePrueba.jpeg;
import static com.uade.crm.vehiculos.FotosDePrueba.pdf;
import static com.uade.crm.vehiculos.FotosDePrueba.png;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.uade.crm.vehiculos.FotoStorageEnMemoria;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.ArchivoFoto;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.FotoInvalidaException;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;
import com.uade.crm.vehiculos.infrastructure.storage.FotoStorageDeshabilitadoDePrueba;

@ExtendWith(MockitoExtension.class)
class FotoVehiculoServiceTest {

    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");
    private static final String URL_PNG = FotoStorageEnMemoria.BASE + ID + ".png";
    private static final String URL_JPG = FotoStorageEnMemoria.BASE + ID + ".jpg";

    @Mock
    private VehiculoRepositorio repositorio;

    private final FotoStorageEnMemoria storage = new FotoStorageEnMemoria();
    private final List<String> eliminadasAlConfirmar = new ArrayList<>();
    private int transacciones;

    private FotoVehiculoService servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new FotoVehiculoService(repositorio, storage, transaccionQueRegistra());
    }

    @Test
    void unaUnidadInexistenteDa404SinSubir() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignar(ID, png(100)))
                .isInstanceOf(VehiculoNoEncontradoException.class);
        assertThat(storage.objetos()).isEmpty();
        verify(repositorio, never()).guardar(any());
    }

    @Test
    void unaUnidadInexistenteDa404AunqueElArchivoSeaInvalido() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignar(ID, pdf(100)))
                .isInstanceOf(VehiculoNoEncontradoException.class);
    }

    @Test
    void unTipoInvalidoDa422SinSubir() {
        unidadSinFoto();

        assertThatThrownBy(() -> servicio.asignar(ID, "texto plano".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(FotoInvalidaException.class);
        sinSubirNiGuardar();
    }

    @Test
    void unArchivoVacioDa422SinSubir() {
        unidadSinFoto();

        assertThatThrownBy(() -> servicio.asignar(ID, new byte[0])).isInstanceOf(FotoInvalidaException.class);
        sinSubirNiGuardar();
    }

    @Test
    void unArchivoDeMasDeDosMbDa422SinSubir() {
        unidadSinFoto();

        assertThatThrownBy(() -> servicio.asignar(ID, png(DOS_MB + 1))).isInstanceOf(FotoInvalidaException.class);
        sinSubirNiGuardar();
    }

    @Test
    void unPdfRenombradoComoPngDa422SinSubir() {
        unidadSinFoto();

        assertThatThrownBy(() -> servicio.asignar(ID, pdf(2048))).isInstanceOf(FotoInvalidaException.class);
        sinSubirNiGuardar();
    }

    @Test
    void unPngValidoSeSubeSeAsignaYSeGuardaUnaVez() {
        unidadSinFoto();
        releeDentroDeLaTransaccion(unidad(null));
        guardarDevuelveLoRecibido();

        Vehiculo resultado = servicio.asignar(ID, png(1024));

        assertThat(resultado.fotoUrl()).contains(URL_PNG);
        assertThat(storage.objetos()).containsOnlyKeys(URL_PNG);
        ArgumentCaptor<Vehiculo> guardado = ArgumentCaptor.forClass(Vehiculo.class);
        verify(repositorio).guardar(guardado.capture());
        assertThat(guardado.getValue().fotoUrl()).contains(URL_PNG);
        assertThat(transacciones).isEqualTo(1);
        assertThat(storage.eliminadas()).isEmpty();
    }

    @Test
    void reemplazarPorOtraExtensionEliminaElObjetoViejoDespuesDeConfirmar() {
        storage.precargar(URL_PNG, ArchivoFoto.de(png(100)));
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(unidad(URL_PNG)));
        releeDentroDeLaTransaccion(unidad(URL_PNG));
        guardarDevuelveLoRecibido();

        Vehiculo resultado = servicio.asignar(ID, jpeg(1024));

        assertThat(resultado.fotoUrl()).contains(URL_JPG);
        assertThat(storage.objetos()).containsOnlyKeys(URL_JPG);
        assertThat(storage.eliminadas()).containsExactly(URL_PNG);
        assertThat(eliminadasAlConfirmar).isEmpty();
    }

    @Test
    void reemplazarConLaMismaExtensionNoElimina() {
        storage.precargar(URL_PNG, ArchivoFoto.de(png(100)));
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(unidad(URL_PNG)));
        releeDentroDeLaTransaccion(unidad(URL_PNG));
        guardarDevuelveLoRecibido();

        servicio.asignar(ID, png(2048));

        assertThat(storage.eliminadas()).isEmpty();
        assertThat(storage.objetos().get(URL_PNG).tamanio()).isEqualTo(2048);
    }

    @Test
    void siFallaElBorradoDelViejoIgualDevuelveLaUnidadGuardada() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(unidad(URL_PNG)));
        releeDentroDeLaTransaccion(unidad(URL_PNG));
        guardarDevuelveLoRecibido();
        storage.fallarBorrado();

        Vehiculo resultado = servicio.asignar(ID, jpeg(1024));

        assertThat(resultado.fotoUrl()).contains(URL_JPG);
        verify(repositorio).guardar(any());
    }

    @Test
    void siFallaElAlmacenamientoDa503SinGuardar() {
        unidadSinFoto();
        storage.fallarSubida();

        assertThatThrownBy(() -> servicio.asignar(ID, png(1024)))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class);
        verify(repositorio, never()).guardar(any());
        assertThat(transacciones).isZero();
    }

    @Test
    void enModoDeshabilitadoDa503SinGuardar() {
        unidadSinFoto();
        FotoVehiculoService deshabilitado = new FotoVehiculoService(repositorio,
                FotoStorageDeshabilitadoDePrueba.crear(), transaccionQueRegistra());

        assertThatThrownBy(() -> deshabilitado.asignar(ID, png(1024)))
                .isInstanceOf(AlmacenamientoNoDisponibleException.class);
        verify(repositorio, never()).guardar(any());
    }

    @Test
    void enModoDeshabilitadoUnArchivoInvalidoDa422() {
        unidadSinFoto();
        FotoVehiculoService deshabilitado = new FotoVehiculoService(repositorio,
                FotoStorageDeshabilitadoDePrueba.crear(), transaccionQueRegistra());

        assertThatThrownBy(() -> deshabilitado.asignar(ID, pdf(1024))).isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void releeLaUnidadDentroDeLaTransaccionParaNoPisarUnCambioDePrecio() {
        Vehiculo leida = unidad(null);
        Vehiculo actualizada = unidad(null);
        actualizada.actualizarPrecio(new BigDecimal("11000000"));
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(leida));
        releeDentroDeLaTransaccion(actualizada);
        guardarDevuelveLoRecibido();

        servicio.asignar(ID, png(1024));

        ArgumentCaptor<Vehiculo> guardado = ArgumentCaptor.forClass(Vehiculo.class);
        verify(repositorio).guardar(guardado.capture());
        assertThat(guardado.getValue().precio()).isEqualByComparingTo("11000000");
        assertThat(guardado.getValue().fotoUrl()).contains(URL_PNG);
    }

    private TransactionOperations transaccionQueRegistra() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> accion) {
                transacciones++;
                T resultado = accion.doInTransaction(new SimpleTransactionStatus());
                eliminadasAlConfirmar.addAll(storage.eliminadas());
                return resultado;
            }
        };
    }

    private void unidadSinFoto() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(unidad(null)));
    }

    private void releeDentroDeLaTransaccion(Vehiculo unidad) {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad));
    }

    private void guardarDevuelveLoRecibido() {
        when(repositorio.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private void sinSubirNiGuardar() {
        assertThat(storage.objetos()).isEmpty();
        verify(repositorio, never()).guardar(any());
    }

    private static Vehiculo unidad(String fotoUrl) {
        return Vehiculo.builder(new RelojDePrueba())
                .id(ID)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .caja(Caja.AUTOMATICA)
                .fotoUrl(fotoUrl)
                .build();
    }
}
