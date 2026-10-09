package com.uade.crm.vehiculos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PostgresDePrueba;
import com.uade.crm.vehiculos.PropiedadesDePrueba;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

/**
 * El seed de desarrollo con el perfil {@code seed}. Tiene contexto propio (otro perfil activo), así que no
 * contamina a los tests que esperan la base vacía. {@code @ActiveProfiles} directo reemplaza al de
 * {@link PropiedadesDePrueba}, por eso se repite {@code test}.
 */
@SpringBootTest
@Import(PostgresDePrueba.class)
@PropiedadesDePrueba
@ActiveProfiles({"test", "seed"})
@Transactional
class SeedTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VehiculoRepositorio repositorio;

    @Test
    void flywayAplicoLaV1YElSeedRepetible() {
        List<Map<String, Object>> aplicadas = jdbc.queryForList(
                "SELECT version, script FROM vehiculos.flyway_schema_history WHERE success AND type = 'SQL' ORDER BY installed_rank");

        assertThat(aplicadas).extracting(fila -> fila.get("version")).containsExactly("1", null);
        assertThat(aplicadas.get(1).get("script")).isEqualTo("R__seed_vehiculos.sql");
    }

    @Test
    void cargaDiezVehiculosQueElDominioPuedeLeer() {
        List<Vehiculo> todos = repositorio.buscar(null);

        assertThat(todos).hasSize(10);
    }

    @Test
    void tieneVariedadDeMarcasCajasAniosPreciosYKilometrajes() {
        List<Vehiculo> todos = repositorio.buscar(null);

        assertThat(todos).extracting(Vehiculo::marca).doesNotHaveDuplicates();
        assertThat(todos).extracting(Vehiculo::caja).contains(Caja.AUTOMATICA, Caja.MANUAL);
        assertThat(todos).extracting(Vehiculo::anio).doesNotHaveDuplicates();
        assertThat(todos).extracting(Vehiculo::precio).doesNotHaveDuplicates();
        assertThat(todos).extracting(Vehiculo::kilometraje).doesNotHaveDuplicates();
        assertThat(todos).anyMatch(v -> v.version().isPresent()).anyMatch(v -> v.version().isEmpty());
    }

    @Test
    void tieneUnidadesDisponiblesYNoDisponibles() {
        assertThat(repositorio.buscar(false)).isNotEmpty();
        assertThat(repositorio.buscar(true)).isNotEmpty();
        assertThat(repositorio.buscar(true).size() + repositorio.buscar(false).size()).isEqualTo(10);
    }

    @Test
    void todasLasFotosQuedanEnNull() {
        assertThat(repositorio.buscar(null)).allMatch(v -> v.fotoUrl().isEmpty());
    }

    @Test
    void cadaUnidadTieneUnaSolaFilaDeHistorialConSuPrecio() {
        for (Vehiculo vehiculo : repositorio.buscar(null)) {
            List<PrecioHistorico> historial = repositorio.historialDePrecios(vehiculo.id());

            assertThat(historial).as("historial de %s %s", vehiculo.marca(), vehiculo.modelo()).hasSize(1);
            assertThat(historial.get(0).precio()).isEqualByComparingTo(vehiculo.precio());
        }
    }

    @Test
    void elScriptEsIdempotente() throws Exception {
        long vehiculosAntes = contar("vehiculo");
        long historialAntes = contar("precio_historial");
        BigDecimal sumaAntes = jdbc.queryForObject("SELECT SUM(precio) FROM vehiculos.vehiculo", BigDecimal.class);

        // Flyway no vuelve a correr una repetible sin cambios, así que se ejecuta el script a mano.
        // Flyway fija el search_path en su conexión; acá hay que hacerlo porque el SQL no califica el schema.
        jdbc.execute((java.sql.Connection conexion) -> {
            conexion.createStatement().execute("SET LOCAL search_path TO vehiculos");
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/seed/R__seed_vehiculos.sql"));
            return null;
        });

        assertThat(contar("vehiculo")).isEqualTo(vehiculosAntes);
        assertThat(contar("precio_historial")).isEqualTo(historialAntes);
        assertThat(jdbc.queryForObject("SELECT SUM(precio) FROM vehiculos.vehiculo", BigDecimal.class))
                .isEqualByComparingTo(sumaAntes);
    }

    @Test
    void lasMarcasNoSeRepitenYEstanEnElListado() {
        String marcas = repositorio.buscar(null).stream().map(Vehiculo::marca).sorted().collect(Collectors.joining(","));

        assertThat(marcas).isEqualTo("Chevrolet,Citroën,Fiat,Ford,Honda,Nissan,Peugeot,Renault,Toyota,Volkswagen");
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM vehiculos." + tabla, Long.class);
    }
}
