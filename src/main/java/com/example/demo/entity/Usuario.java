package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    /** Aquí NO va la contraseña: va su hash BCrypt (60 caracteres). */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    /*
     * Estas tres columnas se agregaron despues: se dejan "nullable" para que
     * ddl-auto=update pueda anadirlas sobre una tabla usuario que ya tiene filas.
     * El DataSeeder completa los valores que falten.
     */
    @Column(name = "nombre_completo", length = 100)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", length = 20)
    private Rol rol;

    @Column(name = "activo")
    private Boolean activo;

    public Usuario() {
    }

    public Usuario(String username, String password, String nombreCompleto, Rol rol) {
        this.username = username;
        this.password = password;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.activo = true;
    }

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }

    public boolean estaActivo() {
        return Boolean.TRUE.equals(activo);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
