package pe.tecsup.chambape.usuarios;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.tecsup.chambape.comun.CuerpoComoDrf;
import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.comun.RespuestaError;

/**
 * Registro desde la app móvil, como {@code RegistroClienteMovilView} y {@code RegistroPrestadorMovilView}
 * de Django. Quien llama demuestra con su token de Firebase que es dueño de una cuenta todavía sin
 * vincular; se le crea una cuenta nueva. Nunca se vincula una cuenta existente: eso lo hace un administrador.
 */
@RestController
public class RegistroMovilController {

    public static final String CLIENTE = "/api/usuarios/registro-movil/";
    public static final String PRESTADOR = "/api/prestadores/registro-movil/";

    private static final String CORREO_REPETIDO =
        "Ya existe una cuenta con ese correo. Pide a administración que la vincule con la app.";

    private final VerificadorFirebase verificador;
    private final RegistroMovil registro;

    public RegistroMovilController(VerificadorFirebase verificador, RegistroMovil registro) {
        this.verificador = verificador;
        this.registro = registro;
    }

    /** Idempotente: si el UID ya es de un cliente, devuelve sus datos (200). */
    @PostMapping(CLIENTE)
    public ResponseEntity<Map<String, Object>> cliente(HttpServletRequest peticion) {
        VerificadorFirebase.Identidad identidad = identidad(peticion);
        Optional<RegistroMovil.Vinculada> vinculada = registro.vinculada(identidad.uid());
        if (vinculada.isPresent()) {
            if (!Cuentas.CLIENTE.equals(vinculada.get().rol())) {
                throw RespuestaError.detalle(409, "Esta cuenta de la app no es de cliente.");
            }
            return ResponseEntity.ok(registro.usuarioActual(vinculada.get().usuarioId()));
        }
        String correo = identidad.correo() == null ? "" : identidad.correo().strip();
        if (!correo.isEmpty() && registro.correoEnUso(correo)) {
            throw new ErrorValidacion(Map.of("email", List.of(CORREO_REPETIDO)));
        }
        long id = registro.crearCliente(identidad, correo, CuerpoComoDrf.leer(peticion));
        return ResponseEntity.status(HttpStatus.CREATED).body(registro.usuarioActual(id));
    }

    @PostMapping(PRESTADOR)
    public ResponseEntity<Map<String, Object>> prestador(HttpServletRequest peticion) {
        VerificadorFirebase.Identidad identidad = identidad(peticion);
        if (registro.vinculada(identidad.uid()).isPresent()) {
            throw RespuestaError.detalle(409, "Esta cuenta de la app ya está registrada.");
        }
        String correo = identidad.correo() == null ? "" : identidad.correo();
        if (!correo.isEmpty() && registro.correoEnUso(correo)) {
            throw new ErrorValidacion(Map.of("email", List.of(CORREO_REPETIDO)));
        }
        Map<String, Object> creado = registro.crearPrestador(identidad, correo, CuerpoComoDrf.leer(peticion));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // token_de() y datos_del_token() de Django: 403 si falta, está mal formado o no es válido.
    private VerificadorFirebase.Identidad identidad(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        String[] partes = cabecera == null ? new String[0] : cabecera.strip().split("[ \\t\\n\\x0B\\f\\r]+");
        boolean bienFormada = partes.length == 2 && partes[0].equalsIgnoreCase("bearer")
            && partes[1].chars().allMatch(c -> c < 128);
        return (bienFormada ? verificador.identidad(partes[1]) : Optional.<VerificadorFirebase.Identidad>empty())
            .orElseThrow(() -> RespuestaError.detalle(403, "El token no es válido o venció."));
    }
}
