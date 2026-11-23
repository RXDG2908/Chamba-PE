package pe.tecsup.chambape.comun;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(ErrorValidacion.class)
    public ResponseEntity<Map<String, List<String>>> datosInvalidos(ErrorValidacion error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.getErrores());
    }
}
