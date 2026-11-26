package pe.tecsup.chambape.usuarios;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifica el token de identidad de Firebase de la app, como {@code usuarios/autenticacion.py}:
 * firma RS256 con las claves públicas de Google, que no esté vencido, que sea de nuestro proyecto
 * (audiencia) y que lo haya emitido Firebase Authentication. No necesita credenciales.
 */
@Component
public class VerificadorFirebase {

    static final String CLAVES_GOOGLE =
        "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private static final Logger LOG = LoggerFactory.getLogger(VerificadorFirebase.class);

    private final String proyecto;
    private final DefaultJWTProcessor<SecurityContext> procesador;

    @Autowired
    public VerificadorFirebase(@Value("${chambape.firebase.proyecto:}") String proyecto,
                               @Value("${chambape.firebase.claves:" + CLAVES_GOOGLE + "}") String claves)
        throws MalformedURLException {
        this(proyecto, JWKSourceBuilder.create(URI.create(claves).toURL()).build());
    }

    /** Con otra fuente de claves (las pruebas firman sus propios tokens). */
    public VerificadorFirebase(String proyecto, JWKSource<SecurityContext> claves) {
        this.proyecto = proyecto;
        this.procesador = new DefaultJWTProcessor<>();
        procesador.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, claves));
        procesador.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
            proyecto,
            new JWTClaimsSet.Builder().issuer("https://securetoken.google.com/" + proyecto).build(),
            Set.of("sub", "exp", "iat")));
    }

    public boolean configurado() {
        return !proyecto.isEmpty();
    }

    /** UID de Firebase del token, o vacío si no es válido para este proyecto. */
    public Optional<String> uid(String token) {
        if (!configurado()) {
            return Optional.empty();
        }
        try {
            String sujeto = procesador.process(token, null).getSubject();
            return sujeto == null || sujeto.isEmpty() ? Optional.empty() : Optional.of(sujeto);
        } catch (Exception error) {
            // Se registra el motivo, nunca el token.
            LOG.warn("Token de Firebase rechazado: {}", error.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
