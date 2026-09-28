package com.example.demo.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "matricula")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seccion_id", nullable = false)
    private Seccion seccion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMatricula estado;

    /** Username de quien registro la matricula. */
    @Column(name = "registrado_por", nullable = false, length = 50)
    private String registradoPor;

    public Matricula() {
    }

    public Matricula(Alumno alumno, Seccion seccion, String registradoPor) {
        this.alumno = alumno;
        this.seccion = seccion;
        this.registradoPor = registradoPor;
        this.fecha = LocalDateTime.now();
        this.estado = EstadoMatricula.ACTIVA;
    }

    public boolean estaActiva() {
        return estado == EstadoMatricula.ACTIVA;
    }

    public Integer getId() {
        return id;
    }

    public Alumno getAlumno() {
        return alumno;
    }

    public Seccion getSeccion() {
        return seccion;
    }

    public void setSeccion(Seccion seccion) {
        this.seccion = seccion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public EstadoMatricula getEstado() {
        return estado;
    }

    public void setEstado(EstadoMatricula estado) {
        this.estado = estado;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }
}
