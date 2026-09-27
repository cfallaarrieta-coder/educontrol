package com.example.demo.config;

import com.example.demo.entity.Usuario;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Se ejecuta una sola vez, justo despues de arrancar la aplicacion. */
    @Override
    public void run(String... args) {

        if (usuarioRepository.existsByUsername("admin")) {
            return;
        }

        usuarioRepository.save(
                new Usuario("admin", passwordEncoder.encode("123456"))
        );

        System.out.println(">>> Usuario inicial creado -> admin / 123456");
    }
}