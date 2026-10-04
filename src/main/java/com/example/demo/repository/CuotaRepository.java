package com.example.demo.repository;

import com.example.demo.entity.Cuota;
import com.example.demo.entity.EstadoCuota;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuotaRepository extends JpaRepository<Cuota, Integer> {

    @EntityGraph(attributePaths = {"recibo"})
    List<Cuota> findByMatriculaIdOrderByNumeroCuotaAsc(Integer matriculaId);

    Optional<Cuota> findByMatriculaIdAndNumeroCuota(Integer matriculaId, Integer numeroCuota);

    boolean existsByMatriculaIdAndNumeroCuotaAndEstado(Integer matriculaId, Integer numeroCuota, EstadoCuota estado);

    long countByMatriculaIdAndEstado(Integer matriculaId, EstadoCuota estado);

    void deleteByMatriculaId(Integer matriculaId);
}
