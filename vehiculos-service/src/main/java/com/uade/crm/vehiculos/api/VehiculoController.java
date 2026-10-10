package com.uade.crm.vehiculos.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.uade.crm.vehiculos.api.dto.ActualizacionVehiculoRequest;
import com.uade.crm.vehiculos.api.dto.AltaVehiculoRequest;
import com.uade.crm.vehiculos.api.dto.PrecioHistoricoResponse;
import com.uade.crm.vehiculos.api.dto.PrecioResponse;
import com.uade.crm.vehiculos.api.dto.VehiculoResponse;
import com.uade.crm.vehiculos.api.seguridad.RequiereClaveServicio;
import com.uade.crm.vehiculos.application.ActualizacionVehiculoService;
import com.uade.crm.vehiculos.application.AltaVehiculo;
import com.uade.crm.vehiculos.application.AltaVehiculoService;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;
import com.uade.crm.vehiculos.config.OpenApiConfig;
import com.uade.crm.vehiculos.domain.model.Vehiculo;

@RestController
@RequestMapping("/vehiculos")
@Tag(name = "Vehículos", description = "Inventario de unidades, precios e historial de precios")
public class VehiculoController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ConsultaVehiculoService consulta;
    private final AltaVehiculoService alta;
    private final ActualizacionVehiculoService actualizacion;

    public VehiculoController(ConsultaVehiculoService consulta, AltaVehiculoService alta,
            ActualizacionVehiculoService actualizacion) {
        this.consulta = consulta;
        this.alta = alta;
        this.actualizacion = actualizacion;
    }

    @GetMapping
    @Operation(summary = "Lista las unidades", description = "De la más nueva a la más vieja. Sin el parámetro devuelve todas.")
    @ApiResponse(responseCode = "200", description = "Unidades")
    @ApiResponse(responseCode = "400", description = "`disponible` no es booleano (SOLICITUD_INVALIDA)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Error interno (ERROR_INTERNO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public List<VehiculoResponse> listar(
            @Parameter(description = "Filtra por disponibilidad") @RequestParam(required = false) Boolean disponible) {
        return consulta.listar(disponible).stream().map(VehiculoResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una unidad", description = "Responde 200 aunque la unidad no esté disponible.")
    @ApiResponse(responseCode = "200", description = "La unidad")
    @ApiResponse(responseCode = "400", description = "El id no es un UUID (SOLICITUD_INVALIDA)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No existe la unidad (VEHICULO_NO_ENCONTRADO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Error interno (ERROR_INTERNO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public VehiculoResponse obtener(@PathVariable UUID id) {
        return VehiculoResponse.de(consulta.obtener(id));
    }

    @GetMapping("/{id}/precio")
    @Operation(summary = "Precio actual de una unidad",
            description = "Incluye `disponible`: responde 200 aunque la unidad ya no esté a la venta.")
    @ApiResponse(responseCode = "200", description = "Precio y disponibilidad")
    @ApiResponse(responseCode = "400", description = "El id no es un UUID (SOLICITUD_INVALIDA)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No existe la unidad (VEHICULO_NO_ENCONTRADO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Error interno (ERROR_INTERNO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public PrecioResponse precio(@PathVariable UUID id) {
        return PrecioResponse.de(consulta.obtener(id));
    }

    @GetMapping("/{id}/historial-precios")
    @Operation(summary = "Historial de precios de una unidad", description = "Del más nuevo al más viejo.")
    @ApiResponse(responseCode = "200", description = "Precios que rigieron")
    @ApiResponse(responseCode = "400", description = "El id no es un UUID (SOLICITUD_INVALIDA)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No existe la unidad (VEHICULO_NO_ENCONTRADO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Error interno (ERROR_INTERNO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public List<PrecioHistoricoResponse> historialDePrecios(@PathVariable UUID id) {
        return consulta.historialDePrecios(id).stream().map(PrecioHistoricoResponse::de).toList();
    }

    @PostMapping
    @RequiereClaveServicio
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_CLAVE_SERVICIO)
    @Operation(summary = "Da de alta una unidad", description = "Registra también el precio inicial en el historial.")
    @ApiResponse(responseCode = "201", description = "La unidad creada, con su URL en `Location`")
    @ApiResponse(responseCode = "400", description = "JSON mal formado (JSON_INVALIDO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida (CLAVE_SERVICIO_INVALIDA)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Datos inválidos (VALIDACION)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Error interno (ERROR_INTERNO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<VehiculoResponse> registrar(@Valid @RequestBody AltaVehiculoRequest pedido) {
        Vehiculo vehiculo = alta.registrar(new AltaVehiculo(pedido.marca(), pedido.modelo(), pedido.version(),
                pedido.anio(), pedido.kilometraje(), pedido.precio(), pedido.disponible(), pedido.caja()));
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(vehiculo.id())
                .toUri();
        return ResponseEntity.created(ubicacion).body(VehiculoResponse.de(vehiculo));
    }

    @PutMapping("/{id}")
    @RequiereClaveServicio
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_CLAVE_SERVICIO)
    @Operation(summary = "Cambia el precio y/o la disponibilidad",
            description = "Al menos uno de los dos. Si cambia el precio, lo registra en el historial.")
    @ApiResponse(responseCode = "200", description = "La unidad actualizada")
    @ApiResponse(responseCode = "400", description = "JSON mal formado o id que no es UUID (JSON_INVALIDO, SOLICITUD_INVALIDA)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida (CLAVE_SERVICIO_INVALIDA)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No existe la unidad (VEHICULO_NO_ENCONTRADO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Datos inválidos o campos no admitidos (VALIDACION)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Error interno (ERROR_INTERNO)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public VehiculoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizacionVehiculoRequest pedido) {
        return VehiculoResponse.de(actualizacion.actualizar(id, pedido.precio(), pedido.disponible()));
    }
}
