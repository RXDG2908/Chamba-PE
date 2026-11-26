package pe.tecsup.chambape.comun;

import java.util.Map;

/** Respuesta de error con estado y cuerpo propios ({"detail": ...}, {"estado": ...}), como en DRF. */
public class RespuestaError extends RuntimeException {

    private final int estado;
    private final Map<String, Object> cuerpo;

    public RespuestaError(int estado, Map<String, Object> cuerpo) {
        super("HTTP " + estado, null, false, false);
        this.estado = estado;
        this.cuerpo = cuerpo;
    }

    public static RespuestaError detalle(int estado, String mensaje) {
        return new RespuestaError(estado, Map.of("detail", mensaje));
    }

    // Mensajes de DRF (LANGUAGE_CODE = 'en-us').
    public static RespuestaError sinCredenciales() {
        return detalle(403, "Authentication credentials were not provided.");
    }

    public static RespuestaError noEncontrada() {
        return detalle(404, "No Solicitud matches the given query.");
    }

    public int getEstado() {
        return estado;
    }

    public Map<String, Object> getCuerpo() {
        return cuerpo;
    }
}
