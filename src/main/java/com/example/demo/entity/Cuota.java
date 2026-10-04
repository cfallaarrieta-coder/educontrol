package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cuota", uniqueConstraints = @UniqueConstraint(columnNames = {"matricula_id", "numero_cuota"}))
public class Cuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota; // 1 a 10

    @Column(name = "mes", nullable = false, length = 20)
    private String mes; // Marzo ... Diciembre

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoCuota estado; // DEBE, PAGADO

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibo_id")
    private Recibo recibo;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    public Cuota() {
    }

    public Cuota(Matricula matricula, Integer numeroCuota, String mes, BigDecimal monto, EstadoCuota estado, LocalDate fechaVencimiento) {
        this.matricula = matricula;
        this.numeroCuota = numeroCuota;
        this.mes = mes;
        this.monto = monto;
        this.estado = estado;
        this.fechaVencimiento = fechaVencimiento;
    }

    public boolean estaPagado() {
        return estado == EstadoCuota.PAGADO;
    }

    public boolean estaDebe() {
        return estado == EstadoCuota.DEBE;
    }

    public Integer getId() {
        return id;
    }

    public Matricula getMatricula() {
        return matricula;
    }

    public void setMatricula(Matricula matricula) {
        this.matricula = matricula;
    }

    public Integer getNumeroCuota() {
        return numeroCuota;
    }

    public void setNumeroCuota(Integer numeroCuota) {
        this.numeroCuota = numeroCuota;
    }

    public String getMes() {
        return mes;
    }

    public void setMes(String mes) {
        this.mes = mes;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoCuota getEstado() {
        return estado;
    }

    public void setEstado(EstadoCuota estado) {
        this.estado = estado;
    }

    public Recibo getRecibo() {
        return recibo;
    }

    public void setRecibo(Recibo recibo) {
        this.recibo = recibo;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }
}
