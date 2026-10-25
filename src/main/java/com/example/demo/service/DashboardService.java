package com.example.demo.service;

import com.example.demo.dto.DashboardResponse;
import com.example.demo.entity.AnioEscolar;
import com.example.demo.entity.EstadoAnio;
import com.example.demo.entity.EstadoMatricula;
import com.example.demo.entity.Nivel;
import com.example.demo.repository.AlumnoRepository;
import com.example.demo.repository.AnioEscolarRepository;
import com.example.demo.repository.MatriculaRepository;
import com.example.demo.repository.SeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final AnioEscolarRepository anioRepository;
    private final AlumnoRepository alumnoRepository;
    private final SeccionRepository seccionRepository;
    private final MatriculaRepository matriculaRepository;
    private final RedisService redisService;

    public DashboardService(AnioEscolarRepository anioRepository,
                            AlumnoRepository alumnoRepository,
                            SeccionRepository seccionRepository,
                            MatriculaRepository matriculaRepository,
                            RedisService redisService) {
        this.anioRepository = anioRepository;
        this.alumnoRepository = alumnoRepository;
        this.seccionRepository = seccionRepository;
        this.matriculaRepository = matriculaRepository;
        this.redisService = redisService;
    }

    /**
     * Cifras del anio escolar abierto mas reciente.
     * Punto 3 de REDIS_SPEC.md: se guarda copia en Redis por 10 minutos
     * y se invalida/actualiza al instante cuando ocurre una matricula, anulacion, traslado o pago.
     */
    @Transactional(readOnly = true)
    public DashboardResponse resumen() {
        return redisService.obtenerOCalcularDashboard(this::calcularResumenDesdeMySQL);
    }

    private DashboardResponse calcularResumenDesdeMySQL() {
        long alumnos = alumnoRepository.count();

        AnioEscolar anio = anioRepository.findFirstByEstadoOrderByAnioDesc(EstadoAnio.ABIERTO)
                .orElse(null);

        if (anio == null) {
            return new DashboardResponse(null, alumnos, 0, 0, 0, 0, 0, 0, 0);
        }

        List<Object[]> porNivel = matriculaRepository.contarPorNivel(anio.getId());

        return new DashboardResponse(
                anio.getAnio(),
                alumnos,
                matriculaRepository.countBySeccionAnioEscolarIdAndEstado(anio.getId(), EstadoMatricula.MATRICULADA),
                seccionRepository.sumarVacantesDisponibles(anio.getId()),
                seccionRepository.countByAnioEscolarId(anio.getId()),
                seccionRepository.countByAnioEscolarIdAndVacantesDisponibles(anio.getId(), 0),
                contar(porNivel, Nivel.INICIAL),
                contar(porNivel, Nivel.PRIMARIA),
                contar(porNivel, Nivel.SECUNDARIA)
        );
    }

    private static long contar(List<Object[]> filas, Nivel nivel) {
        return filas.stream()
                .filter(fila -> fila[0] == nivel)
                .mapToLong(fila -> ((Number) fila[1]).longValue())
                .findFirst()
                .orElse(0);
    }
}
