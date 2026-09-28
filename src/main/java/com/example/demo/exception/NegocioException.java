package com.example.demo.exception;

/**
 * Error esperado por reglas del negocio: contraseña incorrecta,
 * seccion sin vacantes, DNI repetido, etc. Se traduce a un JSON con
 * código 400 y un mensaje legible para el usuario.
 */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
