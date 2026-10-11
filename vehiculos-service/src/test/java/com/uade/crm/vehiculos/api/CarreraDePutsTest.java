package com.uade.crm.vehiculos.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uade.crm.vehiculos.FotosDePrueba;
import com.uade.crm.vehiculos.PruebaIntegracion;
import com.uade.crm.vehiculos.RelojDePrueba;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.FotoVehiculoService;
import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@PruebaIntegracion
@Import(CarreraDePutsTest.RepositorioConPausa.class)
class CarreraDePutsTest {

    private static final UUID ID = UUID.fromString("c0000000-0000-4000-8000-000000000003");
    private static final Instant CREADO = RelojDePrueba.INICIO.minus(Duration.ofDays(30));
    private static final BigDecimal PRECIO_NUEVO = new BigDecimal("2000000");

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RelojDePrueba reloj;

    @Autowired
    private ActualizacionVehiculoService actualizacion;

    @Autowired
    private FotoVehiculoService fotos;

    @Autowired
    private Pausa pausa;

    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    @BeforeEach
    void insertarUnidadConSuPrecioInicial() {
        reloj.reiniciar();
        jdbc.update("INSERT INTO " + schema + ".vehiculo (id, marca, modelo, anio, kilometraje, precio, disponible, "
                + "caja, creado_en, actualizado_en) VALUES (?, 'Ford', 'Ka', 2018, 70000, 1000000.00, TRUE, "
                + "'MANUAL', ?, ?)", ID, Timestamp.from(CREADO), Timestamp.from(CREADO));
        jdbc.update("INSERT INTO " + schema + ".precio_historial (id, vehiculo_id, precio, vigente_desde) "
                + "VALUES ('c0000000-0000-4000-8000-0000000000f3'::uuid, ?, 1000000.00, ?)", ID, Timestamp.from(CREADO));
    }

    @AfterEach
    void borrarLaUnidad() {
        hilos.shutdownNow();
        jdbc.update("DELETE FROM " + schema + ".precio_historial WHERE vehiculo_id = ?", ID);
        jdbc.update("DELETE FROM " + schema + ".vehiculo WHERE id = ?", ID);
        reloj.reiniciar();
    }

    @Test
    void unPutDeDisponibilidadNoRevierteUnCambioDePrecioConcurrente() throws Exception {
        correrConUnCambioDePrecioEnElMedio(() -> actualizacion.actualizar(ID, null, false));

        assertThat(disponible()).isFalse();
        assertThat(precioDeLaUnidad()).isEqualByComparingTo(PRECIO_NUEVO);
        assertThat(ultimoPrecioDelHistorial()).isEqualByComparingTo(PRECIO_NUEVO);
    }

    @Test
    void unaFotoNoRevierteUnCambioDePrecioConcurrente() throws Exception {
        correrConUnCambioDePrecioEnElMedio(() -> fotos.asignar(ID, FotosDePrueba.jpeg(100)));

        assertThat(fotoUrl()).isNotNull();
        assertThat(precioDeLaUnidad()).isEqualByComparingTo(PRECIO_NUEVO);
        assertThat(ultimoPrecioDelHistorial()).isEqualByComparingTo(PRECIO_NUEVO);
    }

    private void correrConUnCambioDePrecioEnElMedio(Runnable escrituraLenta) throws Exception {
        pausa.armar();
        Future<?> lenta = hilos.submit(escrituraLenta);
        assertThat(pausa.llego().await(10, TimeUnit.SECONDS)).isTrue();
        Future<?> cambioDePrecio = hilos.submit(() -> actualizacion.actualizar(ID, PRECIO_NUEVO, null));
        cambioDePrecio.get(20, TimeUnit.SECONDS);
        lenta.get(20, TimeUnit.SECONDS);
    }

    private BigDecimal precioDeLaUnidad() {
        return jdbc.queryForObject("SELECT precio FROM " + schema + ".vehiculo WHERE id = ?", BigDecimal.class, ID);
    }

    private Boolean disponible() {
        return jdbc.queryForObject("SELECT disponible FROM " + schema + ".vehiculo WHERE id = ?", Boolean.class, ID);
    }

    private String fotoUrl() {
        return jdbc.queryForObject("SELECT foto_url FROM " + schema + ".vehiculo WHERE id = ?", String.class, ID);
    }

    private BigDecimal ultimoPrecioDelHistorial() {
        return jdbc.queryForObject("SELECT precio FROM " + schema + ".precio_historial WHERE vehiculo_id = ? "
                + "ORDER BY vigente_desde DESC LIMIT 1", BigDecimal.class, ID);
    }

    static final class Pausa {

        private static final Duration DURACION = Duration.ofMillis(1500);

        private final AtomicBoolean armada = new AtomicBoolean();
        private volatile CountDownLatch llego = new CountDownLatch(1);

        void armar() {
            llego = new CountDownLatch(1);
            armada.set(true);
        }

        CountDownLatch llego() {
            return llego;
        }

        void pausarSiEstaArmada() {
            if (!armada.compareAndSet(true, false)) {
                return;
            }
            llego.countDown();
            try {
                Thread.sleep(DURACION);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @TestConfiguration
    static class RepositorioConPausa {

        @Bean
        Pausa pausa() {
            return new Pausa();
        }

        @Bean
        @Primary
        VehiculoRepositorio repositorioConPausa(@Qualifier("vehiculoRepositorioJpa") VehiculoRepositorio real,
                Pausa pausa) {
            return new VehiculoRepositorio() {

                @Override
                public List<Vehiculo> buscar(Boolean disponible) {
                    return real.buscar(disponible);
                }

                @Override
                public Optional<Vehiculo> buscarPorId(UUID id) {
                    return real.buscarPorId(id);
                }

                @Override
                public Optional<Vehiculo> buscarPorIdParaActualizar(UUID id) {
                    return real.buscarPorIdParaActualizar(id);
                }

                @Override
                public Vehiculo guardar(Vehiculo vehiculo) {
                    pausa.pausarSiEstaArmada();
                    return real.guardar(vehiculo);
                }

                @Override
                public void registrarPrecio(UUID vehiculoId, BigDecimal precio, Instant vigenteDesde) {
                    real.registrarPrecio(vehiculoId, precio, vigenteDesde);
                }

                @Override
                public List<PrecioHistorico> historialDePrecios(UUID vehiculoId) {
                    return real.historialDePrecios(vehiculoId);
                }
            };
        }
    }
}
