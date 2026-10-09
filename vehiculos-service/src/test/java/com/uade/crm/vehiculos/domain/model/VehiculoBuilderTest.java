package com.uade.crm.vehiculos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VehiculoBuilderTest {

    /** Año actual 2026, por lo que el máximo aceptado es 2027. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);

    private static Vehiculo.Builder valido() {
        return Vehiculo.builder(RELOJ)
                .marca("Toyota")
                .modelo("Corolla")
                .anio(2021)
                .kilometraje(45000)
                .precio(new BigDecimal("10000000"))
                .caja(Caja.AUTOMATICA);
    }

    @Test
    void construyeConDatosValidosYExponeLosAccesores() {
        UUID id = UUID.randomUUID();

        Vehiculo vehiculo = valido().id(id).version("XEI CVT").fotoUrl("https://ejemplo.test/foto.jpg").build();

        assertThat(vehiculo.id()).isEqualTo(id);
        assertThat(vehiculo.marca()).isEqualTo("Toyota");
        assertThat(vehiculo.modelo()).isEqualTo("Corolla");
        assertThat(vehiculo.version()).contains("XEI CVT");
        assertThat(vehiculo.anio()).isEqualTo(2021);
        assertThat(vehiculo.kilometraje()).isEqualTo(45000);
        assertThat(vehiculo.precio()).isEqualByComparingTo("10000000");
        assertThat(vehiculo.caja()).isEqualTo(Caja.AUTOMATICA);
        assertThat(vehiculo.fotoUrl()).contains("https://ejemplo.test/foto.jpg");
    }

    @Test
    void aplicaLosValoresPorDefecto() {
        Vehiculo vehiculo = valido().build();

        assertThat(vehiculo.disponible()).isTrue();
        assertThat(vehiculo.id()).isNotNull();
        assertThat(vehiculo.version()).isEmpty();
        assertThat(vehiculo.fotoUrl()).isEmpty();
    }

    @Test
    void respetaDisponibleFalse() {
        assertThat(valido().disponible(false).build().disponible()).isFalse();
    }

    @Test
    void generaUnIdDistintoPorCadaConstruccion() {
        assertThat(valido().build().id()).isNotEqualTo(valido().build().id());
    }

    @Test
    void recortaMarcaModeloYVersion() {
        Vehiculo vehiculo = valido().marca("  Toyota ").modelo(" Corolla  ").version("  XEI  ").build();

        assertThat(vehiculo.marca()).isEqualTo("Toyota");
        assertThat(vehiculo.modelo()).isEqualTo("Corolla");
        assertThat(vehiculo.version()).contains("XEI");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void unaVersionEnBlancoEquivaleANoInformarla(String version) {
        assertThat(valido().version(version).build().version()).isEmpty();
    }

    @Test
    void precioNuloSeRechaza() {
        assertThatThrownBy(() -> valido().precio(null).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El precio debe ser mayor que cero");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1"})
    void precioNoPositivoSeRechaza(String precio) {
        assertThatThrownBy(() -> valido().precio(new BigDecimal(precio)).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El precio debe ser mayor que cero");
    }

    @Test
    void precioPositivoMinimoSeAcepta() {
        assertThat(valido().precio(new BigDecimal("0.01")).build().precio()).isEqualByComparingTo("0.01");
    }

    /** {@code isEqualTo} y no {@code isEqualByComparingTo}: se comprueba la escala, no solo el valor. */
    @ParameterizedTest
    @ValueSource(strings = {"10000000", "10000000.0", "10000000.00", "10000000.000", "1E+7"})
    void precioSeNormalizaADosDecimales(String precio) {
        assertThat(valido().precio(new BigDecimal(precio)).build().precio())
                .isEqualTo(new BigDecimal("10000000.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.555", "0.001", "10000000.001"})
    void precioConMasDeDosDecimalesSeRechaza(String precio) {
        assertThatThrownBy(() -> valido().precio(new BigDecimal(precio)).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El precio admite como máximo 2 decimales");
    }

    @Test
    void precioMaximoDeLaColumnaSeAcepta() {
        assertThat(valido().precio(new BigDecimal("999999999999.99")).build().precio())
                .isEqualTo(new BigDecimal("999999999999.99"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000000000", "1000000000000.00", "1E+13"})
    void precioConMasDeDoceDigitosEnterosSeRechaza(String precio) {
        assertThatThrownBy(() -> valido().precio(new BigDecimal(precio)).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El precio admite como máximo 12 dígitos enteros");
    }

    @Test
    void kilometrajeNegativoSeRechaza() {
        assertThatThrownBy(() -> valido().kilometraje(-1).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El kilometraje debe ser mayor o igual a 0");
    }

    @Test
    void kilometrajeSinInformarSeRechaza() {
        Vehiculo.Builder builder = Vehiculo.builder(RELOJ)
                .marca("Toyota").modelo("Corolla").anio(2021)
                .precio(new BigDecimal("1")).caja(Caja.MANUAL);

        assertThatThrownBy(builder::build).isInstanceOf(VehiculoInvalidoException.class);
    }

    @Test
    void kilometrajeCeroSeAcepta() {
        assertThat(valido().kilometraje(0).build().kilometraje()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void marcaVaciaSeRechaza(String marca) {
        assertThatThrownBy(() -> valido().marca(marca).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("La marca no puede estar vacía");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void modeloVacioSeRechaza(String modelo) {
        assertThatThrownBy(() -> valido().modelo(modelo).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El modelo no puede estar vacío");
    }

    @Test
    void marcaYModeloDeUnCaracterSeAceptan() {
        assertThatCode(() -> valido().marca("A").modelo("B").build()).doesNotThrowAnyException();
    }

    @Test
    void cajaAusenteSeRechaza() {
        assertThatThrownBy(() -> valido().caja(null).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("La caja es obligatoria");
    }

    @ParameterizedTest
    @ValueSource(ints = {1949, 2028})
    void anioFueraDeRangoSeRechaza(int anio) {
        assertThatThrownBy(() -> valido().anio(anio).build())
                .isInstanceOf(VehiculoInvalidoException.class)
                .hasMessage("El año debe estar entre 1950 y 2027");
    }

    @ParameterizedTest
    @ValueSource(ints = {1950, 2026, 2027})
    void anioEnLosLimitesSeAcepta(int anio) {
        assertThat(valido().anio(anio).build().anio()).isEqualTo(anio);
    }

    @Test
    void anioSinInformarSeRechaza() {
        Vehiculo.Builder builder = Vehiculo.builder(RELOJ)
                .marca("Toyota").modelo("Corolla").kilometraje(0)
                .precio(new BigDecimal("1")).caja(Caja.MANUAL);

        assertThatThrownBy(builder::build).isInstanceOf(VehiculoInvalidoException.class);
    }

    @Test
    void elLimiteSuperiorDelAnioSaleDelClockInyectado() {
        Clock reloj2030 = Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);
        Vehiculo.Builder base = Vehiculo.builder(reloj2030)
                .marca("Toyota").modelo("Corolla").kilometraje(0)
                .precio(new BigDecimal("1")).caja(Caja.MANUAL);

        assertThat(base.anio(2031).build().anio()).isEqualTo(2031);
        assertThatThrownBy(() -> base.anio(2032).build()).isInstanceOf(VehiculoInvalidoException.class);
    }

    @Test
    void elLimiteSuperiorCambiaAlCambiarDeAnio() {
        Clock finDeAnio = Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC);
        Clock inicioDeAnio = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);

        assertThatThrownBy(() -> constructorConAnio(finDeAnio, 2028).build())
                .isInstanceOf(VehiculoInvalidoException.class);
        assertThat(constructorConAnio(inicioDeAnio, 2028).build().anio()).isEqualTo(2028);
    }

    @Test
    void builderSinClockSeRechaza() {
        assertThatThrownBy(() -> Vehiculo.builder(null)).isInstanceOf(NullPointerException.class);
    }

    private static Vehiculo.Builder constructorConAnio(Clock reloj, int anio) {
        return Vehiculo.builder(reloj)
                .marca("Toyota").modelo("Corolla").anio(anio).kilometraje(0)
                .precio(new BigDecimal("1")).caja(Caja.MANUAL);
    }
}
