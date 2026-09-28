package com.example.demo.repository;

import com.example.demo.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    /** Para el login: busca al usuario por su nombre de acceso. */
    Optional<Usuario> findByUsername(String username);

    /** Para el seeder: comprueba si ya existe sin traer el registro entero. */
    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Integer id);

    List<Usuario> findAllByOrderByUsernameAsc();
}
