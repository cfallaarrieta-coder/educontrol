package com.example.demo.service;

import com.example.demo.dto.AnioEscolarRequest;
import com.example.demo.dto.AnioEscolarResponse;
import com.example.demo.entity.AnioEscolar;
import com.example.demo.entity.EstadoMatricula;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.AnioEscolarRepository;
import com.example.demo.repository.CuotaRepository;
import com.example.demo.repository.MatriculaRepository;
import com.example.demo.repository.ReciboRepository;
import com.example.demo.repository.SeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnioEscolarService {

    private final AnioEscolarRepository anioRepository;
    private final SeccionRepository seccionRepository;
    private final MatriculaRepository matriculaRepository;
    private final CuotaRepository cuotaRepository;
    private final ReciboRepository reciboRepository;

    public AnioEscolarService(AnioEscolarRepository anioRepository,
                              SeccionRepository seccionRepository,
                              MatriculaRepository matriculaRepository,
                              CuotaRepository cuotaRepository,
                              ReciboRepository reciboRepository) {
        this.anioRepository = anioRepository;
        this.seccionRepository = seccionRepository;
        this.matriculaRepository = matriculaRepository;
        this.cuotaRepository = cuotaRepository;
        this.reciboRepository = reciboRepository;
    }

    @Transactional(readOnly = true)
    public List<AnioEscolarResponse> listar() {
        return anioRepository.findAllByOrderByAnioDesc()
                .stream()
                .map(AnioEscolarResponse::desde)
                .toList();
    }

    @Transactional
    public AnioEscolarResponse crear(AnioEscolarRequest request) {
        if (anioRepository.existsByAnio(request.anio())) {
            throw new NegocioException("El anio escolar " + request.anio() + " ya existe");
        }
        return AnioEscolarResponse.desde(anioRepository.save(new AnioEscolar(request.anio(), request.estado())));
    }

    @Transactional
    public AnioEscolarResponse actualizar(Integer id, AnioEscolarRequest request) {
        if (anioRepository.existsByAnioAndIdNot(request.anio(), id)) {
            throw new NegocioException("El anio escolar " + request.anio() + " ya existe");
        }
        AnioEscolar anio = buscarEntidad(id);
        anio.setAnio(request.anio());
        anio.setEstado(request.estado());
        return AnioEscolarResponse.desde(anio);
    }

    /**
     * Elimina el anio junto con SUS secciones (las secciones pertenecen a un solo anio).
     * Los grados NO se tocan: son un catalogo compartido por todos los anios.
     * Se bloquea si hay matriculas vigentes o pagos registrados; las matriculas
     * ANULADAS (y sus cuotas pendientes) se eliminan junto con las secciones.
     */
    @Transactional
    public void eliminar(Integer id) {
        buscarEntidad(id); // valida que exista

        if (matriculaRepository.existsBySeccionAnioEscolarIdAndEstadoNot(id, EstadoMatricula.ANULADA)) {
            throw new NegocioException("No se puede eliminar: el anio escolar tiene matriculas vigentes. "
                    + "Anulelas primero");
        }
        if (reciboRepository.existsByMatriculaSeccionAnioEscolarId(id)) {
            throw new NegocioException("No se puede eliminar: el anio escolar tiene pagos registrados");
        }

        // Orden por llaves foraneas: cuota -> matricula -> seccion -> anio_escolar
        cuotaRepository.eliminarPorAnio(id);
        matriculaRepository.eliminarPorAnio(id);
        seccionRepository.eliminarPorAnio(id);
        anioRepository.deleteById(id);
    }

    private AnioEscolar buscarEntidad(Integer id) {
        return anioRepository.findById(id)
                .orElseThrow(() -> new NegocioException("No existe el anio escolar " + id));
    }
}
