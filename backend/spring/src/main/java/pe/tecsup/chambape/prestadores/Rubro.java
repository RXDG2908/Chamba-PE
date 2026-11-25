package pe.tecsup.chambape.prestadores;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Tabla de Django {@code prestadores_rubro}; Spring solo la lee. */
@Entity
@Immutable
@Table(name = "prestadores_rubro")
public class Rubro {

    @Id
    private Long id;

    private String nombre;

    protected Rubro() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
