package com.uade.crm.vehiculos.api.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.uade.crm.vehiculos.config.VehiculosProperties;

public class ClaveServicioInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Api-Key";

    private final byte[] claveEscritura;

    public ClaveServicioInterceptor(VehiculosProperties propiedades) {
        this.claveEscritura = propiedades.claveEscritura().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod metodo && metodo.hasMethodAnnotation(RequiereClaveServicio.class)
                && !claveValida(request.getHeader(HEADER))) {
            throw new ClaveServicioInvalidaException();
        }
        return true;
    }

    private boolean claveValida(String recibida) {
        return recibida != null && MessageDigest.isEqual(recibida.getBytes(StandardCharsets.UTF_8), claveEscritura);
    }
}
