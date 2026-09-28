package com.example.demo.entity;

import com.example.demo.exception.NegocioException;
import jakarta.persistence.*;

/**
 * Una seccion (A, B, C...) de un grado en un anio escolar.
 * Es el recurso LIMITADO del sistema: vacantesDisponibles nunca puede bajar de 0.
 * Por eso toda modificacion de esta fila se hace con bloqueo pesimista
 * (ver SeccionRepository.findByIdParaActualizar).
 */
@Entity
@Table(name = "seccion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"anio_escolar_id", "grado_id", "letra"}))
public class Seccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // LAZY: al bloquear la seccion solo se bloquea SU fila, no la del grado ni la del anio.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anio_escolar_id", nullable = false)
    private AnioEscolar anioEscolar;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grado_id", nullable = false)
    private Grado grado;

    @Column(name = "letra", nullable = false, length = 2)
    private String letra;

    @Enumerated(EnumType.STRING)
    @Column(name = "turno", nullable = false, length = 10)
    private Turno turno;

    @Column(name = "aula", length = 30)
    private String aula;

    /** Docente tutor (opcional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id")
    private Docente tutor;

    @Column(name = "vacantes_maximas", nullable = false)
    private Integer vacantesMaximas;

    @Column(name = "vacantes_disponibles", nullable = false)
    private Integer vacantesDisponibles;

    public Seccion() {
    }

    public Seccion(AnioEscolar anioEscolar, Grado grado, String letra, Turno turno,
                   String aula, Docente tutor, Integer vacantesMaximas) {
        this.anioEscolar = anioEscolar;
        this.grado = grado;
        this.letra = letra;
        this.turno = turno;
        this.aula = aula;
        this.tutor = tutor;
        this.vacantesMaximas = vacantesMaximas;
        this.vacantesDisponibles = vacantesMaximas;
    }

    // =========================================================
    // Reglas de las vacantes
    // =========================================================

    public int getMatriculados() {
        return vacantesMaximas - vacantesDisponibles;
    }

    /** Toma una vacante. Llamar SOLO con la fila bloqueada. */
    public void ocuparVacante() {
        if (vacantesDisponibles <= 0) {
            throw new NegocioException("La seccion " + getDescripcion() + " ya no tiene vacantes");
        }
        vacantesDisponibles--;
    }

    /** Devuelve una vacante (anulacion o traslado). Llamar SOLO con la fila bloqueada. */
    public void liberarVacante() {
        if (vacantesDisponibles < vacantesMaximas) {
            vacantesDisponibles++;
        }
    }

    /** Cambia el tope respetando a los alumnos ya matriculados. */
    public void cambiarVacantesMaximas(int nuevoMaximo) {
        int matriculados = getMatriculados();
        if (nuevoMaximo < matriculados) {
            throw new NegocioException("La seccion ya tiene " + matriculados
                    + " matriculados: las vacantes no pueden ser menos que eso");
        }
        this.vacantesMaximas = nuevoMaximo;
        this.vacantesDisponibles = nuevoMaximo - matriculados;
    }

    /** "1° Primaria - A". */
    public String getDescripcion() {
        return grado.getDescripcion() + " - " + letra;
    }

    public Integer getId() {
        return id;
    }

    public AnioEscolar getAnioEscolar() {
        return anioEscolar;
    }

    public void setAnioEscolar(AnioEscolar anioEscolar) {
        this.anioEscolar = anioEscolar;
    }

    public Grado getGrado() {
        return grado;
    }

    public void setGrado(Grado grado) {
        this.grado = grado;
    }

    public String getLetra() {
        return letra;
    }

    public void setLetra(String letra) {
        this.letra = letra;
    }

    public Turno getTurno() {
        return turno;
    }

    public void setTurno(Turno turno) {
        this.turno = turno;
    }

    public String getAula() {
        return aula;
    }

    public void setAula(String aula) {
        this.aula = aula;
    }

    public Docente getTutor() {
        return tutor;
    }

    public void setTutor(Docente tutor) {
        this.tutor = tutor;
    }

    public Integer getVacantesMaximas() {
        return vacantesMaximas;
    }

    public Integer getVacantesDisponibles() {
        return vacantesDisponibles;
    }
}
