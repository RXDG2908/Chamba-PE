package pe.tecsup.chambape.prestadores;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    /**
     * Candidatos de la búsqueda (HU-3 y HU-9): disponibles, verificados y con el rubro pedido.
     * Los rubros de cada uno vienen en la misma consulta (como prefetch_related en Django):
     * sin eso serían 1 + N consultas, una por prestador.
     */
    @EntityGraph(attributePaths = "rubros")
    @Query("""
        select p from Prestador p
        where p.disponible = true and p.estado = :estado
          and exists (select 1 from p.rubros r where r.id = :rubro)
        order by p.id
        """)
    List<Prestador> candidatos(@Param("rubro") long rubro, @Param("estado") String estado);
}
