package com.uade.crm.vehiculos;

import java.util.Arrays;

public final class FotosDePrueba {

    public static final int DOS_MB = 2 * 1024 * 1024;

    private FotosDePrueba() {
    }

    public static byte[] jpeg(int tamanio) {
        return conFirma(tamanio, 0xFF, 0xD8, 0xFF, 0xE0);
    }

    public static byte[] png(int tamanio) {
        return conFirma(tamanio, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    public static byte[] webp(int tamanio) {
        return conFirma(tamanio, 'R', 'I', 'F', 'F', 0x10, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P');
    }

    public static byte[] pdf(int tamanio) {
        return conFirma(tamanio, '%', 'P', 'D', 'F', '-', '1', '.', '4');
    }

    public static byte[] conFirma(int tamanio, int... firma) {
        byte[] bytes = new byte[tamanio];
        Arrays.fill(bytes, (byte) 0x2A);
        for (int i = 0; i < firma.length && i < tamanio; i++) {
            bytes[i] = (byte) firma[i];
        }
        return bytes;
    }
}
