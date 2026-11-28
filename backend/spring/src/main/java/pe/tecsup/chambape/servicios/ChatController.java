package pe.tecsup.chambape.servicios;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import pe.tecsup.chambape.comun.ComoDrf;
import pe.tecsup.chambape.comun.CuerpoComoDrf;
import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.comun.FechasDjango;
import pe.tecsup.chambape.comun.RespuestaError;
import pe.tecsup.chambape.usuarios.Cuentas;
import pe.tecsup.chambape.usuarios.FiltroFirebase;

/**
 * Chat de la solicitud, igual que {@code servicios/chat.py} de Django: el cliente dueño y el prestador
 * asignado coordinan desde Confirmado; se escribe hasta En servicio y al quedar Finalizada queda solo para
 * leer. Para cualquier otro es 404. La tabla {@code servicios_mensaje} la crea la migración de Django.
 */
@RestController
@RequestMapping("/api/servicios/solicitudes")
public class ChatController {

    static final Set<String> ACTIVOS = Set.of("aceptada", "en_camino", "en_servicio");
    static final Map<String, String> SIN_CHAT = Map.of(
        "pendiente", "El chat se abre cuando el prestador acepta la solicitud.",
        "rechazada", "La solicitud fue rechazada y no tiene chat.");
    static final String SOLO_LECTURA = "El servicio ya finalizó; el chat queda solo para leer.";

    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;

    public ChatController(JdbcTemplate jdbc, FechasDjango fechas) {
        this.jdbc = jdbc;
        this.fechas = fechas;
    }

    private record Chat(long id, String estado, Long clienteId, String clienteNombre, String clienteTelefono,
                        long prestadorUsuarioId, String nombres, String apellidos, String tipo,
                        String nombreComercial, String telefono) {

        boolean activo() {
            return ACTIVOS.contains(estado);
        }

        // Prestador.nombre_publico de Django.
        String nombrePublico() {
            if ("empresa".equals(tipo) && nombreComercial != null && !nombreComercial.isEmpty()) {
                return nombreComercial;
            }
            return ComoDrf.stripPython(nombres + " " + apellidos);
        }

        String de(long autor) {
            return autor == prestadorUsuarioId ? "prestador" : "cliente";
        }
    }

    @GetMapping("/{id:\\d+}/chat/")
    public ResponseEntity<Map<String, Object>> leer(@PathVariable long id, HttpServletRequest peticion) {
        Cuentas.Usuario usuario = FiltroFirebase.usuario(peticion).orElseThrow(RespuestaError::sinCredenciales);
        Chat chat = deLasPartes(id, usuario);
        boolean soyPrestador = chat.prestadorUsuarioId() == usuario.id();
        Map<String, Object> contraparte = new LinkedHashMap<>();
        if (soyPrestador) {
            contraparte.put("nombre", chat.clienteNombre());
            contraparte.put("telefono", chat.clienteTelefono());
        } else {
            // El cliente ve el teléfono del prestador solo mientras el chat está activo.
            contraparte.put("nombre", chat.nombrePublico());
            boolean conTelefono = chat.activo() && chat.telefono() != null && !chat.telefono().isEmpty();
            contraparte.put("telefono", conTelefono ? chat.telefono() : null);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("solicitud", chat.id());
        cuerpo.put("estado", chat.estado());
        cuerpo.put("estado_display", Solicitudes.ESTADOS.get(chat.estado()));
        cuerpo.put("activo", chat.activo());
        cuerpo.put("yo", soyPrestador ? "prestador" : "cliente");
        cuerpo.put("contraparte", contraparte);
        cuerpo.put("mensajes", mensajes(chat));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(cuerpo);
    }

    @PostMapping("/{id:\\d+}/chat/")
    public ResponseEntity<Map<String, Object>> enviar(@PathVariable long id, HttpServletRequest peticion) {
        Cuentas.Usuario usuario = FiltroFirebase.usuario(peticion).orElseThrow(RespuestaError::sinCredenciales);
        Chat chat = deLasPartes(id, usuario);
        if (!chat.activo()) {
            throw new RespuestaError(409, Map.of("chat", SOLO_LECTURA));
        }
        JsonNode datos = CuerpoComoDrf.leer(peticion);
        if (!datos.isObject()) {
            throw ComoDrf.noEsDiccionario(datos);
        }
        Map<String, List<String>> errores = new LinkedHashMap<>();
        String texto = ComoDrf.campo(datos, "texto", errores, ChatController::texto);
        if (!errores.isEmpty()) {
            throw new ErrorValidacion(errores);
        }
        Instant enviado = FechasDjango.ahora();
        GeneratedKeyHolder llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            var sentencia = conexion.prepareStatement(
                "insert into servicios_mensaje (solicitud_id, autor_id, texto, enviado) values (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            sentencia.setLong(1, chat.id());
            sentencia.setLong(2, usuario.id());
            sentencia.setString(3, texto);
            sentencia.setObject(4, fechas.parametro(enviado));
            return sentencia;
        }, llave);
        Map<String, Object> claves = llave.getKeyList().get(0);
        Object nuevo = claves.containsKey("id") ? claves.get("id") : claves.values().iterator().next();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mensaje(((Number) nuevo).longValue(), chat.de(usuario.id()), texto, enviado));
    }

    /**
     * NuevoMensajeSerializer de Django: CharField(max_length=500, trim_whitespace=False) junta los
     * mensajes de sus validadores (largo, caracteres nulos y sustitutos); después, validate_texto.
     */
    static String texto(JsonNode valor) throws ComoDrf.Invalido {
        if (valor.isBoolean() || !(valor.isString() || valor.isNumber())) {
            throw new ComoDrf.Invalido("Not a valid string.");
        }
        String texto = ComoDrf.comoTextoPython(valor);
        if (texto.isEmpty()) {
            throw new ComoDrf.Invalido("Escribe un mensaje.");
        }
        List<String> mensajes = new ArrayList<>();
        if (ComoDrf.largo(texto) > 500) {
            mensajes.add("Ensure this field has no more than 500 characters.");
        }
        if (texto.indexOf('\0') >= 0) {
            mensajes.add("Null characters are not allowed.");
        }
        texto.codePoints().filter(c -> c >= 0xD800 && c <= 0xDFFF).findFirst().ifPresent(c ->
            mensajes.add(String.format("Surrogate characters are not allowed: U+%X.", c)));
        if (!mensajes.isEmpty()) {
            throw new ComoDrf.Invalido(mensajes);
        }
        if (ComoDrf.soloEspacios(texto)) {
            throw new ComoDrf.Invalido("Escribe un mensaje; no uses solo espacios.");
        }
        return texto;
    }

    /** La solicitud si quien llama es una de sus partes (si no, 404) y si ya tiene chat (si no, 409). */
    private Chat deLasPartes(long id, Cuentas.Usuario usuario) {
        Optional<Chat> encontrada = jdbc.query("""
            select s.id, s.estado, s.cliente_id, s.cliente_nombre, s.cliente_telefono, p.usuario_id, p.nombres,
                   p.apellidos, p.tipo, p.nombre_comercial, p.telefono
            from servicios_solicitud s join prestadores_prestador p on p.id = s.prestador_id
            where s.id = ? and (s.cliente_id = ? or p.usuario_id = ?)""",
            (fila, n) -> new Chat(fila.getLong("id"), fila.getString("estado"),
                fila.getObject("cliente_id") == null ? null : fila.getLong("cliente_id"),
                fila.getString("cliente_nombre"), fila.getString("cliente_telefono"), fila.getLong("usuario_id"),
                fila.getString("nombres"), fila.getString("apellidos"), fila.getString("tipo"),
                fila.getString("nombre_comercial"), fila.getString("telefono")),
            id, usuario.id(), usuario.id()).stream().findFirst();
        Chat chat = encontrada.orElseThrow(RespuestaError::noEncontrada);
        if (SIN_CHAT.containsKey(chat.estado())) {
            throw new RespuestaError(409, Map.of("chat", SIN_CHAT.get(chat.estado())));
        }
        return chat;
    }

    private List<Map<String, Object>> mensajes(Chat chat) {
        List<Map<String, Object>> lista = new ArrayList<>();
        jdbc.query("select id, autor_id, texto, enviado from servicios_mensaje where solicitud_id = ? order by enviado, id",
            (java.sql.ResultSet fila) -> {
                lista.add(mensaje(fila.getLong("id"), chat.de(fila.getLong("autor_id")), fila.getString("texto"),
                    FechasDjango.leer(fila.getObject("enviado"))));
            }, chat.id());
        return lista;
    }

    private static Map<String, Object> mensaje(long id, String de, String texto, Instant enviado) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", id);
        cuerpo.put("de", de);
        cuerpo.put("texto", texto);
        cuerpo.put("enviado", FechasDjango.paraApi(enviado));
        return cuerpo;
    }
}
