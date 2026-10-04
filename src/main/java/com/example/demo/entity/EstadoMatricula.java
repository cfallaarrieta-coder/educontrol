package com.example.demo.entity;

/** Una matricula anulada devuelve su vacante. */
public enum EstadoMatricula {
    INSCRITA, MATRICULADA, ANULADA,

    /**
     * Estado de la version anterior (antes del modulo de pagos). Se mantiene solo para
     * poder leer filas antiguas de la BD; DataSeeder las migra a MATRICULADA al arrancar.
     * No usar en codigo nuevo.
     */
    @Deprecated
    ACTIVA
}
