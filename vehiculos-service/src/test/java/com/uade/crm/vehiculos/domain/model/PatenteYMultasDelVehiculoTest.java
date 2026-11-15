package com.uade.crm.vehiculos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.uade.crm.vehiculos.RelojDePrueba;

class PatenteYMultasDelVehiculoTest {

    private static final Instant CONSULTA = Instant.parse("2026-10-09T15:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"AAA111", "AB123CD", "aaa111", " ab 123-cd ", "aaa-111"})
    void laPatenteSeNormalizaSinEspaciosNiGuionesYEnMayusculas(String entrada) {
        assertThat(Patente.normalizar(entrada)).matches("[A-Z0-9]+").hasSizeLessThanOrEqualTo(Patente.LARGO_MAXIMO);
    }

    @Test
    void normalizaAlFormatoCanonico() {
        assertThat(Patente.normalizar(" ab 123-cd ")).isEqualTo("AB123CD");
        assertThat(Patente.normalizar("aaa-111")).isEqualTo("AAA111");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "AAA11", "AAA1111", "1AB234C", "ABCD123", "AB123CDE", "AA 11", "ÑAA111"})
    void unaPatenteConOtroFormatoSeRechaza(String entrada) {
        assertThatThrownBy(() -> Patente.normalizar(entrada)).isInstanceOf(VehiculoInvalidoException.class);
    }

    @Test
    void unaPatenteNulaSeRechaza() {
        assertThatThrownBy(() -> Patente.normalizar(null)).isInstanceOf(VehiculoInvalidoException.class)
                .hasMessageContaining("vacía");
    }

    @Test
    void sinPatenteNiMultasPorDefecto() {
        Vehiculo vehiculo = unaUnidad().build();

        assertThat(vehiculo.patente()).isEmpty();
        assertThat(vehiculo.multasConsultadasEn()).isEmpty();
        assertThat(vehiculo.multasSubtotal()).isEmpty();
    }

    @Test
    void elBuilderNormalizaLaPatenteYUnTextoEnBlancoEquivaleANoInformarla() {
        assertThat(unaUnidad().patente(" ab123cd ").build().patente()).contains("AB123CD");
        assertThat(unaUnidad().patente("   ").build().patente()).isEmpty();
        assertThat(unaUnidad().patente(null).build().patente()).isEmpty();
    }

    @Test
    void elBuilderRechazaUnaPatenteInvalida() {
        assertThatThrownBy(() -> unaUnidad().patente("XYZ").build()).isInstanceOf(VehiculoInvalidoException.class);
    }

    @Test
    void asignarPatenteLaNormaliza() {
        Vehiculo vehiculo = unaUnidad().build();

        vehiculo.asignarPatente("ab 123 cd");

        assertThat(vehiculo.patente()).contains("AB123CD");
    }

    @Test
    void cambiarDePatenteDescartaLasMultasDeLaAnterior() {
        Vehiculo vehiculo = unaUnidad().patente("AAA111").build();
        vehiculo.registrarMultas(CONSULTA, new BigDecimal("39000"));

        vehiculo.asignarPatente("BBB222");

        assertThat(vehiculo.patente()).contains("BBB222");
        assertThat(vehiculo.multasConsultadasEn()).isEmpty();
        assertThat(vehiculo.multasSubtotal()).isEmpty();
    }

    @Test
    void asignarLaMismaPatenteConservaLasMultas() {
        Vehiculo vehiculo = unaUnidad().patente("AAA111").build();
        vehiculo.registrarMultas(CONSULTA, new BigDecimal("39000"));

        vehiculo.asignarPatente(" aaa-111 ");

        assertThat(vehiculo.multasConsultadasEn()).contains(CONSULTA);
        assertThat(vehiculo.multasSubtotal()).hasValueSatisfying(s -> assertThat(s).isEqualByComparingTo("39000"));
    }

    @Test
    void registrarMultasGuardaLaFechaYElSubtotalConDosDecimales() {
        Vehiculo vehiculo = unaUnidad().patente("AAA111").build();

        vehiculo.registrarMultas(CONSULTA, new BigDecimal("39000"));

        assertThat(vehiculo.multasConsultadasEn()).contains(CONSULTA);
        assertThat(vehiculo.multasSubtotal()).contains(new BigDecimal("39000.00"));
    }

    @Test
    void unaPatenteSinMultasTieneSubtotalCero() {
        Vehiculo vehiculo = unaUnidad().patente("AAA111").build();

        vehiculo.registrarMultas(CONSULTA, BigDecimal.ZERO);

        assertThat(vehiculo.multasSubtotal()).contains(new BigDecimal("0.00"));
    }

    @Test
    void elSubtotalNegativoOFaltanteSeRechaza() {
        Vehiculo vehiculo = unaUnidad().patente("AAA111").build();

        assertThatThrownBy(() -> vehiculo.registrarMultas(CONSULTA, new BigDecimal("-1")))
                .isInstanceOf(VehiculoInvalidoException.class);
        assertThatThrownBy(() -> vehiculo.registrarMultas(CONSULTA, null)).isInstanceOf(VehiculoInvalidoException.class);
        assertThat(vehiculo.multasConsultadasEn()).isEmpty();
    }

    @Test
    void laFechaDeLaConsultaEsObligatoria() {
        Vehiculo vehiculo = unaUnidad().patente("AAA111").build();

        assertThatThrownBy(() -> vehiculo.registrarMultas(null, BigDecimal.ONE)).isInstanceOf(NullPointerException.class);
    }

    private static Vehiculo.Builder unaUnidad() {
        return Vehiculo.builder(new RelojDePrueba()).marca("Toyota").modelo("Corolla").anio(2021).kilometraje(45000)
                .precio(new BigDecimal("10000000")).caja(Caja.MANUAL);
    }
}
