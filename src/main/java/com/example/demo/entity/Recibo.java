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

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    private TipoRecibo tipo = TipoRecibo.INGRESO;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20)
    private EstadoRecibo estado = EstadoRecibo.EMITIDO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibo_origen_id")
    private Recibo reciboOrigen;

    @OneToOne(mappedBy = "reciboOrigen", fetch = FetchType.LAZY)
    private Recibo boletaDevolucion;

    @Column(name = "concepto", nullable = false, length = 120)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "cajero", nullable = false, length = 50)
    private String cajero;

    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "anulado_por", length = 50)
    private String anuladoPor;

    public Recibo() {
    }

    public Recibo(String numero, Matricula matricula, String concepto, BigDecimal monto, String cajero, String metodoPago) {
        this.numero = numero;
        this.tipo = TipoRecibo.INGRESO;
        this.estado = EstadoRecibo.EMITIDO;
        this.matricula = matricula;
        this.concepto = concepto;
        this.monto = monto;
        this.fecha = LocalDateTime.now();
        this.cajero = cajero;
        this.metodoPago = (metodoPago == null || metodoPago.isBlank()) ? "EFECTIVO" : metodoPago.trim().toUpperCase();
    }

    public static Recibo crearDevolucion(String numeroDev, Recibo reciboOriginal, String motivo, String adminUsername, String metodoDevolucion) {
        Recibo dev = new Recibo();
        dev.numero = numeroDev;
        dev.tipo = TipoRecibo.DEVOLUCION;
        dev.estado = EstadoRecibo.EMITIDO;
        dev.matricula = reciboOriginal.getMatricula();
        dev.reciboOrigen = reciboOriginal;
        dev.concepto = "Devolución - " + reciboOriginal.getConcepto();
        dev.monto = reciboOriginal.getMonto();
        dev.fecha = LocalDateTime.now();
        dev.cajero = adminUsername;
        dev.metodoPago = (metodoDevolucion == null || metodoDevolucion.isBlank())
                ? (reciboOriginal.getMetodoPago() != null ? reciboOriginal.getMetodoPago() : "EFECTIVO")
                : metodoDevolucion.trim().toUpperCase();
        dev.motivoAnulacion = motivo;
        dev.fechaAnulacion = dev.fecha;
        dev.anuladoPor = adminUsername;
        return dev;
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

    public TipoRecibo getTipo() {
        return tipo != null ? tipo : TipoRecibo.INGRESO;
    }

    public void setTipo(TipoRecibo tipo) {
        this.tipo = tipo;
    }

    public EstadoRecibo getEstado() {
        return estado != null ? estado : EstadoRecibo.EMITIDO;
    }

    public void setEstado(EstadoRecibo estado) {
        this.estado = estado;
    }

    public Matricula getMatricula() {
        return matricula;
    }

    public void setMatricula(Matricula matricula) {
        this.matricula = matricula;
    }

    public Recibo getReciboOrigen() {
        return reciboOrigen;
    }

    public void setReciboOrigen(Recibo reciboOrigen) {
        this.reciboOrigen = reciboOrigen;
    }

    public Recibo getBoletaDevolucion() {
        return boletaDevolucion;
    }

    public void setBoletaDevolucion(Recibo boletaDevolucion) {
        this.boletaDevolucion = boletaDevolucion;
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

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public void setMotivoAnulacion(String motivoAnulacion) {
        this.motivoAnulacion = motivoAnulacion;
    }

    public LocalDateTime getFechaAnulacion() {
        return fechaAnulacion;
    }

    public void setFechaAnulacion(LocalDateTime fechaAnulacion) {
        this.fechaAnulacion = fechaAnulacion;
    }

    public String getAnuladoPor() {
        return anuladoPor;
    }

    public void setAnuladoPor(String anuladoPor) {
        this.anuladoPor = anuladoPor;
    }
}
