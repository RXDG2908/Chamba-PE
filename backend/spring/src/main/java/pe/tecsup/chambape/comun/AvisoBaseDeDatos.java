package pe.tecsup.chambape.comun;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Al arrancar dice qué base se abrió. Con SQLite la ruta es relativa a la carpeta desde donde
 * se arranca, y una ruta equivocada abre otra base sin avisar: aquí se ve la ruta completa.
 */
@Component
public class AvisoBaseDeDatos {

    private static final Logger LOG = LoggerFactory.getLogger(AvisoBaseDeDatos.class);

    private final Environment entorno;

    public AvisoBaseDeDatos(Environment entorno) {
        this.entorno = entorno;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void avisar() {
        String url = entorno.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:sqlite:")) {
            // Sin usuario ni clave: solo el tipo de base.
            LOG.info("Base de datos: {}", url.replaceFirst("^(jdbc:[a-z]+:).*", "$1..."));
            return;
        }
        Path archivo = rutaSqlite(url);
        LOG.info("Base de datos SQLite: {}{}", archivo, Files.exists(archivo) ? "" : " (NO EXISTE)");
    }

    static Path rutaSqlite(String url) {
        String ruta = url.substring("jdbc:sqlite:".length()).replaceFirst("^file:", "");
        int consulta = ruta.indexOf('?');
        return Path.of(consulta < 0 ? ruta : ruta.substring(0, consulta)).toAbsolutePath().normalize();
    }
}
