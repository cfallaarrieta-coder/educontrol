package com.example.demo.repository;

import com.example.demo.entity.Seccion;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeccionRepository extends JpaRepository<Seccion, Integer> {

    /**
     * BLOQUEO PESIMISTA: en MySQL genera
     *     select ... from seccion where id = ? FOR UPDATE
     *
     * La fila queda bloqueada hasta el commit/rollback de la transaccion.
     * Si otra transaccion pide la misma seccion, ESPERA en esta linea
     * y, cuando entra, ya ve las vacantes actualizadas. Asi es imposible
     * vender la misma vacante dos veces.
     *
     * El hint limita la espera (10 s); si se supera, Spring lanza
     * PessimisticLockingFailureException y el usuario ve un mensaje claro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("select s from Seccion s where s.id = :id")
    Optional<Seccion> findByIdParaActualizar(Integer id);

    /** Listado con filtros opcionales (null = sin filtrar). */
    @EntityGraph(attributePaths = {"anioEscolar", "grado", "tutor"})
    @Query("""
            select s from Seccion s
            where (:anioId is null or s.anioEscolar.id = :anioId)
              and (:gradoId is null or s.grado.id = :gradoId)
            order by s.anioEscolar.anio desc, s.grado.orden, s.letra
            """)
    List<Seccion> filtrar(Integer anioId, Integer gradoId);

    boolean existsByAnioEscolarIdAndGradoIdAndLetra(Integer anioId, Integer gradoId, String letra);

    boolean existsByGradoId(Integer gradoId);

    boolean existsByTutorId(Integer docenteId);

    boolean existsByAnioEscolarId(Integer anioId);

    long countByAnioEscolarId(Integer anioId);

    long countByAnioEscolarIdAndVacantesDisponibles(Integer anioId, Integer vacantes);

    @Query("select coalesce(sum(s.vacantesDisponibles), 0) from Seccion s where s.anioEscolar.id = :anioId")
    long sumarVacantesDisponibles(Integer anioId);
}
