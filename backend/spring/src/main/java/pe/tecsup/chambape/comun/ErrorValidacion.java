package pe.tecsup.chambape.comun;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Errores por campo, con la misma forma que Django REST Framework: {"campo": ["mensaje"]}. */
public class ErrorValidacion extends RuntimeException {

    private final Map<String, List<String>> errores;

    public ErrorValidacion(Map<String, List<String>> errores) {
        super("Datos inválidos: " + errores.keySet());
        this.errores = new LinkedHashMap<>(errores);
    }

    public Map<String, List<String>> getErrores() {
        return errores;
    }
}
