package com.uade.crm.vehiculos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PruebaIntegracionSeed;
import com.uade.crm.vehiculos.domain.model.Caja;
import com.uade.crm.vehiculos.domain.model.PrecioHistorico;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

@PruebaIntegracionSeed
@Transactional
class SeedTest {

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VehiculoRepositorio repositorio;

    @Test
    void flywayAplicoLaV1YElSeedRepetible() {
        List<Map<String, Object>> aplicadas = jdbc.queryForList(
                "SELECT version, script FROM " + schema + ".flyway_schema_history WHERE success AND type = 'SQL' ORDER BY installed_rank");

        assertThat(aplicadas).extracting(fila -> fila.get("version")).containsExactly("1", "2", null);
        assertThat(aplicadas.get(2).get("script")).isEqualTo("R__seed_vehiculos.sql");
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
    void cadaUnidadTieneUnaPatenteValidaYDistinta() {
        List<Vehiculo> todos = repositorio.buscar(null);

        assertThat(todos).allMatch(v -> v.patente().isPresent());
        assertThat(todos).extracting(v -> v.patente().orElseThrow()).doesNotHaveDuplicates()
                .allMatch(p -> p.equals(com.uade.crm.vehiculos.domain.model.Patente.normalizar(p)));
    }

    @Test
    void laUnidadDePruebaDeMultasTieneLaPatenteAAA111YEstaDisponible() {
        assertThat(repositorio.buscar(true)).anyMatch(v -> v.patente().filter("AAA111"::equals).isPresent());
    }

    @Test
    void hayPatentesDeLosDosFormatos() {
        List<String> patentes = repositorio.buscar(null).stream().map(v -> v.patente().orElseThrow()).toList();

        assertThat(patentes).anyMatch(p -> p.matches("[A-Z]{3}[0-9]{3}")).anyMatch(p -> p.matches("[A-Z]{2}[0-9]{3}[A-Z]{2}"));
    }

    @Test
    void lasPatentesNoPisanUnaCargadaAMano() throws Exception {
        jdbc.update("UPDATE " + schema + ".vehiculo SET patente = 'QQ111QQ' WHERE id = 'a0000000-0000-4000-8000-000000000002'::uuid");

        jdbc.execute((java.sql.Connection conexion) -> {
            conexion.createStatement().execute("SET LOCAL search_path TO " + schema);
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/seed/R__seed_vehiculos.sql"));
            return null;
        });

        assertThat(jdbc.queryForObject("SELECT patente FROM " + schema + ".vehiculo WHERE id = 'a0000000-0000-4000-8000-000000000002'::uuid",
                String.class)).isEqualTo("QQ111QQ");
    }

    @Test
    void elScriptEsIdempotente() throws Exception {
        long vehiculosAntes = contar("vehiculo");
        long historialAntes = contar("precio_historial");
        BigDecimal sumaAntes = jdbc.queryForObject("SELECT SUM(precio) FROM " + schema + ".vehiculo", BigDecimal.class);

        // Flyway no vuelve a correr una repetible sin cambios, así que se ejecuta el script a mano.
        // Flyway fija el search_path en su conexión; acá hay que hacerlo porque el SQL no califica el schema.
        jdbc.execute((java.sql.Connection conexion) -> {
            conexion.createStatement().execute("SET LOCAL search_path TO " + schema);
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/seed/R__seed_vehiculos.sql"));
            return null;
        });

        assertThat(contar("vehiculo")).isEqualTo(vehiculosAntes);
        assertThat(contar("precio_historial")).isEqualTo(historialAntes);
        assertThat(jdbc.queryForObject("SELECT SUM(precio) FROM " + schema + ".vehiculo", BigDecimal.class))
                .isEqualByComparingTo(sumaAntes);
    }

    @Test
    void lasMarcasNoSeRepitenYEstanEnElListado() {
        String marcas = repositorio.buscar(null).stream().map(Vehiculo::marca).sorted().collect(Collectors.joining(","));

        assertThat(marcas).isEqualTo("Chevrolet,Citroën,Fiat,Ford,Honda,Nissan,Peugeot,Renault,Toyota,Volkswagen");
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + "." + tabla, Long.class);
    }
}
