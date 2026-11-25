package pe.tecsup.chambape.comun;

import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reemplaza la página de error de Spring Boot ({timestamp, status, error, path}): los errores que
 * ocurren antes de llegar a un controlador (Tomcat, filtros) también salen como {"detail": ...}.
 */
@RestController
public class ErroresDelServidor implements ErrorController {

    private static final Map<Integer, String> MENSAJES = Map.of(
        400, "Malformed request.",
        404, "Not found.",
        405, "Method not allowed.",
        500, "A server error occurred.");

    static String mensaje(int estado) {
        String texto = MENSAJES.get(estado);
        if (texto != null) {
            return texto;
        }
        HttpStatus conocido = HttpStatus.resolve(estado);
        return conocido != null ? conocido.getReasonPhrase() + "." : "Error.";
    }

    @RequestMapping("/error")
    public ResponseEntity<Map<String, String>> error(HttpServletRequest peticion) {
        Object codigo = peticion.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int estado = codigo instanceof Integer numero ? numero : 500;
        return ResponseEntity.status(estado).body(Map.of("detail", mensaje(estado)));
    }
}
