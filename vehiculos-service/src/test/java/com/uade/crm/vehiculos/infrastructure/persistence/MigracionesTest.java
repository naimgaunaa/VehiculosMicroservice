package com.uade.crm.vehiculos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.PruebaIntegracion;

import jakarta.persistence.Column;
import jakarta.persistence.Table;

/**
 * La V1 contra PostgreSQL real. Que el contexto arranque ya prueba que las entidades coinciden con el
 * esquema ({@code ddl-auto: validate}), pero esa validación solo mira nombres y tipos: no compara precisión,
 * escala, longitud ni nulabilidad. Acá se verifica el esquema, sus restricciones y esos atributos.
 */
@PruebaIntegracion
@Transactional
class MigracionesTest {

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAplicoSoloLaV1EnElSchemaVehiculos() {
        List<String> aplicadas = jdbc.queryForList(
                "SELECT version FROM " + schema + ".flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);

        assertThat(aplicadas).containsExactly("1");
    }

    @Test
    void creaLasTablasYLosIndicesEnElSchemaVehiculos() {
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?", String.class, schema);
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = ?", String.class, schema);

        assertThat(tablas).contains("vehiculo", "precio_historial");
        assertThat(indices).contains("ix_vehiculo_disponible", "ix_precio_historial_vehiculo");
    }

    /** Lo mismo que declara el SQL de la V1 (sección 9.2 del doc), columna por columna y en orden. */
    @Test
    void lasColumnasDeVehiculoCoincidenConLaV1() {
        assertThat(describirColumnas("vehiculo")).containsExactly(
                "id uuid NOT NULL",
                "marca character varying(60) NOT NULL",
                "modelo character varying(80) NOT NULL",
                "version character varying(80)",
                "anio integer NOT NULL",
                "kilometraje integer NOT NULL",
                "precio numeric(14,2) NOT NULL",
                "disponible boolean NOT NULL",
                "caja character varying(15) NOT NULL",
                "foto_url character varying(500)",
                "creado_en timestamp with time zone NOT NULL",
                "actualizado_en timestamp with time zone NOT NULL");
    }

    @Test
    void lasColumnasDePrecioHistorialCoincidenConLaV1() {
        assertThat(describirColumnas("precio_historial")).containsExactly(
                "id uuid NOT NULL",
                "vehiculo_id uuid NOT NULL",
                "precio numeric(14,2) NOT NULL",
                "vigente_desde timestamp with time zone NOT NULL");
    }

    /** Longitud, precisión, escala y nulabilidad de cada {@code @Column} contra la base, que validate no mira. */
    @Test
    void lasEntidadesDeclaranLoMismoQueLaBase() {
        for (Class<?> entidad : List.of(VehiculoEntity.class, PrecioHistorialEntity.class)) {
            String tabla = entidad.getAnnotation(Table.class).name();
            Map<String, Map<String, Object>> columnas = columnas(tabla).stream()
                    .collect(Collectors.toMap(c -> (String) c.get("column_name"), c -> c));
            List<Field> mapeados = Arrays.stream(entidad.getDeclaredFields())
                    .filter(campo -> campo.isAnnotationPresent(Column.class))
                    .toList();

            assertThat(mapeados).extracting(campo -> campo.getAnnotation(Column.class).name())
                    .as("columnas de %s sin mapear o de más", tabla)
                    .containsExactlyInAnyOrderElementsOf(columnas.keySet());

            for (Field campo : mapeados) {
                Column declarada = campo.getAnnotation(Column.class);
                Map<String, Object> real = columnas.get(declarada.name());
                String donde = entidad.getSimpleName() + "." + campo.getName();

                assertThat(declarada.nullable()).as("nullable de %s", donde)
                        .isEqualTo("YES".equals(real.get("is_nullable")));
                if (campo.getType() == String.class) {
                    assertThat(declarada.length()).as("length de %s", donde)
                            .isEqualTo(entero(real.get("character_maximum_length")));
                }
                if (campo.getType() == BigDecimal.class) {
                    assertThat(declarada.precision()).as("precision de %s", donde)
                            .isEqualTo(entero(real.get("numeric_precision")));
                    assertThat(declarada.scale()).as("scale de %s", donde)
                            .isEqualTo(entero(real.get("numeric_scale")));
                }
            }
        }
    }

    @Test
    void disponibleEsTruePorDefecto() {
        UUID id = insertarVehiculo("100000.00", 0, "AUTOMATICA");

        Boolean disponible = jdbc.queryForObject(
                "SELECT disponible FROM " + schema + ".vehiculo WHERE id = ?", Boolean.class, id);

        assertThat(disponible).isTrue();
    }

    @Test
    void rechazaPrecioNoPositivo() {
        assertThatThrownBy(() -> insertarVehiculo("0", 0, "MANUAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rechazaKilometrajeNegativo() {
        assertThatThrownBy(() -> insertarVehiculo("100000.00", -1, "MANUAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rechazaCajaFueraDelEnum() {
        assertThatThrownBy(() -> insertarVehiculo("100000.00", 0, "SEMIAUTOMATICA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elHistorialExigeUnVehiculoExistente() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO " + schema + ".precio_historial (id, vehiculo_id, precio, vigente_desde) "
                        + "VALUES (?, ?, 100000.00, now())", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elHistorialRechazaPrecioNoPositivo() {
        UUID id = insertarVehiculo("100000.00", 0, "MANUAL");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO " + schema + ".precio_historial (id, vehiculo_id, precio, vigente_desde) "
                        + "VALUES (?, ?, 0, now())", UUID.randomUUID(), id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<Map<String, Object>> columnas(String tabla) {
        return jdbc.queryForList("SELECT column_name, data_type, character_maximum_length, numeric_precision, "
                + "numeric_scale, is_nullable FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position", schema, tabla);
    }

    /** Cada columna como {@code nombre tipo[(largo|precisión,escala)] [NOT NULL]}. */
    private List<String> describirColumnas(String tabla) {
        return columnas(tabla).stream().map(c -> {
            String tipo = (String) c.get("data_type");
            if (c.get("character_maximum_length") != null) {
                tipo += "(" + entero(c.get("character_maximum_length")) + ")";
            } else if ("numeric".equals(tipo)) {
                tipo += "(" + entero(c.get("numeric_precision")) + "," + entero(c.get("numeric_scale")) + ")";
            }
            return c.get("column_name") + " " + tipo + ("NO".equals(c.get("is_nullable")) ? " NOT NULL" : "");
        }).toList();
    }

    private static int entero(Object valor) {
        return ((Number) valor).intValue();
    }

    private UUID insertarVehiculo(String precio, int kilometraje, String caja) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = new Timestamp(System.currentTimeMillis());
        jdbc.update("INSERT INTO " + schema + ".vehiculo "
                        + "(id, marca, modelo, anio, kilometraje, precio, caja, creado_en, actualizado_en) "
                        + "VALUES (?, 'Marca', 'Modelo', 2020, ?, ?::numeric, ?, ?, ?)",
                id, kilometraje, precio, caja, ahora, ahora);
        return id;
    }
}
