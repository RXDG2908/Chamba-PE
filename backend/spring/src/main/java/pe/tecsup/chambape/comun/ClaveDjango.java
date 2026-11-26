package pe.tecsup.chambape.comun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La SECRET_KEY de Django, resuelta igual que en {@code core/settings.py}: DJANGO_SECRET_KEY o, con
 * DJANGO_DEBUG=true y sin clave, la misma clave de desarrollo local. Con ella Spring lee la sesión de
 * la web y firma el código de seguimiento como Django. Sin clave (y sin debug) queda vacía.
 */
@Component
public class ClaveDjango {

    /** La de {@code core/settings.py}; solo sirve en desarrollo local. */
    static final String DESARROLLO = "django-insecure-solo-desarrollo-local-no-usar-en-produccion";

    private final String valor;

    public ClaveDjango(@Value("${chambape.django.secret-key:}") String clave,
                       @Value("${chambape.django.debug:false}") boolean debug) {
        this.valor = !clave.isEmpty() ? clave : debug ? DESARROLLO : "";
    }

    public String valor() {
        return valor;
    }
}
