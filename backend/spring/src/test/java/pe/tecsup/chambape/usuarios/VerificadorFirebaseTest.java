package pe.tecsup.chambape.usuarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.Optional;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** La verificación real del token (firma, vencimiento, proyecto y emisor) con claves propias. */
class VerificadorFirebaseTest {

    private static final String PROYECTO = "proyecto-de-prueba";
    private static RSAKey google;
    private static RSAKey otra;
    private static VerificadorFirebase verificador;

    @BeforeAll
    static void claves() throws Exception {
        google = new RSAKeyGenerator(2048).keyID("clave-google").generate();
        otra = new RSAKeyGenerator(2048).keyID("clave-google").generate();
        verificador = new VerificadorFirebase(PROYECTO, new ImmutableJWKSet<>(new JWKSet(google.toPublicJWK())));
    }

    private static String token(RSAKey clave, String audiencia, String emisor, long segundosParaVencer, String sujeto)
        throws Exception {
        long ahora = System.currentTimeMillis();
        JWTClaimsSet.Builder datos = new JWTClaimsSet.Builder().audience(audiencia).issuer(emisor)
            .issueTime(new Date(ahora - 60_000)).expirationTime(new Date(ahora + segundosParaVencer * 1000));
        if (sujeto != null) {
            datos.subject(sujeto);
        }
        SignedJWT firmado = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID())
            .type(JOSEObjectType.JWT).build(), datos.build());
        firmado.sign(new RSASSASigner(clave));
        return firmado.serialize();
    }

    private static final String EMISOR = "https://securetoken.google.com/" + PROYECTO;

    @Test
    void tokenValidoDaElUid() throws Exception {
        assertEquals(Optional.of("uid-ana"), verificador.uid(token(google, PROYECTO, EMISOR, 3600, "uid-ana")));
    }

    @Test
    void rechazaLoQueDjangoRechaza() throws Exception {
        assertFalse(verificador.uid(token(google, PROYECTO, EMISOR, -120, "uid-ana")).isPresent(), "vencido");
        assertFalse(verificador.uid(token(google, "otro-proyecto", EMISOR, 3600, "uid-ana")).isPresent(), "audiencia");
        assertFalse(verificador.uid(token(google, PROYECTO, "https://securetoken.google.com/otro", 3600, "uid-ana"))
            .isPresent(), "emisor");
        assertFalse(verificador.uid(token(otra, PROYECTO, EMISOR, 3600, "uid-ana")).isPresent(), "firmado con otra clave");
        assertFalse(verificador.uid(token(google, PROYECTO, EMISOR, 3600, null)).isPresent(), "sin usuario");
        String sinFirma = new PlainJWT(new JWTClaimsSet.Builder().audience(PROYECTO).issuer(EMISOR).subject("uid-ana")
            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000)).issueTime(new Date()).build()).serialize();
        assertFalse(verificador.uid(sinFirma).isPresent(), "alg none");
        assertFalse(verificador.uid("no-es-un-token").isPresent());
    }

    @Test
    void sinProyectoConfiguradoNoAceptaNada() throws Exception {
        VerificadorFirebase sinProyecto = new VerificadorFirebase("", new ImmutableJWKSet<>(new JWKSet(google.toPublicJWK())));
        assertFalse(sinProyecto.configurado());
        assertFalse(sinProyecto.uid(token(google, "", "https://securetoken.google.com/", 3600, "uid-ana")).isPresent());
        assertTrue(verificador.configurado());
    }
}
