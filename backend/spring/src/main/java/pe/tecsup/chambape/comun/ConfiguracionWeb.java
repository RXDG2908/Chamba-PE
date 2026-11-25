package pe.tecsup.chambape.comun;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS como en Django (CORS_ALLOW_ALL_ORIGINS = True): la web de usuario puede llamar a la API
 * desde otro puerto u otro dominio. Por ahora la API de Spring solo tiene lecturas públicas,
 * así que se permiten GET sin cookies. Revisar al agregar rutas con sesión.
 */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/**").allowedOrigins("*").allowedMethods("GET", "HEAD");
    }
}
