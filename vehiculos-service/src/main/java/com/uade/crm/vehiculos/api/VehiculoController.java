package com.uade.crm.vehiculos.api;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uade.crm.vehiculos.api.dto.PrecioHistoricoResponse;
import com.uade.crm.vehiculos.api.dto.PrecioResponse;
import com.uade.crm.vehiculos.api.dto.VehiculoResponse;
import com.uade.crm.vehiculos.application.ConsultaVehiculoService;

/** Lecturas del inventario. Son abiertas: no piden {@code X-Api-Key}. */
@RestController
@RequestMapping("/vehiculos")
@Tag(name = "Vehículos", description = "Inventario de unidades, precios e historial de precios")
public class VehiculoController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ConsultaVehiculoService consulta;

    public VehiculoController(ConsultaVehiculoService consulta) {
        this.consulta = consulta;
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
}
