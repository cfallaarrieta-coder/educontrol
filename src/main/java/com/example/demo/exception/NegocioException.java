package com.example.demo.exception;

/**
 * Error esperado por reglas del negocio: contraseña incorrecta,
 * libro inexistente, etc. En el paso 9 se traduce a un JSON con
 * código 400 y un mensaje legible para el usuario.
 */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
