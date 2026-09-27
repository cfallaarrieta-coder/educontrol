package com.example.demo.exception;


import com.example.demo.dto.MensajeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte cualquier excepcion en un JSON con la forma { "mensaje": "..." },
 * para que el JavaScript siempre pueda leer resp.mensaje y mostrarlo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Reglas de negocio: clave incorrecta, libro inexistente, etc. -> 400 */
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<MensajeResponse> negocio(NegocioException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MensajeResponse(ex.getMessage()));
    }

    /** Falla una anotacion @NotBlank, @Size, @PastOrPresent... -> 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MensajeResponse> validacion(MethodArgumentNotValidException ex) {

        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Datos invalidos");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MensajeResponse(mensaje));
    }

    /** Cualquier otro error no previsto -> 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeResponse> general(Exception ex) {

        // El detalle tecnico va a la consola, no al navegador.
        ex.printStackTrace();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MensajeResponse("Ocurrio un error inesperado en el servidor"));
    }


}
