package com.example.demo.repository;

import com.example.demo.entity.Recibo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReciboRepository extends JpaRepository<Recibo, Integer> {

    Optional<Recibo> findTopByOrderByIdDesc();

    @EntityGraph(attributePaths = {"matricula", "matricula.alumno", "matricula.seccion", "matricula.seccion.grado"})
    List<Recibo> findByMatriculaIdOrderByFechaDesc(Integer matriculaId);

    @EntityGraph(attributePaths = {"matricula", "matricula.alumno", "matricula.seccion", "matricula.seccion.grado"})
    @Query("""
            select r from Recibo r
            where (:anioId is null or r.matricula.seccion.anioEscolar.id = :anioId)
              and (:matriculaId is null or r.matricula.id = :matriculaId)
              and (:texto is null
                   or r.numero like concat('%', :texto, '%')
                   or r.matricula.alumno.dni like concat('%', :texto, '%')
                   or lower(r.matricula.alumno.apellidos) like lower(concat('%', :texto, '%'))
                   or lower(r.matricula.alumno.nombres) like lower(concat('%', :texto, '%')))
            order by r.fecha desc
            """)
    List<Recibo> filtrar(Integer anioId, Integer matriculaId, String texto);

    @Query("select coalesce(max(r.id), 0) from Recibo r")
    Integer obtenerUltimoId();
}
