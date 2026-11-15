package com.uade.crm.vehiculos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.application.MultasVehiculoService.MultasDeVehiculo;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.Multa;
import com.uade.crm.vehiculos.domain.model.PatenteNoCoincideException;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@ExtendWith(MockitoExtension.class)
class MultasVehiculoServiceTest {

    private static final UUID ID = UUID.fromString("9e8d7c6b-5a4f-4e3d-8c1b-0a9f8e7d6c5b");

    @Mock
    private VehiculoRepositorio repositorio;

    private MultasVehiculoService servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new MultasVehiculoService(repositorio, new RelojDePrueba());
    }

    @Test
    void guardaElSubtotalYLaFechaDeLaConsultaYReemplazaLasActas() {
        Vehiculo vehiculo = unidad("AB123CD");
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(vehiculo));
        List<Multa> actas = List.of(acta("A-1", "33150"), acta("A-2", "10000"));

        servicio.registrar(ID, "AB123CD", new BigDecimal("49000"), actas);

        InOrder orden = inOrder(repositorio);
        ArgumentCaptor<Vehiculo> guardado = ArgumentCaptor.forClass(Vehiculo.class);
        orden.verify(repositorio).guardar(guardado.capture());
        orden.verify(repositorio).reemplazarMultas(ID, actas);
        assertThat(guardado.getValue().multasSubtotal()).contains(new BigDecimal("49000.00"));
        assertThat(guardado.getValue().multasConsultadasEn()).contains(RelojDePrueba.INICIO);
    }

    @Test
    void unaPatenteSinMultasReemplazaLasActasAnterioresPorNinguna() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad("AB123CD")));

        servicio.registrar(ID, "AB123CD", BigDecimal.ZERO, List.of());

        verify(repositorio).reemplazarMultas(ID, List.of());
    }

    @Test
    void elDominioSeComparaNormalizado() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad("AB123CD")));

        servicio.registrar(ID, " ab-123 cd", BigDecimal.ZERO, List.of());

        verify(repositorio).guardar(any());
    }

    @Test
    void sinDominioSeAceptaElResultado() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad("AB123CD")));

        servicio.registrar(ID, null, BigDecimal.ZERO, List.of());

        verify(repositorio).guardar(any());
    }

    @Test
    void unaUnidadInexistenteLanzaNoEncontrada() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(ID, "AB123CD", BigDecimal.ZERO, List.of()))
                .isInstanceOf(VehiculoNoEncontradoException.class);
        verify(repositorio, never()).guardar(any());
    }

    @Test
    void unaUnidadSinPatenteNoPuedeRecibirMultas() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad(null)));

        assertThatThrownBy(() -> servicio.registrar(ID, "AB123CD", BigDecimal.ZERO, List.of()))
                .isInstanceOf(PatenteNoCoincideException.class)
                .hasMessageContaining("no tiene patente");
        verify(repositorio, never()).guardar(any());
        verify(repositorio, never()).reemplazarMultas(any(), any());
    }

    @Test
    void multasDeOtraPatenteSeRechazanSinTocarNada() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad("AB123CD")));

        assertThatThrownBy(() -> servicio.registrar(ID, "ZZZ999", BigDecimal.ZERO, List.of(acta("A-1", "1"))))
                .isInstanceOf(PatenteNoCoincideException.class);
        verify(repositorio, never()).guardar(any());
        verify(repositorio, never()).reemplazarMultas(any(), any());
    }

    @Test
    void unDominioConFormatoInvalidoEsUnDatoInvalido() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad("AB123CD")));

        assertThatThrownBy(() -> servicio.registrar(ID, "???", BigDecimal.ZERO, List.of()))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void dosActasConElMismoNumeroSonUnDatoInvalido() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad("AB123CD")));

        assertThatThrownBy(() -> servicio.registrar(ID, "AB123CD", BigDecimal.TEN,
                List.of(acta("A-1", "5"), acta("A-1", "5"))))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("A-1");
        verify(repositorio, never()).guardar(any());
    }

    @Test
    void unSubtotalNegativoEsUnDatoInvalido() {
        when(repositorio.buscarPorIdParaActualizar(ID)).thenReturn(Optional.of(unidad("AB123CD")));

        assertThatThrownBy(() -> servicio.registrar(ID, "AB123CD", new BigDecimal("-1"), List.of()))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void obtenerDevuelveLaUnidadConSusActas() {
        Vehiculo vehiculo = unidad("AB123CD");
        List<Multa> actas = List.of(acta("A-1", "100"));
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(vehiculo));
        when(repositorio.multasDe(ID)).thenReturn(actas);

        MultasDeVehiculo datos = servicio.obtener(ID);

        assertThat(datos.vehiculo()).isSameAs(vehiculo);
        assertThat(datos.multas()).isEqualTo(actas);
    }

    @Test
    void obtenerUnaUnidadInexistenteLanzaNoEncontrada() {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(ID)).isInstanceOf(VehiculoNoEncontradoException.class);
    }

    private static Vehiculo unidad(String patente) {
        return Vehiculo.builder(new RelojDePrueba()).id(ID).marca("Toyota").modelo("Corolla").anio(2021)
                .kilometraje(45000).precio(new BigDecimal("10000000")).caja(Caja.MANUAL).patente(patente).build();
    }

    private static Multa acta(String numero, String importe) {
        return new Multa(numero, new BigDecimal(importe), null, "LEY 24449", "Córdoba", "Juzgado 1");
    }
}
