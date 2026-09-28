package com.example.demo.repository;

import com.example.demo.entity.Grado;
import com.example.demo.entity.Nivel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradoRepository extends JpaRepository<Grado, Integer> {

    List<Grado> findAllByOrderByOrdenAsc();

    boolean existsByNivelAndNombre(Nivel nivel, String nombre);

    boolean existsByNivelAndNombreAndIdNot(Nivel nivel, String nombre, Integer id);
}
