package pe.tecsup.chambape.comun;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Errores con la misma forma que Django REST Framework: {"campo": ["mensaje"]} al validar y
 * {"detail": "mensaje"} en lo demás, con los textos de Django (LANGUAGE_CODE = 'en-us').
 * Así la web y la app muestran el mismo mensaje con Django o con Spring.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ErrorValidacion.class)
    public ResponseEntity<Map<String, List<String>>> datosInvalidos(ErrorValidacion error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.getErrores());
    }

    @ExceptionHandler(RespuestaError.class)
    public ResponseEntity<Map<String, Object>> respuestaError(RespuestaError error) {
        return ResponseEntity.status(error.getEstado()).body(error.getCuerpo());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> metodoNoPermitido(HttpRequestMethodNotSupportedException error) {
        HttpHeaders cabeceras = new HttpHeaders();
        if (error.getSupportedHttpMethods() != null) {
            cabeceras.setAllow(error.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(cabeceras)
            .body(Map.of("detail", "Method \"" + error.getMethod() + "\" not allowed."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> noEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", ErroresDelServidor.mensaje(404)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> errorInterno(Exception error) {
        // Los errores de Spring que ya traen su estado (406, 415...) lo conservan.
        if (error instanceof ErrorResponse conEstado && conEstado.getStatusCode().is4xxClientError()) {
            int estado = conEstado.getStatusCode().value();
            return ResponseEntity.status(estado).body(Map.of("detail", ErroresDelServidor.mensaje(estado)));
        }
        // El detalle queda en el registro del servidor, nunca en la respuesta.
        LOG.error("Error no controlado", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("detail", ErroresDelServidor.mensaje(500)));
    }
}
