package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "grado",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nivel", "nombre"}))
public class Grado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 20)
    private Nivel nivel;

    /** "3 años", "1°", "2°"... */
    @Column(name = "nombre", nullable = false, length = 20)
    private String nombre;

    /** Posicion en los listados: 3 años (1) ... 5° Secundaria (14). */
    @Column(name = "orden", nullable = false)
    private Integer orden;

    public Grado() {
    }

    public Grado(Nivel nivel, String nombre, Integer orden) {
        this.nivel = nivel;
        this.nombre = nombre;
        this.orden = orden;
    }

    /** "1° Primaria", "4 años Inicial". */
    public String getDescripcion() {
        String n = nivel.name();
        return nombre + " " + n.charAt(0) + n.substring(1).toLowerCase();
    }

    public Integer getId() {
        return id;
    }

    public Nivel getNivel() {
        return nivel;
    }

    public void setNivel(Nivel nivel) {
        this.nivel = nivel;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }
}
