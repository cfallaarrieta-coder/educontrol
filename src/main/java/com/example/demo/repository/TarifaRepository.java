package com.example.demo.repository;

import com.example.demo.entity.Nivel;
import com.example.demo.entity.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, Integer> {

    List<Tarifa> findByAnioEscolarIdOrderByNivelAsc(Integer anioId);

    Optional<Tarifa> findByAnioEscolarIdAndNivel(Integer anioId, Nivel nivel);

    boolean existsByAnioEscolarId(Integer anioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Tarifa t where t.anioEscolar.id = :anioId")
    int deleteByAnioEscolarId(Integer anioId);
}
