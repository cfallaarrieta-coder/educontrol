package com.example.demo.repository;

import com.example.demo.entity.Alumno;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, Integer> {

    /**
     * BLOQUEO PESIMISTA sobre el alumno (SELECT ... FOR UPDATE).
     * Evita que dos secretarias matriculen al MISMO alumno a la vez en
     * secciones distintas: la segunda espera y luego ve que ya tiene matricula.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("select a from Alumno a where a.id = :id")
    Optional<Alumno> findByIdParaMatricula(Integer id);

    List<Alumno> findAllByOrderByApellidosAscNombresAsc();

    /** Buscador: coincidencia parcial en DNI, nombres o apellidos. */
    @Query("""
            select a from Alumno a
            where a.dni like concat('%', :texto, '%')
               or lower(a.nombres) like lower(concat('%', :texto, '%'))
               or lower(a.apellidos) like lower(concat('%', :texto, '%'))
            order by a.apellidos, a.nombres
            """)
    List<Alumno> buscar(String texto);

    /**
     * Filtra alumnos por texto y por su situacion academica en un anio escolar (:anioId).
     */
    @Query("""
            select a from Alumno a
            where (:texto is null
                   or a.dni like concat('%', :texto, '%')
                   or lower(a.nombres) like lower(concat('%', :texto, '%'))
                   or lower(a.apellidos) like lower(concat('%', :texto, '%')))
              and (
                   :situacion is null
                   or (:situacion = 'SIN_MATRICULA' and not exists (
                       select 1 from Matricula m
                       where m.alumno = a
                         and m.seccion.anioEscolar.id = :anioId
                         and m.estado in (com.example.demo.entity.EstadoMatricula.INSCRITA,
                                          com.example.demo.entity.EstadoMatricula.MATRICULADA)
                   ))
                   or (:situacion = 'INSCRITA' and exists (
                       select 1 from Matricula m
                       where m.alumno = a
                         and m.seccion.anioEscolar.id = :anioId
                         and m.estado = com.example.demo.entity.EstadoMatricula.INSCRITA
                   ))
                   or (:situacion = 'MATRICULADA' and exists (
                       select 1 from Matricula m
                       where m.alumno = a
                         and m.seccion.anioEscolar.id = :anioId
                         and m.estado = com.example.demo.entity.EstadoMatricula.MATRICULADA
                   ))
              )
            order by a.apellidos asc, a.nombres asc
            """)
    List<Alumno> filtrarPorSituacion(Integer anioId, String situacion, String texto);

    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(String dni, Integer id);
}
