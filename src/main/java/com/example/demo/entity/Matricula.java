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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibo_matricula_id")
    private Recibo reciboMatricula;

    public Matricula() {
    }

    public Matricula(Alumno alumno, Seccion seccion, String registradoPor) {
        this(alumno, seccion, registradoPor, EstadoMatricula.INSCRITA);
    }

    public Matricula(Alumno alumno, Seccion seccion, String registradoPor, EstadoMatricula estado) {
        this.alumno = alumno;
        this.seccion = seccion;
        this.registradoPor = registradoPor;
        this.fecha = LocalDateTime.now();
        this.estado = estado != null ? estado : EstadoMatricula.INSCRITA;
    }

    public boolean estaActiva() {
        return estado == EstadoMatricula.MATRICULADA;
    }

    public boolean estaInscrita() {
        return estado == EstadoMatricula.INSCRITA;
    }

    public boolean estaAnulada() {
        return estado == EstadoMatricula.ANULADA;
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

    public Recibo getReciboMatricula() {
        return reciboMatricula;
    }

    public void setReciboMatricula(Recibo reciboMatricula) {
        this.reciboMatricula = reciboMatricula;
    }
}
