package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos de prueba para la demo. Cada bloque solo se ejecuta si su tabla esta vacia,
 * asi que reiniciar la aplicacion no duplica nada.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final AnioEscolarRepository anioRepository;
    private final GradoRepository gradoRepository;
    private final DocenteRepository docenteRepository;
    private final SeccionRepository seccionRepository;
    private final AlumnoRepository alumnoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      AnioEscolarRepository anioRepository,
                      GradoRepository gradoRepository,
                      DocenteRepository docenteRepository,
                      SeccionRepository seccionRepository,
                      AlumnoRepository alumnoRepository,
                      PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.anioRepository = anioRepository;
        this.gradoRepository = gradoRepository;
        this.docenteRepository = docenteRepository;
        this.seccionRepository = seccionRepository;
        this.alumnoRepository = alumnoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Se ejecuta una sola vez, justo despues de arrancar la aplicacion. */
    @Override
    @Transactional
    public void run(String... args) {
        usuarios();
        AnioEscolar anio = anioEscolar();
        List<Grado> grados = grados();
        List<Docente> docentes = docentes();
        secciones(anio, grados, docentes);
        alumnos();
    }

    private void usuarios() {

        // Cuentas creadas con la version anterior (sin rol): pasan a ADMIN.
        usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == null)
                .forEach(u -> {
                    u.setRol(Rol.ADMIN);
                    u.setActivo(true);
                    u.setNombreCompleto(u.getUsername());
                });

        if (!usuarioRepository.existsByUsername("admin")) {
            usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("123456"),
                    "Director General", Rol.ADMIN));
            System.out.println(">>> Usuario inicial creado -> admin / 123456");
        }
        if (!usuarioRepository.existsByUsername("secretaria")) {
            usuarioRepository.save(new Usuario("secretaria", passwordEncoder.encode("123456"),
                    "Secretaria Academica", Rol.SECRETARIA));
            System.out.println(">>> Usuario inicial creado -> secretaria / 123456");
        }
    }

    private AnioEscolar anioEscolar() {
        int actual = LocalDate.now().getYear();
        return anioRepository.findByAnio(actual)
                .orElseGet(() -> anioRepository.save(new AnioEscolar(actual, EstadoAnio.ABIERTO)));
    }

    private List<Grado> grados() {
        if (gradoRepository.count() == 0) {
            int orden = 1;
            for (String edad : List.of("3 años", "4 años", "5 años")) {
                gradoRepository.save(new Grado(Nivel.INICIAL, edad, orden++));
            }
            for (int g = 1; g <= 6; g++) {
                gradoRepository.save(new Grado(Nivel.PRIMARIA, g + "°", orden++));
            }
            for (int g = 1; g <= 5; g++) {
                gradoRepository.save(new Grado(Nivel.SECUNDARIA, g + "°", orden++));
            }
        }
        return gradoRepository.findAllByOrderByOrdenAsc();
    }

    private List<Docente> docentes() {
        if (docenteRepository.count() == 0) {
            docenteRepository.saveAll(List.of(
                    new Docente("40112233", "Rosa", "Huaman Torres", "Educacion Inicial", "987654321"),
                    new Docente("40223344", "Carlos", "Mendoza Rios", "Educacion Primaria", "987111222"),
                    new Docente("40334455", "Lucia", "Paredes Soto", "Matematica", "987333444"),
                    new Docente("40445566", "Jorge", "Salazar Vega", "Comunicacion", "987555666")
            ));
        }
        return docenteRepository.findAllByOrderByApellidosAscNombresAsc();
    }

    /**
     * Secciones A y B para cada grado. "1° Primaria - C" tiene solo 2 vacantes:
     * es la seccion pensada para demostrar el bloqueo pesimista en vivo.
     */
    private void secciones(AnioEscolar anio, List<Grado> grados, List<Docente> docentes) {
        if (seccionRepository.existsByAnioEscolarId(anio.getId())) {
            return;
        }
        int i = 0;
        for (Grado grado : grados) {
            int vacantes = grado.getNivel() == Nivel.INICIAL ? 20 : 30;
            Docente tutor = docentes.get(i++ % docentes.size());
            seccionRepository.save(new Seccion(anio, grado, "A", Turno.MANANA,
                    "Aula " + grado.getOrden() + "A", tutor, vacantes));
            seccionRepository.save(new Seccion(anio, grado, "B", Turno.TARDE,
                    "Aula " + grado.getOrden() + "B", null, vacantes));

            if (grado.getNivel() == Nivel.PRIMARIA && grado.getNombre().equals("1°")) {
                seccionRepository.save(new Seccion(anio, grado, "C", Turno.MANANA,
                        "Aula 4C", tutor, 2));
            }
        }
    }

    private void alumnos() {
        if (alumnoRepository.count() > 0) {
            return;
        }
        alumnoRepository.saveAll(List.of(
                new Alumno("78123401", "Ana Lucia", "Quispe Mamani", LocalDate.of(2019, 3, 14), "Maria Mamani Condori", "912345678"),
                new Alumno("78123402", "Diego", "Flores Chavez", LocalDate.of(2019, 7, 2), "Luis Flores Rojas", "912345679"),
                new Alumno("78123403", "Valentina", "Ramos Huerta", LocalDate.of(2019, 1, 25), "Carmen Huerta Diaz", "912345680"),
                new Alumno("78123404", "Mateo", "Torres Gutierrez", LocalDate.of(2019, 11, 8), "Pedro Torres Leon", "912345681"),
                new Alumno("78123405", "Camila", "Vargas Silva", LocalDate.of(2019, 5, 19), "Rosa Silva Paz", "912345682"),
                new Alumno("72123406", "Sebastian", "Castillo Ruiz", LocalDate.of(2014, 9, 30), "Jose Castillo Mora", "912345683"),
                new Alumno("72123407", "Isabella", "Rojas Medina", LocalDate.of(2013, 4, 12), "Elena Medina Cruz", "912345684"),
                new Alumno("70123408", "Joaquin", "Espinoza Vera", LocalDate.of(2011, 2, 5), "Raul Espinoza Tello", "912345685"),
                new Alumno("81123409", "Mia", "Cardenas Lopez", LocalDate.of(2022, 8, 21), "Julia Lopez Ramos", "912345686"),
                new Alumno("80123410", "Thiago", "Aguilar Pinto", LocalDate.of(2021, 6, 17), "Marco Aguilar Soto", "912345687")
        ));
    }
}
