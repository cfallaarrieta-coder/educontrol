package com.example.demo.repository;

import com.example.demo.entity.EstadoMatricula;
import com.example.demo.entity.Matricula;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatriculaRepository extends JpaRepository<Matricula, Integer> {

    /**
     * BLOQUEO PESIMISTA sobre la matricula: si dos personas anulan (o trasladan)
     * la misma matricula a la vez, la segunda espera y luego ve el estado real,
     * asi la vacante no se devuelve dos veces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("select m from Matricula m where m.id = :id")
    Optional<Matricula> findByIdParaActualizar(Integer id);

    /** Regla: un alumno no puede tener otra matricula inscrita o formalizada en el mismo anio escolar. */
    boolean existsByAlumnoIdAndSeccionAnioEscolarIdAndEstado(
            Integer alumnoId, Integer anioId, EstadoMatricula estado);

    boolean existsByAlumnoIdAndSeccionAnioEscolarIdAndEstadoIn(
            Integer alumnoId, Integer anioId, List<EstadoMatricula> estados);

    boolean existsBySeccionId(Integer seccionId);

    boolean existsByAlumnoId(Integer alumnoId);

    @EntityGraph(attributePaths = {"alumno", "seccion", "seccion.grado", "seccion.anioEscolar", "reciboMatricula"})
    @Query("""
            select m from Matricula m
            where (:anioId is null or m.seccion.anioEscolar.id = :anioId)
              and (:seccionId is null or m.seccion.id = :seccionId)
              and (:estado is null or m.estado = :estado)
              and (:texto is null
                   or m.alumno.dni like concat('%', :texto, '%')
                   or lower(m.alumno.apellidos) like lower(concat('%', :texto, '%'))
                   or lower(m.alumno.nombres) like lower(concat('%', :texto, '%')))
            order by m.fecha desc
            """)
    List<Matricula> filtrar(Integer anioId, Integer seccionId, EstadoMatricula estado, String texto);

    /** Nomina de una seccion en orden alfabetico (solo alumnos con matricula pagada). */
    @EntityGraph(attributePaths = {"alumno", "seccion", "seccion.grado", "seccion.anioEscolar", "reciboMatricula"})
    List<Matricula> findBySeccionIdAndEstadoOrderByAlumnoApellidosAscAlumnoNombresAsc(
            Integer seccionId, EstadoMatricula estado);

    long countBySeccionAnioEscolarIdAndEstado(Integer anioId, EstadoMatricula estado);

    long countBySeccionIdAndEstado(Integer seccionId, EstadoMatricula estado);

    /** Matriculados activos por nivel: [Nivel, cantidad]. */
    @Query("""
            select m.seccion.grado.nivel, count(m) from Matricula m
            where m.estado = com.example.demo.entity.EstadoMatricula.MATRICULADA
              and m.seccion.anioEscolar.id = :anioId
            group by m.seccion.grado.nivel
            """)
    List<Object[]> contarPorNivel(Integer anioId);
}
