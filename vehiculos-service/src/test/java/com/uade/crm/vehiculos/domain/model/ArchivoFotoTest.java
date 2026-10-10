package com.uade.crm.vehiculos.domain.model;

import static com.uade.crm.vehiculos.FotosDePrueba.DOS_MB;
import static com.uade.crm.vehiculos.FotosDePrueba.conFirma;
import static com.uade.crm.vehiculos.FotosDePrueba.jpeg;
import static com.uade.crm.vehiculos.FotosDePrueba.pdf;
import static com.uade.crm.vehiculos.FotosDePrueba.png;
import static com.uade.crm.vehiculos.FotosDePrueba.webp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ArchivoFotoTest {

    @Test
    void detectaJpeg() {
        ArchivoFoto archivo = ArchivoFoto.de(jpeg(100));

        assertThat(archivo.tipo()).isEqualTo(TipoFoto.JPEG);
        assertThat(archivo.extension()).isEqualTo("jpg");
        assertThat(archivo.tipo().contentType()).isEqualTo("image/jpeg");
        assertThat(archivo.tamanio()).isEqualTo(100);
    }

    @Test
    void detectaPng() {
        ArchivoFoto archivo = ArchivoFoto.de(png(100));

        assertThat(archivo.tipo()).isEqualTo(TipoFoto.PNG);
        assertThat(archivo.extension()).isEqualTo("png");
        assertThat(archivo.tipo().contentType()).isEqualTo("image/png");
    }

    @Test
    void detectaWebp() {
        ArchivoFoto archivo = ArchivoFoto.de(webp(100));

        assertThat(archivo.tipo()).isEqualTo(TipoFoto.WEBP);
        assertThat(archivo.extension()).isEqualTo("webp");
        assertThat(archivo.tipo().contentType()).isEqualTo("image/webp");
    }

    @Test
    void conservaLosBytes() {
        byte[] contenido = png(64);

        assertThat(ArchivoFoto.de(contenido).bytes()).isEqualTo(contenido);
    }

    @Test
    void rechazaUnPdf() {
        assertThatThrownBy(() -> ArchivoFoto.de(pdf(100)))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessageContaining("JPEG, PNG o WebP");
    }

    @Test
    void rechazaTexto() {
        byte[] texto = "esto no es una imagen".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ArchivoFoto.de(texto)).isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void rechazaRiffQueNoEsWebp() {
        byte[] wav = conFirma(100, 'R', 'I', 'F', 'F', 0x10, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E');

        assertThatThrownBy(() -> ArchivoFoto.de(wav)).isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void rechazaUnArchivoMasCortoQueLaFirma() {
        assertThatThrownBy(() -> ArchivoFoto.de(new byte[] {(byte) 0x89, 0x50, 0x4E}))
                .isInstanceOf(FotoInvalidaException.class);
        assertThatThrownBy(() -> ArchivoFoto.de(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B'}))
                .isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void rechazaUnArchivoVacio() {
        assertThatThrownBy(() -> ArchivoFoto.de(new byte[0]))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessageContaining("vacía");
        assertThatThrownBy(() -> ArchivoFoto.de(null))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessageContaining("vacía");
    }

    @Test
    void aceptaJustoDosMb() {
        assertThat(ArchivoFoto.de(png(DOS_MB)).tamanio()).isEqualTo(DOS_MB);
    }

    @Test
    void rechazaMasDeDosMb() {
        assertThatThrownBy(() -> ArchivoFoto.de(png(DOS_MB + 1)))
                .isInstanceOf(FotoInvalidaException.class)
                .hasMessageContaining("2 MB");
    }
}
