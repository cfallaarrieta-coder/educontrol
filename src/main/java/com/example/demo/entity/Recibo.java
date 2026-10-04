package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recibo")
public class Recibo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @Column(name = "concepto", nullable = false, length = 100)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "cajero", nullable = false, length = 50)
    private String cajero;

    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    public Recibo() {
    }

    public Recibo(String numero, Matricula matricula, String concepto, BigDecimal monto, String cajero, String metodoPago) {
        this.numero = numero;
        this.matricula = matricula;
        this.concepto = concepto;
        this.monto = monto;
        this.fecha = LocalDateTime.now();
        this.cajero = cajero;
        this.metodoPago = (metodoPago == null || metodoPago.isBlank()) ? "EFECTIVO" : metodoPago.trim().toUpperCase();
    }

    public Integer getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public Matricula getMatricula() {
        return matricula;
    }

    public void setMatricula(Matricula matricula) {
        this.matricula = matricula;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getCajero() {
        return cajero;
    }

    public void setCajero(String cajero) {
        this.cajero = cajero;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }
}
