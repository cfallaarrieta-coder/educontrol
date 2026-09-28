package com.example.demo.repository;

import com.example.demo.entity.AnioEscolar;
import com.example.demo.entity.EstadoAnio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnioEscolarRepository extends JpaRepository<AnioEscolar, Integer> {

    List<AnioEscolar> findAllByOrderByAnioDesc();

    /** El anio "vigente" para el dashboard: el abierto mas reciente. */
    Optional<AnioEscolar> findFirstByEstadoOrderByAnioDesc(EstadoAnio estado);

    Optional<AnioEscolar> findByAnio(Integer anio);

    boolean existsByAnio(Integer anio);

    boolean existsByAnioAndIdNot(Integer anio, Integer id);
}
