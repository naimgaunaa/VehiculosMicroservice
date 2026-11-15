package com.uade.crm.vehiculos.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uade.crm.vehiculos.domain.model.Multa;
import com.uade.crm.vehiculos.domain.model.Patente;
import com.uade.crm.vehiculos.domain.model.PatenteNoCoincideException;
import com.uade.crm.vehiculos.domain.model.Vehiculo;
import com.uade.crm.vehiculos.domain.model.VehiculoInvalidoException;
import com.uade.crm.vehiculos.domain.model.VehiculoNoEncontradoException;
import com.uade.crm.vehiculos.domain.port.VehiculoRepositorio;

/**
 * Multas de una unidad: las recibe del orquestador (que las pidió al servicio {@code multas}) y las expone.
 * Qué significa tener multas lo decide vehiculos: por ahora solo las guarda, con su subtotal y la fecha de la consulta.
 */
@Service
public class MultasVehiculoService {

    /** La unidad con las actas de su última consulta (vacías si nunca se consultó o no tiene multas). */
    public record MultasDeVehiculo(Vehiculo vehiculo, List<Multa> multas) {
    }

    private final VehiculoRepositorio repositorio;
    private final Clock clock;

    public MultasVehiculoService(VehiculoRepositorio repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    /**
     * Guarda el resultado de una consulta y reemplaza el anterior. Es idempotente: el mismo resultado dos veces
     * deja el mismo estado (salvo la fecha de la consulta).
     *
     * @param dominio patente a la que corresponde el resultado; si viene, tiene que ser la de la unidad
     */
    @Transactional
    public void registrar(UUID id, String dominio, BigDecimal subtotal, List<Multa> multas) {
        Vehiculo vehiculo = repositorio.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new VehiculoNoEncontradoException(id));
        String patente = vehiculo.patente().orElseThrow(() -> new PatenteNoCoincideException(
                "La unidad no tiene patente cargada: no se pueden registrar sus multas"));
        try {
            if (dominio != null && !dominio.isBlank() && !patente.equals(Patente.normalizar(dominio))) {
                throw new PatenteNoCoincideException("Las multas son de otra patente: no coincide con la de la unidad");
            }
            Set<String> actas = new HashSet<>();
            for (Multa multa : multas) {
                if (!actas.add(multa.numeroActa())) {
                    throw new VehiculoInvalidoException("El número de acta " + multa.numeroActa() + " está repetido");
                }
            }
            vehiculo.registrarMultas(clock.instant(), subtotal);
        } catch (VehiculoInvalidoException e) {
            throw new DatosInvalidosException(e);
        }
        repositorio.guardar(vehiculo);
        repositorio.reemplazarMultas(id, multas);
    }

    @Transactional(readOnly = true)
    public MultasDeVehiculo obtener(UUID id) {
        Vehiculo vehiculo = repositorio.buscarPorId(id).orElseThrow(() -> new VehiculoNoEncontradoException(id));
        return new MultasDeVehiculo(vehiculo, repositorio.multasDe(id));
    }
}
