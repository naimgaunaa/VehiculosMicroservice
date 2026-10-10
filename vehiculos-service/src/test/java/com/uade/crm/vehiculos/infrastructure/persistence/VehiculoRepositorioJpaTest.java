package com.uade.crm.vehiculos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PruebaIntegracion;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import jakarta.persistence.EntityManager;

/**
 * Adaptador JPA contra PostgreSQL real. Antes de cada lectura se vacía la sesión de Hibernate
 * ({@link #sincronizar()}) para que el mapeo se compruebe contra la base y no contra el caché.
 */
@PruebaIntegracion
@Transactional
class VehiculoRepositorioJpaTest {

    @Autowired
    private VehiculoRepositorio repositorio;

    @Autowired
    private VehiculoJpaRepository jpa;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RelojDePrueba reloj;

    @BeforeEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    private Vehiculo.Builder unVehiculo() {
        return Vehiculo.builder(reloj)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("18500000.00"))
                .caja(Caja.AUTOMATICA);
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void guardaYLeeTodosLosCampos() {
        Vehiculo original = unVehiculo()
                .version("XEI CVT")
                .fotoUrl("https://ejemplo.test/fotos/corolla.jpg")
                .disponible(false)
                .build();

        repositorio.guardar(original);
        sincronizar();
        Vehiculo leido = repositorio.buscarPorId(original.id()).orElseThrow();

        assertThat(leido.id()).isEqualTo(original.id());
        assertThat(leido.marca()).isEqualTo("Toyota");
        assertThat(leido.modelo()).isEqualTo("Corolla");
        assertThat(leido.version()).contains("XEI CVT");
        assertThat(leido.anio()).isEqualTo(2021);
        assertThat(leido.kilometraje()).isEqualTo(45000);
        assertThat(leido.precio()).isEqualByComparingTo("18500000");
        assertThat(leido.disponible()).isFalse();
        assertThat(leido.caja()).isEqualTo(Caja.AUTOMATICA);
        assertThat(leido.fotoUrl()).contains("https://ejemplo.test/fotos/corolla.jpg");
    }

    @Test
    void guardaYLeeSinVersionNiFoto() {
        Vehiculo original = unVehiculo().caja(Caja.MANUAL).build();

        repositorio.guardar(original);
        sincronizar();
        Vehiculo leido = repositorio.buscarPorId(original.id()).orElseThrow();

        assertThat(leido.version()).isEmpty();
        assertThat(leido.fotoUrl()).isEmpty();
        assertThat(leido.disponible()).isTrue();
        assertThat(leido.caja()).isEqualTo(Caja.MANUAL);
    }

    /** Lo que devuelve {@code guardar} tiene que ser igual, escala incluida, a lo que se lee después. */
    @Test
    void elPrecioConservaLaEscalaEnLaIdaYVuelta() {
        Vehiculo original = unVehiculo().precio(new BigDecimal("10000000")).build();

        Vehiculo guardado = repositorio.guardar(original);
        sincronizar();
        Vehiculo leido = repositorio.buscarPorId(original.id()).orElseThrow();

        assertThat(guardado.precio()).isEqualTo(new BigDecimal("10000000.00"));
        assertThat(leido.precio()).isEqualTo(guardado.precio());
    }

    /** El alta consulta una sola vez si la unidad existe y después inserta, sin el SELECT extra de {@code merge}. */
    @Test
    void elAltaHaceUnSoloSelectAntesDelInsert() {
        Vehiculo vehiculo = unVehiculo().build();
        Statistics estadisticas = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        estadisticas.setStatisticsEnabled(true);
        try {
            repositorio.guardar(vehiculo);

            assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
            assertThat(estadisticas.getEntityInsertCount()).isEqualTo(1);
        } finally {
            estadisticas.setStatisticsEnabled(false);
        }
    }

    @Test
    void guardarDosVecesEnLaMismaTransaccionActualizaSinDuplicar() {
        Vehiculo vehiculo = unVehiculo().build();
        repositorio.guardar(vehiculo);
        vehiculo.actualizarPrecio(new BigDecimal("17000000"));

        repositorio.guardar(vehiculo);
        sincronizar();

        assertThat(jpa.count()).isEqualTo(1);
        assertThat(repositorio.buscarPorId(vehiculo.id()).orElseThrow().precio()).isEqualByComparingTo("17000000");
    }

    @Test
    void buscarPorIdDeUnaUnidadInexistenteDevuelveVacio() {
        assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void elAltaFijaCreadoEnYActualizadoEnConElReloj() {
        Vehiculo vehiculo = unVehiculo().build();

        repositorio.guardar(vehiculo);
        sincronizar();
        VehiculoEntity entidad = jpa.findById(vehiculo.id()).orElseThrow();

        assertThat(entidad.getCreadoEn()).isEqualTo(RelojDePrueba.INICIO);
        assertThat(entidad.getActualizadoEn()).isEqualTo(RelojDePrueba.INICIO);
    }

    @Test
    void actualizarConservaCreadoEnYRenuevaActualizadoEn() {
        Vehiculo vehiculo = unVehiculo().build();
        repositorio.guardar(vehiculo);
        sincronizar();
        Instant alta = reloj.instant();

        reloj.avanzar(Duration.ofHours(3));
        Vehiculo recuperado = repositorio.buscarPorId(vehiculo.id()).orElseThrow();
        recuperado.actualizarPrecio(new BigDecimal("17900000.50"));
        recuperado.cambiarDisponibilidad(false);
        recuperado.asignarFoto("https://ejemplo.test/fotos/nueva.png");
        repositorio.guardar(recuperado);
        sincronizar();

        VehiculoEntity entidad = jpa.findById(vehiculo.id()).orElseThrow();
        assertThat(entidad.getCreadoEn()).isEqualTo(alta);
        assertThat(entidad.getActualizadoEn()).isEqualTo(alta.plus(Duration.ofHours(3)));
        assertThat(entidad.getPrecio()).isEqualByComparingTo("17900000.50");
        assertThat(entidad.isDisponible()).isFalse();
        assertThat(entidad.getFotoUrl()).isEqualTo("https://ejemplo.test/fotos/nueva.png");
        assertThat(jpa.count()).isEqualTo(1);
    }

    @Test
    void buscarSinFiltroDevuelveTodasDeLaMasNuevaALaMasVieja() {
        Vehiculo primero = unVehiculo().marca("Ford").build();
        repositorio.guardar(primero);
        reloj.avanzar(Duration.ofMinutes(1));
        Vehiculo segundo = unVehiculo().marca("Fiat").disponible(false).build();
        repositorio.guardar(segundo);
        reloj.avanzar(Duration.ofMinutes(1));
        Vehiculo tercero = unVehiculo().marca("Honda").build();
        repositorio.guardar(tercero);
        sincronizar();

        List<Vehiculo> todas = repositorio.buscar(null);

        assertThat(todas).extracting(Vehiculo::id)
                .containsExactly(tercero.id(), segundo.id(), primero.id());
    }

    @Test
    void buscarFiltraPorDisponible() {
        Vehiculo disponible1 = unVehiculo().marca("Ford").build();
        Vehiculo disponible2 = unVehiculo().marca("Fiat").build();
        Vehiculo vendido = unVehiculo().marca("Honda").disponible(false).build();
        repositorio.guardar(disponible1);
        repositorio.guardar(disponible2);
        repositorio.guardar(vendido);
        sincronizar();

        assertThat(repositorio.buscar(true)).extracting(Vehiculo::id)
                .containsExactlyInAnyOrder(disponible1.id(), disponible2.id());
        assertThat(repositorio.buscar(false)).extracting(Vehiculo::id).containsExactly(vendido.id());
        assertThat(repositorio.buscar(null)).hasSize(3);
    }

    @Test
    void buscarSinDatosDevuelveListaVacia() {
        assertThat(repositorio.buscar(true)).isEmpty();
        assertThat(repositorio.buscar(null)).isEmpty();
    }

    @Test
    void elHistorialSeDevuelveDelMasNuevoAlMasViejo() {
        Vehiculo vehiculo = unVehiculo().build();
        repositorio.guardar(vehiculo);
        Instant t0 = reloj.instant();
        repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("18500000.00"), t0);
        repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("17500000.50"), t0.plus(Duration.ofDays(30)));
        repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("17000000.00"), t0.plus(Duration.ofDays(60)));
        sincronizar();

        List<PrecioHistorico> historial = repositorio.historialDePrecios(vehiculo.id());

        assertThat(historial).extracting(PrecioHistorico::vigenteDesde)
                .containsExactly(t0.plus(Duration.ofDays(60)), t0.plus(Duration.ofDays(30)), t0);
        assertThat(historial).extracting(PrecioHistorico::precio)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("17000000.00"), new BigDecimal("17500000.50"),
                        new BigDecimal("18500000.00"));
    }

    @Test
    void elHistorialEstaAisladoPorVehiculo() {
        Vehiculo uno = unVehiculo().build();
        Vehiculo otro = unVehiculo().marca("Fiat").build();
        repositorio.guardar(uno);
        repositorio.guardar(otro);
        repositorio.registrarPrecio(uno.id(), new BigDecimal("1000000"), reloj.instant());
        repositorio.registrarPrecio(otro.id(), new BigDecimal("2000000"), reloj.instant());
        sincronizar();

        assertThat(repositorio.historialDePrecios(uno.id())).hasSize(1);
        assertThat(repositorio.historialDePrecios(uno.id()).get(0).precio()).isEqualByComparingTo("1000000");
    }

    @Test
    void registrarPrecioEnElMismoInstanteQueLaUltimaFilaFalla() {
        Vehiculo vehiculo = unVehiculo().build();
        repositorio.guardar(vehiculo);
        repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("18500000.00"), reloj.instant());

        // @Repository traduce la IllegalStateException a la excepción de Spring para mal uso de la API de datos.
        assertThatThrownBy(() -> repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("17000000.00"), reloj.instant()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining(vehiculo.id().toString());
        sincronizar();
        assertThat(repositorio.historialDePrecios(vehiculo.id())).hasSize(1);
    }

    @Test
    void registrarPrecioAnteriorALaUltimaFilaFalla() {
        Vehiculo vehiculo = unVehiculo().build();
        repositorio.guardar(vehiculo);
        Instant t0 = reloj.instant();
        repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("18500000.00"), t0.plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("17000000.00"), t0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void registrarPrecioUnMicrosegundoDespuesDeLaUltimaFilaSeAcepta() {
        Vehiculo vehiculo = unVehiculo().build();
        repositorio.guardar(vehiculo);
        Instant t0 = reloj.instant();
        repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("18500000.00"), t0);
        repositorio.registrarPrecio(vehiculo.id(), new BigDecimal("17000000.00"), t0.plusNanos(1_000));
        sincronizar();

        assertThat(repositorio.historialDePrecios(vehiculo.id()).get(0).precio()).isEqualByComparingTo("17000000");
    }

    @Test
    void sinFilasElHistorialEstaVacio() {
        Vehiculo vehiculo = unVehiculo().build();
        repositorio.guardar(vehiculo);
        sincronizar();

        assertThat(repositorio.historialDePrecios(vehiculo.id())).isEmpty();
        assertThat(repositorio.historialDePrecios(UUID.randomUUID())).isEmpty();
    }

    /** Sin la transacción del test: el caso de uso que se olvide de abrir una falla en vez de commitear a medias. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void guardarFueraDeUnaTransaccionFallaSinEscribir() {
        Vehiculo vehiculo = unVehiculo().build();

        assertThatThrownBy(() -> repositorio.guardar(vehiculo))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(repositorio.buscarPorId(vehiculo.id())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registrarPrecioFueraDeUnaTransaccionFalla() {
        assertThatThrownBy(() -> repositorio.registrarPrecio(UUID.randomUUID(), BigDecimal.TEN, reloj.instant()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** Las lecturas abren su propia transacción de solo lectura si no hay una. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lasLecturasNoExigenTransaccion() {
        assertThat(repositorio.buscar(null)).isEmpty();
        assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
        assertThat(repositorio.historialDePrecios(UUID.randomUUID())).isEmpty();
    }

    @Test
    void registrarPrecioDeUnaUnidadInexistenteViolaLaClaveForanea() {
        assertThatThrownBy(() -> repositorio.registrarPrecio(UUID.randomUUID(), BigDecimal.TEN, reloj.instant()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
