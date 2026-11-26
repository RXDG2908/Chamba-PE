package pe.tecsup.chambape.data.mapper

import pe.tecsup.chambape.data.local.PrestadorEntity
import pe.tecsup.chambape.data.local.RubroEntity
import pe.tecsup.chambape.data.remote.PrestadorDto
import pe.tecsup.chambape.data.remote.RubroDto
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.domain.model.TipoError

/** Dto -> Entity -> modelo. Un campo obligatorio ausente se trata como respuesta inválida. */

private fun invalido(): Nothing = throw ErrorDatos(TipoError.DATOS_INVALIDOS)

fun RubroDto.aEntity(): RubroEntity =
    RubroEntity(id = id ?: invalido(), nombre = nombre?.takeIf { it.isNotBlank() } ?: invalido())

fun RubroEntity.aModelo(): Rubro = Rubro(id = id, nombre = nombre)

fun PrestadorDto.aEntity(rubroConsultaId: Int, favorito: Boolean): PrestadorEntity {
    val lat = latitud ?: invalido()
    val lon = longitud ?: invalido()
    if (!lat.isFinite() || !lon.isFinite()) invalido()
    return PrestadorEntity(
        id = id ?: invalido(),
        nombres = nombres ?: invalido(),
        apellidos = apellidos.orEmpty(),
        rubros = rubros.orEmpty().mapNotNull { it.nombre }.joinToString("\n"),
        latitud = lat,
        longitud = lon,
        distanciaKm = distanciaKm?.takeIf { it.isFinite() },
        rubroConsultaId = rubroConsultaId,
        favorito = favorito,
        calificacionPromedio = calificacionPromedio?.takeIf { it.isFinite() && it in 1.0..5.0 },
        calificacionesTotal = (calificacionesTotal ?: 0).coerceAtLeast(0),
    )
}

fun PrestadorEntity.aModelo(): Prestador =
    Prestador(
        id = id,
        nombres = nombres,
        apellidos = apellidos,
        rubros = rubros.split("\n").filter { it.isNotBlank() },
        latitud = latitud,
        longitud = longitud,
        distanciaKm = distanciaKm,
        favorito = favorito,
        calificacionPromedio = calificacionPromedio,
        calificacionesTotal = calificacionesTotal,
    )
