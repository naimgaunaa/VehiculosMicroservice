package com.uade.crm.vehiculos.api.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.uade.crm.vehiculos.api.seguridad.ClaveServicioInvalidaException;
import com.uade.crm.vehiculos.application.DatosInvalidosException;
import com.uade.crm.vehiculos.config.CorrelationIdFilter;
import com.uade.crm.vehiculos.domain.model.AlmacenamientoNoDisponibleException;
import com.uade.crm.vehiculos.domain.model.FotoInvalidaException;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;

/**
 * Traduce toda excepción a un {@link ProblemDetail} con {@code codigo} estable y {@code correlationId}.
 * Los códigos de dominio ({@code VEHICULO_NO_ENCONTRADO}, {@code FOTO_INVALIDA}, etc.) se agregan con
 * un {@code @ExceptionHandler} que delegue en {@link #responder(CodigoError, String, WebRequest)}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String DETALLE_VALIDACION = "Hay campos que no cumplen las validaciones";
    private static final String DETALLE_INTERNO = "Ocurrió un error inesperado";
    private static final String DETALLE_FOTO_GRANDE = "La foto supera el tamaño máximo de 2 MB";

    /** Un campo que no pasó la validación, tal como viaja en {@code errores}. */
    public record CampoInvalido(String campo, String mensaje) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<CampoInvalido> errores = new ArrayList<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String campo = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errores.add(new CampoInvalido(campo, error.getDefaultMessage()));
        }
        return validacion(errores, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<CampoInvalido> errores = new ArrayList<>();
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors parametro) {
                parametro.getFieldErrors().forEach(e -> errores.add(new CampoInvalido(e.getField(), e.getDefaultMessage())));
                parametro.getGlobalErrors().forEach(e -> errores.add(new CampoInvalido(e.getObjectName(), e.getDefaultMessage())));
            } else {
                String campo = resultado.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : resultado.getResolvableErrors()) {
                    errores.add(new CampoInvalido(campo, error.getDefaultMessage()));
                }
            }
        }
        return validacion(errores, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<CampoInvalido> errores = new ArrayList<>();
        for (ConstraintViolation<?> violacion : ex.getConstraintViolations()) {
            errores.add(new CampoInvalido(ultimoNodo(violacion.getPropertyPath()), violacion.getMessage()));
        }
        return validacion(errores, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String campo = mismatch.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            return validacion(List.of(new CampoInvalido(campo, mensajeDeConversion(mismatch))), request);
        }
        log.debug("Cuerpo ilegible: {}", ex.getMessage());
        return responder(CodigoError.JSON_INVALIDO, "El cuerpo de la solicitud no es un JSON válido", request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return responder(CodigoError.FOTO_INVALIDA, DETALLE_FOTO_GRANDE, request);
    }

    /** Resto de los 4xx que resuelve Spring MVC (método no permitido, ruta inexistente, tipo no soportado...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is4xxClientError()) {
            String detalle = body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : ex.getMessage();
            return responder(CodigoError.SOLICITUD_INVALIDA, statusCode, detalle, headers, request, null);
        }
        log.error("Error al procesar {}", ruta(request), ex);
        return responder(CodigoError.ERROR_INTERNO, statusCode, DETALLE_INTERNO, headers, request, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        String tipo = request instanceof ServletWebRequest web ? web.getRequest().getContentType() : null;
        if (tipo == null || !tipo.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.MULTIPART_FORM_DATA));
            return responder(CodigoError.SOLICITUD_INVALIDA, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "El cuerpo debe ser multipart/form-data", headers, request, null);
        }
        log.debug("Multipart ilegible: {}", ex.getMessage());
        return responder(CodigoError.SOLICITUD_INVALIDA, HttpStatus.BAD_REQUEST,
                "El cuerpo multipart está mal formado", null, request, null);
    }

    @ExceptionHandler(VehiculoNoEncontradoException.class)
    public ResponseEntity<Object> handleVehiculoNoEncontrado(VehiculoNoEncontradoException ex, WebRequest request) {
        return responder(CodigoError.VEHICULO_NO_ENCONTRADO, ex.getMessage(), request);
    }

    @ExceptionHandler(ClaveServicioInvalidaException.class)
    public ResponseEntity<Object> handleClaveServicioInvalida(ClaveServicioInvalidaException ex, WebRequest request) {
        return responder(CodigoError.CLAVE_SERVICIO_INVALIDA, ex.getMessage(), request);
    }

    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<Object> handleDatosInvalidos(DatosInvalidosException ex, WebRequest request) {
        return responder(CodigoError.VALIDACION, ex.getMessage(), request);
    }

    @ExceptionHandler(FotoInvalidaException.class)
    public ResponseEntity<Object> handleFotoInvalida(FotoInvalidaException ex, WebRequest request) {
        return responder(CodigoError.FOTO_INVALIDA, ex.getMessage(), request);
    }

    @ExceptionHandler(AlmacenamientoNoDisponibleException.class)
    public ResponseEntity<Object> handleAlmacenamientoNoDisponible(AlmacenamientoNoDisponibleException ex,
            WebRequest request) {
        return responder(CodigoError.ALMACENAMIENTO_NO_DISPONIBLE, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleError(Exception ex, WebRequest request) {
        log.error("Error no controlado al procesar {}", ruta(request), ex);
        return responder(CodigoError.ERROR_INTERNO, DETALLE_INTERNO, request);
    }

    /** Respuesta con el status propio del código, para los handlers de dominio. */
    protected ResponseEntity<Object> responder(CodigoError codigo, String detalle, WebRequest request) {
        return responder(codigo, codigo.status(), detalle, null, request, null);
    }

    private ResponseEntity<Object> validacion(List<CampoInvalido> errores, WebRequest request) {
        return responder(CodigoError.VALIDACION, CodigoError.VALIDACION.status(), DETALLE_VALIDACION, null, request, errores);
    }

    private ResponseEntity<Object> responder(CodigoError codigo, HttpStatusCode status, String detalle,
            HttpHeaders headers, WebRequest request, List<CampoInvalido> errores) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setType(codigo.type());
        problema.setTitle(codigo.titulo());
        String ruta = ruta(request);
        if (ruta != null) {
            problema.setInstance(URI.create(ruta));
        }
        problema.setProperty("codigo", codigo.name());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CLAVE);
        if (correlationId != null) {
            problema.setProperty("correlationId", correlationId);
        }
        if (errores != null) {
            problema.setProperty("errores", errores);
        }
        return ResponseEntity.status(status)
                .headers(headers == null ? new HttpHeaders() : headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }

    private static String mensajeDeConversion(MismatchedInputException ex) {
        if (ex instanceof UnrecognizedPropertyException) {
            return "campo no admitido";
        }
        if (ex instanceof InvalidFormatException formato && formato.getTargetType() != null
                && formato.getTargetType().isEnum()) {
            String aceptados = Arrays.stream(formato.getTargetType().getEnumConstants())
                    .map(valor -> ((Enum<?>) valor).name())
                    .collect(Collectors.joining(", "));
            return "valor inválido, los valores aceptados son: " + aceptados;
        }
        return "el valor no tiene el tipo esperado";
    }

    private static String ultimoNodo(Path ruta) {
        String ultimo = null;
        for (Path.Node nodo : ruta) {
            ultimo = nodo.getName();
        }
        return ultimo;
    }

    private static String ruta(WebRequest request) {
        return request instanceof ServletWebRequest web ? web.getRequest().getRequestURI() : null;
    }
}
