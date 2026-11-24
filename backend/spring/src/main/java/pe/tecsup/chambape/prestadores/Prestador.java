package pe.tecsup.chambape.prestadores;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Tabla de Django {@code prestadores_prestador}; Spring solo la lee.
 * Solo se mapean las columnas que usa la búsqueda: DNI, teléfono y cuenta no salen de aquí.
 */
@Entity
@Immutable
@Table(name = "prestadores_prestador")
public class Prestador {

    public static final String VERIFICADO = "verificado";

    @Id
    private Long id;

    private String nombres;

    private String apellidos;

    // Centro del área de cobertura.
    private BigDecimal latitud;

    private BigDecimal longitud;

    @Column(name = "radio_cobertura_km")
    private Integer radioCoberturaKm;

    private Boolean disponible;

    private String estado;

    // HU-12: los mantiene Django al guardar o borrar calificaciones. Promedio nulo = sin calificaciones.
    @Column(name = "calificacion_promedio")
    private BigDecimal calificacionPromedio;

    @Column(name = "calificaciones_total")
    private Integer calificacionesTotal;

    @ManyToMany
    @JoinTable(
        name = "prestadores_prestador_rubros",
        joinColumns = @JoinColumn(name = "prestador_id"),
        inverseJoinColumns = @JoinColumn(name = "rubro_id"))
    @OrderBy("nombre")
    private List<Rubro> rubros = new ArrayList<>();

    protected Prestador() {
    }

    public Long getId() {
        return id;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public Integer getRadioCoberturaKm() {
        return radioCoberturaKm;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public String getEstado() {
        return estado;
    }

    public BigDecimal getCalificacionPromedio() {
        return calificacionPromedio;
    }

    public Integer getCalificacionesTotal() {
        return calificacionesTotal;
    }

    public List<Rubro> getRubros() {
        return rubros;
    }
}
