package com.uade.crm.vehiculos.domain.model;

public final class ArchivoFoto {

    public static final int TAMANIO_MAXIMO = 2 * 1024 * 1024;

    private final byte[] bytes;
    private final TipoFoto tipo;

    private ArchivoFoto(byte[] bytes, TipoFoto tipo) {
        this.bytes = bytes;
        this.tipo = tipo;
    }

    public static ArchivoFoto de(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new FotoInvalidaException("La foto está vacía");
        }
        if (contenido.length > TAMANIO_MAXIMO) {
            throw new FotoInvalidaException("La foto supera el tamaño máximo de 2 MB");
        }
        TipoFoto tipo = TipoFoto.detectar(contenido)
                .orElseThrow(() -> new FotoInvalidaException("La foto debe ser JPEG, PNG o WebP"));
        return new ArchivoFoto(contenido.clone(), tipo);
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public TipoFoto tipo() {
        return tipo;
    }

    public int tamanio() {
        return bytes.length;
    }

    public String extension() {
        return tipo.extension();
    }
}
