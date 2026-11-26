package pe.tecsup.chambape.usuarios;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.tecsup.chambape.comun.FechasDjango;

/** Cuentas de Django (auth_user, usuarios_perfil) y sus sanciones de HU-13; Spring solo las lee. */
@Component
public class Cuentas {

    public static final String CLIENTE = "cliente";
    public static final String PRESTADOR = "prestador";

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;

    public Cuentas(JdbcTemplate jdbc, FechasDjango fechas) {
        this.jdbc = jdbc;
        this.fechas = fechas;
    }

    /** La cuenta que inició sesión. {@code prestadorId} es null si no tiene perfil de prestador. */
    public record Usuario(long id, String rol, Long prestadorId) {
        public boolean esPrestador() {
            return PRESTADOR.equals(rol) && prestadorId != null;
        }
    }

    /** La cuenta activa vinculada a ese UID de Firebase. */
    public Optional<Usuario> porFirebase(String uid) {
        List<Usuario> filas = jdbc.query("""
            select u.id, p.rol, pr.id as prestador
            from usuarios_perfil p
            join auth_user u on u.id = p.usuario_id
            left join prestadores_prestador pr on pr.usuario_id = u.id
            where p.firebase_uid = ? and u.is_active = ?""",
            (fila, n) -> new Usuario(fila.getLong("id"), fila.getString("rol"),
                fila.getObject("prestador") == null ? null : fila.getLong("prestador")),
            uid, true);
        return filas.stream().findFirst();
    }

    /**
     * La cuenta activa de una sesión de la web, con el rol de {@code rol_de}: el de su perfil o,
     * sin perfil, administrador si es {@code is_staff}.
     */
    public Optional<Usuario> activaPorId(long id) {
        List<Usuario> filas = jdbc.query("""
            select u.id, u.is_staff, p.rol, pr.id as prestador
            from auth_user u
            left join usuarios_perfil p on p.usuario_id = u.id
            left join prestadores_prestador pr on pr.usuario_id = u.id
            where u.id = ? and u.is_active = ?""",
            (fila, n) -> {
                String rol = fila.getString("rol");
                if (rol == null && fila.getBoolean("is_staff")) {
                    rol = "administrador";
                }
                return new Usuario(fila.getLong("id"), rol,
                    fila.getObject("prestador") == null ? null : fila.getLong("prestador"));
            },
            id, true);
        return filas.stream().findFirst();
    }

    /** Mensaje de la sanción vigente (HU-13), igual al de Django, o vacío si no tiene. */
    public Optional<String> sancionVigente(long usuarioId) {
        Object ahora = fechas.parametro(FechasDjango.ahora());
        List<String> mensajes = jdbc.query("""
            select tipo, motivo, hasta from administracion_sancioncuenta
            where usuario_id = ? and levantada is null and (hasta is null or hasta > ?)
            order by creada desc limit 1""",
            (fila, n) -> mensaje(fila.getString("tipo"), fila.getString("motivo"),
                FechasDjango.leer(fila.getObject("hasta"))),
            usuarioId, ahora);
        return mensajes.stream().findFirst();
    }

    private static String mensaje(String tipo, String motivo, Instant hasta) {
        if ("bloqueo".equals(tipo)) {
            return "Tu cuenta está bloqueada. Motivo: " + motivo;
        }
        String fin = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm").format(hasta.atZone(LIMA));
        return "Tu cuenta está suspendida hasta el " + fin + ". Motivo: " + motivo;
    }
}
