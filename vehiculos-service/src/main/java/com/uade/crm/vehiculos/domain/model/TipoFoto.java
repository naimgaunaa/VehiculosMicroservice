package com.uade.crm.vehiculos.domain.model;

import java.util.Optional;

public enum TipoFoto {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] FIRMA_RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] FIRMA_WEBP = {'W', 'E', 'B', 'P'};
    private static final int POSICION_WEBP = 8;

    private final String contentType;
    private final String extension;

    TipoFoto(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    static Optional<TipoFoto> detectar(byte[] contenido) {
        if (empiezaCon(contenido, FIRMA_JPEG, 0)) {
            return Optional.of(JPEG);
        }
        if (empiezaCon(contenido, FIRMA_PNG, 0)) {
            return Optional.of(PNG);
        }
        if (empiezaCon(contenido, FIRMA_RIFF, 0) && empiezaCon(contenido, FIRMA_WEBP, POSICION_WEBP)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma, int desde) {
        if (contenido.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (contenido[desde + i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
