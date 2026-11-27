package pe.tecsup.chambape.usuarios;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import pe.tecsup.chambape.comun.ComoDrf;
import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.comun.FechasDjango;

/**
 * Cuentas nuevas de la app móvil con las mismas filas que crea Django: {@code create_user} sin
 * contraseña utilizable, el perfil que deja la señal {@code crear_perfil} (y {@code marcar_prestador})
 * y, para el prestador, lo que valida {@code RegistroPrestadorMovilSerializer}.
 */
@Service
public class RegistroMovil {

    private static final Pattern DNI = Pattern.compile("^\\d{8}$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String LETRAS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom AZAR = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;

    public RegistroMovil(JdbcTemplate jdbc, FechasDjango fechas) {
        this.jdbc = jdbc;
        this.fechas = fechas;
    }

    /** Cuenta ya vinculada a ese UID (activa o no), con su rol. */
    public record Vinculada(long usuarioId, String rol) {
    }

    public Optional<Vinculada> vinculada(String uid) {
        return jdbc.query("select usuario_id, rol from usuarios_perfil where firebase_uid = ?",
            (fila, n) -> new Vinculada(fila.getLong("usuario_id"), fila.getString("rol")), uid).stream().findFirst();
    }

    /** {@code email__iexact}: SQLite y PostgreSQL comparan sin distinguir mayúsculas (ASCII en SQLite). */
    public boolean correoEnUso(String correo) {
        Integer total = jdbc.queryForObject("select count(*) from auth_user where upper(email) = upper(?)",
            Integer.class, correo);
        return total != null && total > 0;
    }

    /** {@code UsuarioActualSerializer} (el cuerpo de GET /api/usuarios/yo/). */
    public Map<String, Object> usuarioActual(long usuarioId) {
        return jdbc.queryForObject("""
            select u.id, u.username, u.email, u.first_name, u.last_name, u.is_staff, p.rol,
                   pr.id as prestador, pr.nombres, pr.apellidos, p.foto, pr.tipo, pr.nombre_comercial
            from auth_user u
            left join usuarios_perfil p on p.usuario_id = u.id
            left join prestadores_prestador pr on pr.usuario_id = u.id
            where u.id = ?""", (fila, n) -> {
            String rol = fila.getString("rol");
            if (rol == null && fila.getBoolean("is_staff")) {
                rol = "administrador";
            }
            boolean esPrestador = Cuentas.PRESTADOR.equals(rol) && fila.getObject("prestador") != null;
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("id", fila.getLong("id"));
            cuerpo.put("username", fila.getString("username"));
            cuerpo.put("email", fila.getString("email"));
            cuerpo.put("nombres", esPrestador ? fila.getString("nombres") : fila.getString("first_name"));
            cuerpo.put("apellidos", esPrestador ? fila.getString("apellidos") : fila.getString("last_name"));
            cuerpo.put("rol", rol);
            cuerpo.put("prestador_id", esPrestador ? fila.getLong("prestador") : null);
            // Perfil, como Django: ruta de la foto (o null) y el nombre que ven los clientes.
            String foto = fila.getString("foto");
            cuerpo.put("foto", foto == null || foto.isEmpty() ? null : "/media/" + foto);
            String comercial = fila.getString("nombre_comercial");
            cuerpo.put("nombre_publico", !esPrestador ? null
                : "empresa".equals(fila.getString("tipo")) && comercial != null && !comercial.isEmpty() ? comercial
                : (fila.getString("nombres") + " " + fila.getString("apellidos")).strip());
            return cuerpo;
        }, usuarioId);
    }

    /** POST /api/usuarios/registro-movil/: devuelve el id de la cuenta nueva. */
    @Transactional
    public long crearCliente(VerificadorFirebase.Identidad identidad, String correo, JsonNode datos) {
        // str(request.data.get('nombre') or datos.get('name') or '').strip()[:150]
        JsonNode valor = datos.isObject() ? datos.get("nombre") : null;
        String nombre = valor != null && verdadero(valor) ? ComoDrf.comoTextoPython(valor)
            : identidad.nombre() != null ? identidad.nombre() : "";
        nombre = primeros(ComoDrf.stripPython(nombre), 150);
        long usuario = crearUsuario(primeros("fb-" + identidad.uid(), 150), correo, nombre);
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values (?, ?, ?)",
            Cuentas.CLIENTE, usuario, identidad.uid());
        return usuario;
    }

    private record DatosPrestador(String nombres, String apellidos, String dni, String telefono, List<Long> rubros,
                                  BigDecimal latitud, BigDecimal longitud, int radio) {
    }

    /** POST /api/prestadores/registro-movil/: devuelve el prestador como lo muestra el serializer. */
    @Transactional
    public Map<String, Object> crearPrestador(VerificadorFirebase.Identidad identidad, String correo, JsonNode datos) {
        DatosPrestador d = validarPrestador(datos);
        long usuario = crearUsuario("fb-" + identidad.uid(), correo, "");
        GeneratedKeyHolder llave = new GeneratedKeyHolder();
        Object ahora = fechas.parametro(FechasDjango.ahora());
        jdbc.update(conexion -> {
            PreparedStatement sentencia = conexion.prepareStatement("""
                insert into prestadores_prestador (nombres, apellidos, dni, telefono, latitud, longitud,
                  radio_cobertura_km, disponible, estado, creado, usuario_id, calificacion_promedio, calificaciones_total)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'pendiente', ?, ?, null, 0)""", Statement.RETURN_GENERATED_KEYS);
            sentencia.setString(1, d.nombres());
            sentencia.setString(2, d.apellidos());
            sentencia.setString(3, d.dni());
            sentencia.setString(4, d.telefono());
            // Como Django: el decimal va como texto con sus 6 decimales.
            sentencia.setString(5, d.latitud().toPlainString());
            sentencia.setString(6, d.longitud().toPlainString());
            sentencia.setInt(7, d.radio());
            sentencia.setBoolean(8, false);
            sentencia.setObject(9, ahora);
            sentencia.setLong(10, usuario);
            return sentencia;
        }, llave);
        long prestador = id(llave);
        for (long rubro : new LinkedHashSet<>(d.rubros())) {
            jdbc.update("insert into prestadores_prestador_rubros (prestador_id, rubro_id) values (?, ?)", prestador, rubro);
        }
        // Las señales dejan el perfil con rol prestador; la vista lo vincula con el UID.
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values (?, ?, ?)",
            Cuentas.PRESTADOR, usuario, identidad.uid());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", prestador);
        cuerpo.put("nombres", d.nombres());
        cuerpo.put("apellidos", d.apellidos());
        cuerpo.put("dni", d.dni());
        cuerpo.put("telefono", d.telefono());
        // prestador.rubros.all(): en el orden de Rubro (por nombre).
        cuerpo.put("rubros", jdbc.queryForList("""
            select r.id from prestadores_rubro r join prestadores_prestador_rubros pr on pr.rubro_id = r.id
            where pr.prestador_id = ? order by r.nombre""", Long.class, prestador));
        cuerpo.put("latitud", d.latitud().toPlainString());
        cuerpo.put("longitud", d.longitud().toPlainString());
        cuerpo.put("radio_cobertura_km", d.radio());
        cuerpo.put("estado", "pendiente");
        return cuerpo;
    }

    private DatosPrestador validarPrestador(JsonNode datos) {
        if (!datos.isObject()) {
            throw ComoDrf.noEsDiccionario(datos);
        }
        Map<String, List<String>> errores = new LinkedHashMap<>();
        // validate_nombres / validate_apellidos (DatosPersonalesMixin): después de los validadores del campo.
        String nombres = ComoDrf.campo(datos, "nombres", errores,
            valor -> ComoDrf.nombreDePersona(ComoDrf.textoRecortado(valor, 100, false, null, List.of())));
        String apellidos = ComoDrf.campo(datos, "apellidos", errores,
            valor -> ComoDrf.nombreDePersona(ComoDrf.textoRecortado(valor, 100, false, null, List.of())));
        // Validadores del modelo (RegexValidator y MaxLengthValidator de Django) y UniqueValidator, en ese orden.
        String dni = ComoDrf.campo(datos, "dni", errores, valor -> ComoDrf.textoRecortado(valor, 8, false, null, List.of(
            texto -> DNI.matcher(texto).find() ? null : "El DNI debe tener 8 dígitos.",
            texto -> ComoDrf.largo(texto) > 8
                ? "Ensure this value has at most 8 characters (it has " + ComoDrf.largo(texto) + ")." : null,
            texto -> dniEnUso(texto) ? "Ya existe un prestador con ese DNI." : null)));
        String telefono = ComoDrf.opcional(datos, "telefono", "", errores,
            valor -> ComoDrf.telefonoPeruano(ComoDrf.textoRecortado(valor, 20, true, null, List.of())));
        List<Long> rubros = ComoDrf.campo(datos, "rubros", errores, this::rubros);
        BigDecimal latitud = ComoDrf.campo(datos, "latitud", errores, valor -> ComoDrf.coordenada(valor, 90));
        BigDecimal longitud = ComoDrf.campo(datos, "longitud", errores, valor -> ComoDrf.coordenada(valor, 180));
        Integer radio = ComoDrf.campo(datos, "radio_cobertura_km", errores, valor -> ComoDrf.entero(valor, 1, 100));
        if (!errores.isEmpty()) {
            throw new ErrorValidacion(errores);
        }
        return new DatosPrestador(nombres, apellidos, dni, telefono, rubros, latitud, longitud, radio);
    }

    // PrimaryKeyRelatedField(many=True, allow_empty=False): se detiene en el primer rubro inválido.
    private List<Long> rubros(JsonNode valor) throws ComoDrf.Invalido {
        List<JsonNode> elementos = new ArrayList<>();
        if (valor.isArray()) {
            valor.forEach(elementos::add);
        } else if (valor.isObject()) {
            // Python recorre las claves de un diccionario.
            valor.propertyNames().forEach(clave -> elementos.add(StringNode.valueOf(clave)));
        } else {
            throw new ComoDrf.Invalido("Expected a list of items but got type \"" + ComoDrf.tipoDeDato(valor) + "\".");
        }
        if (elementos.isEmpty()) {
            throw new ComoDrf.Invalido("Elige al menos un rubro.");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode elemento : elementos) {
            if (elemento.isNull()) {
                throw new ComoDrf.Invalido("Invalid pk \"None\" - object does not exist.");
            }
            long id = ComoDrf.clavePrimaria(elemento);
            Integer existe = jdbc.queryForObject("select count(*) from prestadores_rubro where id = ?", Integer.class, id);
            if (existe == null || existe == 0) {
                throw new ComoDrf.Invalido(ComoDrf.noExiste(elemento));
            }
            ids.add(id);
        }
        return ids;
    }

    private boolean dniEnUso(String dni) {
        Integer total = jdbc.queryForObject("select count(*) from prestadores_prestador where dni = ?", Integer.class, dni);
        return total != null && total > 0;
    }

    // create_user(username, email) sin contraseña: make_password(None) deja «!» y 40 caracteres al azar.
    private long crearUsuario(String usuario, String correo, String nombre) {
        StringBuilder clave = new StringBuilder("!");
        for (int i = 0; i < 40; i++) {
            clave.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
        }
        Object ahora = fechas.parametro(FechasDjango.ahora());
        GeneratedKeyHolder llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement sentencia = conexion.prepareStatement("""
                insert into auth_user (password, last_login, is_superuser, username, first_name, last_name, email,
                  is_staff, is_active, date_joined)
                values (?, null, ?, ?, ?, '', ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            sentencia.setString(1, clave.toString());
            sentencia.setBoolean(2, false);
            sentencia.setString(3, Normalizer.normalize(usuario, Normalizer.Form.NFKC));
            sentencia.setString(4, nombre);
            sentencia.setString(5, normalizarCorreo(correo));
            sentencia.setBoolean(6, false);
            sentencia.setBoolean(7, true);
            sentencia.setObject(8, ahora);
            return sentencia;
        }, llave);
        return id(llave);
    }

    /** {@code BaseUserManager.normalize_email}: el dominio en minúsculas. */
    static String normalizarCorreo(String correo) {
        String limpio = ComoDrf.stripPython(correo);
        int arroba = limpio.lastIndexOf('@');
        if (arroba < 0) {
            return correo;
        }
        return limpio.substring(0, arroba) + "@" + limpio.substring(arroba + 1).toLowerCase(Locale.ROOT);
    }

    // Falsos en Python: "", 0, 0.0, false, [] y {}.
    private static boolean verdadero(JsonNode valor) {
        if (valor.isNull()) {
            return false;
        }
        if (valor.isBoolean()) {
            return valor.booleanValue();
        }
        if (valor.isNumber()) {
            return valor.doubleValue() != 0;
        }
        if (valor.isString()) {
            return !valor.asString().isEmpty();
        }
        return !valor.isEmpty();
    }

    private static String primeros(String texto, int caracteres) {
        return ComoDrf.largo(texto) <= caracteres ? texto : texto.substring(0, texto.offsetByCodePoints(0, caracteres));
    }

    private static long id(GeneratedKeyHolder llave) {
        Map<String, Object> claves = llave.getKeyList().get(0);
        Object id = claves.containsKey("id") ? claves.get("id") : claves.values().iterator().next();
        return ((Number) id).longValue();
    }
}
