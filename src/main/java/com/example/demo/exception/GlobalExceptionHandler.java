package com.example.demo.exception;


import com.example.demo.dto.MensajeResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Convierte cualquier excepcion en un JSON con la forma { "mensaje": "..." },
 * para que el JavaScript siempre pueda leer resp.mensaje y mostrarlo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Reglas de negocio: sin vacantes, DNI repetido, etc. -> 400 */
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<MensajeResponse> negocio(NegocioException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Falla una anotacion @NotBlank, @Size, @Pattern... -> 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MensajeResponse> validacion(MethodArgumentNotValidException ex) {

        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Datos invalidos");

        return responder(HttpStatus.BAD_REQUEST, mensaje);
    }

    /** JSON mal formado, fecha invalida, valor de enum inexistente... -> 400 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<MensajeResponse> datosIlegibles(Exception ex) {
        return responder(HttpStatus.BAD_REQUEST, "Los datos enviados no tienen un formato valido");
    }

    /**
     * BLOQUEO PESIMISTA: otra transaccion tuvo la fila bloqueada mas tiempo
     * del permitido (lock timeout) o MySQL detecto un deadlock -> 409
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<MensajeResponse> bloqueo(PessimisticLockingFailureException ex) {
        return responder(HttpStatus.CONFLICT,
                "El registro esta siendo modificado por otro usuario. Intenta nuevamente en unos segundos.");
    }

    /** Clave unica o foranea violada (respaldo de las validaciones del service) -> 409 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<MensajeResponse> integridad(DataIntegrityViolationException ex) {
        return responder(HttpStatus.CONFLICT,
                "No se pudo guardar: el dato ya existe o esta relacionado con otros registros");
    }

    /** Archivo o ruta inexistente -> 404 (antes caia en el 500 de abajo) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<MensajeResponse> noEncontrado(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    /** Cualquier otro error no previsto -> 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeResponse> general(Exception ex) {

        // El detalle tecnico va a la consola, no al navegador.
        ex.printStackTrace();

        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error inesperado en el servidor");
    }

    private static ResponseEntity<MensajeResponse> responder(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new MensajeResponse(mensaje));
    }
}
