package com.example.demo.repository;

import com.example.demo.entity.Recibo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReciboRepository extends JpaRepository<Recibo, Integer> {

    Optional<Recibo> findTopByOrderByIdDesc();

    Optional<Recibo> findTopByNumeroStartingWithOrderByNumeroDesc(String prefijo);

    boolean existsByNumero(String numero);

    @EntityGraph(attributePaths = {
            "matricula", "matricula.alumno", "matricula.seccion",
            "matricula.seccion.grado", "matricula.seccion.anioEscolar",
            "reciboOrigen", "boletaDevolucion"
    })
    List<Recibo> findByMatriculaIdOrderByFechaDesc(Integer matriculaId);

    @EntityGraph(attributePaths = {
            "matricula", "matricula.alumno", "matricula.seccion",
            "matricula.seccion.grado", "matricula.seccion.anioEscolar",
            "reciboOrigen", "boletaDevolucion"
    })
    @Query("select r from Recibo r where r.id = :id")
    Optional<Recibo> findDetalleById(Integer id);

    @EntityGraph(attributePaths = {
            "matricula", "matricula.alumno", "matricula.seccion",
            "matricula.seccion.grado", "matricula.seccion.anioEscolar",
            "reciboOrigen", "boletaDevolucion"
    })
    @Query("""
            select r from Recibo r
            where (:anioId is null or r.matricula.seccion.anioEscolar.id = :anioId)
              and (:matriculaId is null or r.matricula.id = :matriculaId)
              and (:texto is null
                   or r.numero like concat('%', :texto, '%')
                   or r.matricula.alumno.dni like concat('%', :texto, '%')
                   or lower(r.matricula.alumno.apellidos) like lower(concat('%', :texto, '%'))
                   or lower(r.matricula.alumno.nombres) like lower(concat('%', :texto, '%')))
            order by r.fecha desc, r.id desc
            """)
    List<Recibo> filtrar(Integer anioId, Integer matriculaId, String texto);

    @Query("select count(r) from Recibo r where r.numero like concat(:prefijo, '%')")
    long contarPorPrefijo(String prefijo);

    @Query("select coalesce(max(r.id), 0) from Recibo r")
    Integer obtenerUltimoId();

    boolean existsByMatriculaSeccionId(Integer seccionId);

    boolean existsByMatriculaSeccionAnioEscolarId(Integer anioId);

    /** Rellena tipo = INGRESO y estado = EMITIDO en recibos existentes creados antes de esta version. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Recibo r
               set r.tipo = coalesce(r.tipo, com.example.demo.entity.TipoRecibo.INGRESO),
                   r.estado = coalesce(r.estado, com.example.demo.entity.EstadoRecibo.EMITIDO)
             where r.tipo is null or r.estado is null
            """)
    int migrarRecibosAntiguos();
}
