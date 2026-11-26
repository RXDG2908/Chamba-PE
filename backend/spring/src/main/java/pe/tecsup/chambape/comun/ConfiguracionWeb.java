package pe.tecsup.chambape.comun;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS como en Django ({@code CORS_ALLOW_ALL_ORIGINS = True}, con los mismos métodos y cabeceras y sin
 * {@code CORS_ALLOW_CREDENTIALS}): cualquier origen puede llamar a la API, pero el navegador no le
 * entrega respuestas hechas con la sesión de otra persona. Las escrituras con sesión además pasan por el
 * CSRF de {@code CsrfDjango}, igual que en Django. Spring no debe rechazar el Origin de la web: tras el
 * proxy de Vite llega como {@code http://localhost:5173} aunque el Host sea el de Spring.
 */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/**").allowedOrigins("*")
            .allowedMethods("DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT")
            .allowedHeaders("accept", "accept-encoding", "authorization", "content-type", "dnt", "origin", "user-agent",
                "x-csrftoken", "x-requested-with");
    }
}
