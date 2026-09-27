package com.example.demo.repository;

import com.example.demo.entity.Libro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LibroRepository extends JpaRepository<Libro, Integer> {

    /** Listado con el más reciente arriba. */
    List<Libro> findAllByOrderByIdAsc();

    /** Buscador del formulario: coincidencia parcial en título o autor. */
    List<Libro> findByTituloContainingIgnoreCaseOrAutorContainingIgnoreCase(
            String titulo, String autor);
}
