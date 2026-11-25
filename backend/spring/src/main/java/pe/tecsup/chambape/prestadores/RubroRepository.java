package pe.tecsup.chambape.prestadores;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RubroRepository extends JpaRepository<Rubro, Long> {

    /** Mismo orden que {@code Rubro.Meta.ordering} de Django. */
    List<Rubro> findAllByOrderByNombreAsc();
}
