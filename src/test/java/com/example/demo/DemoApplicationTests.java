package com.example.demo;

import com.example.demo.dto.AlumnoResponse;
import com.example.demo.dto.MatriculaRequest;
import com.example.demo.dto.MatriculaResponse;
import com.example.demo.entity.*;
import com.example.demo.repository.AlumnoRepository;
import com.example.demo.repository.AnioEscolarRepository;
import com.example.demo.repository.GradoRepository;
import com.example.demo.repository.SeccionRepository;
import com.example.demo.service.AlumnoService;
import com.example.demo.service.MatriculaService;
import com.example.demo.service.PagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DemoApplicationTests {

    @Autowired AlumnoService alumnoService;
    @Autowired MatriculaService matriculaService;
    @Autowired PagoService pagoService;
    @Autowired AlumnoRepository alumnoRepository;
    @Autowired AnioEscolarRepository anioRepository;
    @Autowired GradoRepository gradoRepository;
    @Autowired SeccionRepository seccionRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void filtroAlumnosPorSituacionEnAnioEscolar() {
        AnioEscolar anio = anioRepository.findByAnio(LocalDate.now().getYear()).orElseThrow();
        Grado grado = gradoRepository.findAllByOrderByOrdenAsc().getFirst();
        Seccion seccion = seccionRepository.save(new Seccion(anio, grado, "F", Turno.MANANA, null, null, 10));

        Alumno sinMat = alumnoRepository.save(new Alumno("79990001", "Lucia", "FiltroTest",
                LocalDate.of(2015, 5, 10), "Apoderado 1", null));
        Alumno inscrito = alumnoRepository.save(new Alumno("79990002", "Mateo", "FiltroTest",
                LocalDate.of(2015, 6, 11), "Apoderado 2", null));
        Alumno matriculado = alumnoRepository.save(new Alumno("79990003", "Sofia", "FiltroTest",
                LocalDate.of(2015, 7, 12), "Apoderado 3", null));
        Alumno anulado = alumnoRepository.save(new Alumno("79990004", "Diego", "FiltroTest",
                LocalDate.of(2015, 8, 13), "Apoderado 4", null));

        // 1) Mateo: INSCRITA
        MatriculaResponse mInscrito = matriculaService.inscribir(
                new MatriculaRequest(inscrito.getId(), seccion.getId()), "test");

        // 2) Sofia: MATRICULADA (pagada)
        MatriculaResponse mMatriculado = matriculaService.inscribir(
                new MatriculaRequest(matriculado.getId(), seccion.getId()), "test");
        pagoService.pagarMatricula(mMatriculado.id(), null, "cajero");

        // 3) Diego: INSCRITA y luego ANULADA -> vuelve a ser SIN_MATRICULA con tuvoAnulacionEnAnio = true
        MatriculaResponse mAnulado = matriculaService.inscribir(
                new MatriculaRequest(anulado.getId(), seccion.getId()), "test");
        matriculaService.anular(mAnulado.id(), "test");

        // Filtrar SIN_MATRICULA con apellido FiltroTest
        List<AlumnoResponse> sinMatriculaList = alumnoService.buscar("FiltroTest", anio.getId(), SituacionAlumno.SIN_MATRICULA);
        assertEquals(2, sinMatriculaList.size());

        AlumnoResponse respLucia = sinMatriculaList.stream()
                .filter(a -> a.id().equals(sinMat.getId())).findFirst().orElseThrow();
        assertEquals(SituacionAlumno.SIN_MATRICULA, respLucia.situacion());
        assertNull(respLucia.seccionActual());
        assertFalse(respLucia.tuvoAnulacionEnAnio());

        AlumnoResponse respDiego = sinMatriculaList.stream()
                .filter(a -> a.id().equals(anulado.getId())).findFirst().orElseThrow();
        assertEquals(SituacionAlumno.SIN_MATRICULA, respDiego.situacion());
        assertNull(respDiego.seccionActual());
        assertTrue(respDiego.tuvoAnulacionEnAnio());

        // Filtrar INSCRITA con apellido FiltroTest
        List<AlumnoResponse> inscritosList = alumnoService.buscar("FiltroTest", anio.getId(), SituacionAlumno.INSCRITA);
        assertEquals(1, inscritosList.size());
        assertEquals(inscrito.getId(), inscritosList.getFirst().id());
        assertEquals(SituacionAlumno.INSCRITA, inscritosList.getFirst().situacion());
        assertEquals(mInscrito.id(), inscritosList.getFirst().matriculaActivaId());
        assertEquals(seccion.getDescripcion(), inscritosList.getFirst().seccionActual());

        // Filtrar MATRICULADA con apellido FiltroTest
        List<AlumnoResponse> matriculadosList = alumnoService.buscar("FiltroTest", anio.getId(), SituacionAlumno.MATRICULADA);
        assertEquals(1, matriculadosList.size());
        assertEquals(matriculado.getId(), matriculadosList.getFirst().id());
        assertEquals(SituacionAlumno.MATRICULADA, matriculadosList.getFirst().situacion());
        assertEquals(mMatriculado.id(), matriculadosList.getFirst().matriculaActivaId());
        assertEquals(seccion.getDescripcion(), matriculadosList.getFirst().seccionActual());
    }
}
