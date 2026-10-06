package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tarifa", uniqueConstraints = @UniqueConstraint(columnNames = {"anio_escolar_id", "nivel"}))
public class Tarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anio_escolar_id", nullable = false)
    private AnioEscolar anioEscolar;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 20)
    private Nivel nivel;

    @Column(name = "monto_matricula", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoMatricula;

    @Column(name = "monto_mensualidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoMensualidad;

    public Tarifa() {
    }

    public Tarifa(AnioEscolar anioEscolar, Nivel nivel, BigDecimal montoMatricula, BigDecimal montoMensualidad) {
        this.anioEscolar = anioEscolar;
        this.nivel = nivel;
        this.montoMatricula = montoMatricula;
        this.montoMensualidad = montoMensualidad;
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

    public Nivel getNivel() {
        return nivel;
    }

    public void setNivel(Nivel nivel) {
        this.nivel = nivel;
    }

    public BigDecimal getMontoMatricula() {
        return montoMatricula;
    }

    public void setMontoMatricula(BigDecimal montoMatricula) {
        this.montoMatricula = montoMatricula;
    }

    public BigDecimal getMontoMensualidad() {
        return montoMensualidad;
    }

    public void setMontoMensualidad(BigDecimal montoMensualidad) {
        this.montoMensualidad = montoMensualidad;
    }
}
