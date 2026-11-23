package pe.tecsup.chambape.prestadores;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    /** Candidatos de la búsqueda (HU-3 y HU-9): disponibles, verificados y con el rubro pedido. */
    @Query("""
        select p from Prestador p
        where p.disponible = true and p.estado = :estado
          and exists (select 1 from p.rubros r where r.id = :rubro)
        order by p.id
        """)
    List<Prestador> candidatos(@Param("rubro") long rubro, @Param("estado") String estado);
}
