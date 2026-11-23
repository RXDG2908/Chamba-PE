package pe.tecsup.chambape;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Back End de usuario de ChambaPE: lo consumen la app móvil y la web de usuario. */
@SpringBootApplication
public class ChambaPeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChambaPeApplication.class, args);
    }
}
