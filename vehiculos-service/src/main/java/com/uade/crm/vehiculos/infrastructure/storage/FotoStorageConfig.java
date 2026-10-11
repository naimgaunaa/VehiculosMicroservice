package com.uade.crm.vehiculos.infrastructure.storage;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.uade.crm.vehiculos.config.VehiculosProperties;
import com.uade.crm.vehiculos.domain.port.FotoStorage;

@Configuration
class FotoStorageConfig {

    private static final Duration TIEMPO_CONEXION = Duration.ofSeconds(5);
    private static final Duration TIEMPO_LECTURA = Duration.ofSeconds(15);

    @Bean
    FotoStorage fotoStorage(VehiculosProperties propiedades, RestClient.Builder builder) {
        return switch (propiedades.modoFotos()) {
            case SUPABASE -> new SupabaseFotoStorage(builder.requestFactory(conTimeouts()), propiedades);
            case DESHABILITADO -> new FotoStorageDeshabilitado();
        };
    }

    private static JdkClientHttpRequestFactory conTimeouts() {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(TIEMPO_CONEXION).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(TIEMPO_LECTURA);
        return fabrica;
    }
}
