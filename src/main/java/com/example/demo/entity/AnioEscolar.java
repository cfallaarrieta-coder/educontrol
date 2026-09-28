package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "anio_escolar")
public class AnioEscolar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "anio", nullable = false, unique = true)
    private Integer anio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoAnio estado;

    public AnioEscolar() {
    }

    public AnioEscolar(Integer anio, EstadoAnio estado) {
        this.anio = anio;
        this.estado = estado;
    }

    public boolean estaAbierto() {
        return estado == EstadoAnio.ABIERTO;
    }

    public Integer getId() {
        return id;
    }

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

    public EstadoAnio getEstado() {
        return estado;
    }

    public void setEstado(EstadoAnio estado) {
        this.estado = estado;
    }
}
