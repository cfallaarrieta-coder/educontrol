package com.example.demo.service;

import com.example.demo.dto.TarifaRequest;
import com.example.demo.dto.TarifaResponse;
import com.example.demo.entity.AnioEscolar;
import com.example.demo.entity.EstadoCuota;
import com.example.demo.entity.Nivel;
import com.example.demo.entity.Tarifa;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.AnioEscolarRepository;
import com.example.demo.repository.CuotaRepository;
import com.example.demo.repository.TarifaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TarifaService {

    private final TarifaRepository tarifaRepository;
    private final AnioEscolarRepository anioRepository;
    private final CuotaRepository cuotaRepository;

    public TarifaService(TarifaRepository tarifaRepository,
                         AnioEscolarRepository anioRepository,
                         CuotaRepository cuotaRepository) {
        this.tarifaRepository = tarifaRepository;
        this.anioRepository = anioRepository;
        this.cuotaRepository = cuotaRepository;
    }

    @Transactional(readOnly = true)
    public List<TarifaResponse> listar(Integer anioId) {
        return tarifaRepository.findByAnioEscolarIdOrderByNivelAsc(anioId)
                .stream().map(TarifaResponse::desde).toList();
    }

    @Transactional
    public TarifaResponse guardar(Integer anioId, Nivel nivel, TarifaRequest request) {
        AnioEscolar anio = anioRepository.findById(anioId)
                .orElseThrow(() -> new NegocioException("No existe el año escolar"));

        if (!anio.estaAbierto()) {
            throw new NegocioException("El año escolar está cerrado. No se pueden modificar las tarifas.");
        }

        Tarifa tarifa = tarifaRepository.findByAnioEscolarIdAndNivel(anioId, nivel)
                .orElse(new Tarifa(anio, nivel, request.montoMatricula(), request.montoMensualidad()));

        tarifa.setMontoMatricula(request.montoMatricula());
        tarifa.setMontoMensualidad(request.montoMensualidad());

        return TarifaResponse.desde(tarifaRepository.save(tarifa));
    }

    @Transactional
    public int aplicarACuotasPendientes(Integer anioId, Nivel nivel) {
        AnioEscolar anio = anioRepository.findById(anioId)
                .orElseThrow(() -> new NegocioException("No existe el año escolar"));

        if (!anio.estaAbierto()) {
            throw new NegocioException("El año escolar está cerrado.");
        }

        Tarifa tarifa = tarifaRepository.findByAnioEscolarIdAndNivel(anioId, nivel)
                .orElseThrow(() -> new NegocioException("No existe tarifa configurada para " + nivel + " en este año."));

        return cuotaRepository.actualizarMontoPendientes(anioId, nivel, EstadoCuota.DEBE, tarifa.getMontoMensualidad());
    }

    @Transactional
    public void copiarDesdeAnio(Integer origenId, Integer destinoId) {
        AnioEscolar destino = anioRepository.findById(destinoId)
                .orElseThrow(() -> new NegocioException("No existe el año destino"));

        if (!destino.estaAbierto()) {
            throw new NegocioException("El año destino está cerrado.");
        }

        List<Tarifa> origenTarifas = tarifaRepository.findByAnioEscolarIdOrderByNivelAsc(origenId);
        if (origenTarifas.isEmpty()) {
            throw new NegocioException("El año de origen no tiene tarifas configuradas.");
        }

        for (Tarifa origen : origenTarifas) {
            Tarifa tarifa = tarifaRepository.findByAnioEscolarIdAndNivel(destinoId, origen.getNivel())
                    .orElse(new Tarifa(destino, origen.getNivel(), origen.getMontoMatricula(), origen.getMontoMensualidad()));

            tarifa.setMontoMatricula(origen.getMontoMatricula());
            tarifa.setMontoMensualidad(origen.getMontoMensualidad());
            tarifaRepository.save(tarifa);
        }
    }
}
