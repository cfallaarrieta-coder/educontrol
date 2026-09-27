package com.example.demo.service;

import com.example.demo.dto.LibroRequest;
import com.example.demo.dto.LibroResponse;
import com.example.demo.entity.Libro;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.LibroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LibroService {

    private final LibroRepository libroRepository;

    public LibroService(LibroRepository libroRepository) {
        this.libroRepository = libroRepository;
    }

    @Transactional(readOnly = true)
    public List<LibroResponse> listar() {
        return libroRepository.findAllByOrderByIdAsc()
                .stream()
                .map(LibroResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LibroResponse> buscar(String texto) {

        if (texto == null || texto.isBlank()) {
            return listar();
        }

        return libroRepository
                .findByTituloContainingIgnoreCaseOrAutorContainingIgnoreCase(texto, texto)
                .stream()
                .map(LibroResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public LibroResponse obtenerPorId(Integer id) {
        return LibroResponse.desde(buscarEntidad(id));
    }

    @Transactional
    public LibroResponse crear(LibroRequest request) {

        Libro libro = new Libro(
                request.titulo().trim(),
                request.autor().trim(),
                request.fechaRegistro()
        );

        return LibroResponse.desde(libroRepository.save(libro));
    }

    @Transactional
    public LibroResponse actualizar(Integer id, LibroRequest request) {

        Libro libro = buscarEntidad(id);

        libro.setTitulo(request.titulo().trim());
        libro.setAutor(request.autor().trim());
        libro.setFechaRegistro(request.fechaRegistro());

        return LibroResponse.desde(libroRepository.save(libro));
    }

    @Transactional
    public void eliminar(Integer id) {
        libroRepository.delete(buscarEntidad(id));
    }

    /** Uso interno: recupera el libro o falla con un mensaje claro. */
    private Libro buscarEntidad(Integer id) {
        return libroRepository.findById(id)
                .orElseThrow(() ->
                        new NegocioException("No existe un libro con id " + id));
    }
}
