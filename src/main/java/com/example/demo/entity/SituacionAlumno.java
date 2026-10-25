package com.example.demo.entity;

public enum SituacionAlumno {
    /** No tiene registro INSCRITA ni MATRICULADA en el año escolar (incluye nuevos o con matrícula ANULADA). */
    SIN_MATRICULA,
    /** Tiene reserva de matrícula pendiente de pago en Caja en el año escolar. */
    INSCRITA,
    /** Tiene matrícula pagada y formalizada en el año escolar. */
    MATRICULADA
}
