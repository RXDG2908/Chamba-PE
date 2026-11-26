package pe.tecsup.chambape.servicios;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import pe.tecsup.chambape.comun.FechasDjango;
import pe.tecsup.chambape.usuarios.SesionDjango;

/**
 * Aviso push al prestador cuando le llega una solicitud (HU-8), como {@code servicios/push.py}:
 * mensaje FCM solo de datos a los dispositivos que registró el prestador, si su sesión de Django
 * sigue vigente y es de esa misma cuenta. Usa la cuenta de servicio de GOOGLE_APPLICATION_CREDENTIALS. Un fallo nunca deshace
 * la solicitud: solo se registra, sin el token ni el texto del error.
 */
@Component
public class Notificador {

    private static final Logger LOG = LoggerFactory.getLogger(Notificador.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean habilitado;
    private final String proyecto;
    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;
    private final SesionDjango sesiones;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private GoogleCredentials credenciales;

    public Notificador(@Value("${chambape.fcm.habilitado:false}") boolean habilitado,
                       @Value("${chambape.fcm.proyecto:}") String proyecto, JdbcTemplate jdbc, FechasDjango fechas,
                       SesionDjango sesiones) {
        this.habilitado = habilitado && !proyecto.isEmpty();
        this.proyecto = proyecto;
        this.jdbc = jdbc;
        this.fechas = fechas;
        this.sesiones = sesiones;
    }

    private record Dispositivo(long id, String token, String sesion, boolean vigente) {
    }

    public void solicitudNueva(long solicitudId, long prestadorUsuarioId) {
        if (!habilitado) {
            return;
        }
        try {
            // Solo los dispositivos cuya sesión de Django sigue vigente y es del prestador; los demás se
            // borran (como Django). Sin DJANGO_SECRET_KEY la sesión no se puede leer: basta que siga vigente.
            Object ahora = fechas.parametro(FechasDjango.ahora());
            List<Dispositivo> dispositivos = jdbc.query("""
                select t.id, t.token, t.sesion, (select count(*) from django_session s
                                        where s.session_key = t.sesion and s.expire_date > ?) as vigente
                from servicios_tokenfcm t where t.usuario_id = ?""",
                (fila, n) -> new Dispositivo(fila.getLong("id"), fila.getString("token"), fila.getString("sesion"),
                    fila.getInt("vigente") > 0),
                ahora, prestadorUsuarioId);
            for (Dispositivo dispositivo : dispositivos) {
                boolean suya = !sesiones.disponible() ? dispositivo.vigente()
                    : sesiones.leer(dispositivo.sesion()).filter(c -> c.usuarioId() == prestadorUsuarioId).isPresent();
                if (!suya) {
                    jdbc.update("delete from servicios_tokenfcm where id = ?", dispositivo.id());
                    continue;
                }
                enviar(dispositivo, Map.of("solicitud_id", Long.toString(solicitudId),
                    "usuario_id", Long.toString(prestadorUsuarioId)), solicitudId);
            }
        } catch (Exception error) {
            LOG.warn("No se pudo notificar solicitud {} ({})", solicitudId, error.getClass().getSimpleName());
        }
    }

    private void enviar(Dispositivo dispositivo, Map<String, String> datos, long solicitudId) throws IOException, InterruptedException {
        Map<String, Object> mensaje = Map.of("message", Map.of(
            "token", dispositivo.token(), "data", datos,
            "webpush", Map.of("headers", Map.of("TTL", "300", "Urgency", "high"))));
        HttpRequest peticion = HttpRequest.newBuilder(
                URI.create("https://fcm.googleapis.com/v1/projects/" + proyecto + "/messages:send"))
            .timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer " + accesoGoogle())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(mensaje)))
            .build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() == 200) {
            LOG.info("FCM aceptó envío de solicitud {}; entrega no confirmada", solicitudId);
            return;
        }
        // Token que FCM ya no reconoce: se borra, como UnregisteredError en Django.
        if (respuesta.statusCode() == 404 || respuesta.body().contains("UNREGISTERED")) {
            jdbc.update("delete from servicios_tokenfcm where id = ?", dispositivo.id());
        }
        LOG.warn("FCM falló para solicitud {} (HTTP {})", solicitudId, respuesta.statusCode());
    }

    private synchronized String accesoGoogle() throws IOException {
        if (credenciales == null) {
            credenciales = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/firebase.messaging");
        }
        credenciales.refreshIfExpired();
        return credenciales.getAccessToken().getTokenValue();
    }
}
