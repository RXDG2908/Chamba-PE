package pe.tecsup.chambape.data.repository

import com.google.gson.JsonParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.tecsup.chambape.data.local.PrestadorDao
import pe.tecsup.chambape.data.local.RubroDao
import pe.tecsup.chambape.data.mapper.aEntity
import pe.tecsup.chambape.data.mapper.aModelo
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.domain.model.Datos
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.domain.model.TipoError
import retrofit2.HttpException
import java.io.IOException

/** Contrato que usan los ViewModel. Los fallos se lanzan como [ErrorDatos]. */
interface PrestadorRepository {
    suspend fun rubros(): Datos<List<Rubro>>
    suspend fun buscar(rubroId: Int, latitud: Double, longitud: Double): Datos<List<Prestador>>
    suspend fun prestador(id: Int): Prestador?
    fun favoritos(): Flow<List<Prestador>>
    suspend fun alternarFavorito(id: Int): Boolean
}

/**
 * Primero la API; lo descargado se guarda en Room. Sin conexión se devuelve lo guardado
 * (marcado como caché) y, si no hay nada, se lanza SIN_CONEXION.
 */
class PrestadorRepositoryImpl(
    private val api: ApiService,
    private val rubroDao: RubroDao,
    private val prestadorDao: PrestadorDao,
) : PrestadorRepository {

    override suspend fun rubros(): Datos<List<Rubro>> =
        try {
            val entidades = api.rubros().map { it.aEntity() }
            rubroDao.borrarTodos()
            rubroDao.guardar(entidades)
            Datos(entidades.sortedBy { it.nombre }.map { it.aModelo() }, desdeCache = false)
        } catch (e: IOException) {
            val guardados = rubroDao.todos()
            if (guardados.isEmpty()) throw ErrorDatos(TipoError.SIN_CONEXION, e)
            Datos(guardados.map { it.aModelo() }, desdeCache = true)
        } catch (e: HttpException) {
            throw ErrorDatos(TipoError.SERVIDOR, e)
        } catch (e: JsonParseException) {
            throw ErrorDatos(TipoError.DATOS_INVALIDOS, e)
        }

    override suspend fun buscar(rubroId: Int, latitud: Double, longitud: Double): Datos<List<Prestador>> =
        try {
            val favoritos = prestadorDao.idsFavoritos().toSet()
            // Se conserva el orden del servidor (por distancia): la app no reordena.
            val entidades = api.buscar(rubroId, latitud, longitud).map { dto ->
                dto.aEntity(rubroConsultaId = rubroId, favorito = dto.id in favoritos)
            }
            prestadorDao.borrarBusqueda(rubroId)
            prestadorDao.guardar(entidades)
            Datos(entidades.map { it.aModelo() }, desdeCache = false)
        } catch (e: IOException) {
            val guardados = prestadorDao.porRubro(rubroId)
            if (guardados.isEmpty()) throw ErrorDatos(TipoError.SIN_CONEXION, e)
            Datos(guardados.map { it.aModelo() }, desdeCache = true)
        } catch (e: HttpException) {
            throw ErrorDatos(TipoError.SERVIDOR, e)
        } catch (e: JsonParseException) {
            throw ErrorDatos(TipoError.DATOS_INVALIDOS, e)
        }

    override suspend fun prestador(id: Int): Prestador? = prestadorDao.porId(id)?.aModelo()

    override fun favoritos(): Flow<List<Prestador>> =
        prestadorDao.favoritos().map { lista -> lista.map { it.aModelo() } }

    override suspend fun alternarFavorito(id: Int): Boolean {
        val actual = prestadorDao.porId(id) ?: return false
        val nuevo = !actual.favorito
        prestadorDao.marcarFavorito(id, nuevo)
        return nuevo
    }
}
