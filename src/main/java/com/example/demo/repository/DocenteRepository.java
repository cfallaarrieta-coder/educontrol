package com.example.demo.repository;

import com.example.demo.entity.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocenteRepository extends JpaRepository<Docente, Integer> {

    List<Docente> findAllByOrderByApellidosAscNombresAsc();

    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(String dni, Integer id);
}
