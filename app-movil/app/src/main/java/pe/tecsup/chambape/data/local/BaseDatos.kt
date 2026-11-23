package pe.tecsup.chambape.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rubros")
data class RubroEntity(
    @PrimaryKey val id: Int,
    val nombre: String,
)

/** Prestador guardado en el celular: última búsqueda por rubro y favoritos. */
@Entity(tableName = "prestadores")
data class PrestadorEntity(
    @PrimaryKey val id: Int,
    val nombres: String,
    val apellidos: String,
    /** Nombres de rubros separados por salto de línea. */
    val rubros: String,
    val latitud: Double,
    val longitud: Double,
    val distanciaKm: Double?,
    /** Rubro de la búsqueda que lo trajo; sirve para mostrarla sin conexión. */
    val rubroConsultaId: Int,
    val favorito: Boolean,
)

@Dao
interface RubroDao {
    @Query("SELECT * FROM rubros ORDER BY nombre")
    suspend fun todos(): List<RubroEntity>

    @Upsert
    suspend fun guardar(rubros: List<RubroEntity>)

    @Query("DELETE FROM rubros")
    suspend fun borrarTodos()
}

@Dao
interface PrestadorDao {
    @Query("SELECT * FROM prestadores WHERE rubroConsultaId = :rubroId ORDER BY distanciaKm")
    suspend fun porRubro(rubroId: Int): List<PrestadorEntity>

    @Query("SELECT * FROM prestadores WHERE id = :id")
    suspend fun porId(id: Int): PrestadorEntity?

    @Query("SELECT * FROM prestadores WHERE favorito = 1 ORDER BY nombres, apellidos")
    fun favoritos(): Flow<List<PrestadorEntity>>

    @Query("SELECT id FROM prestadores WHERE favorito = 1")
    suspend fun idsFavoritos(): List<Int>

    @Upsert
    suspend fun guardar(prestadores: List<PrestadorEntity>)

    /** Quita la búsqueda anterior del rubro, pero conserva los favoritos. */
    @Query("DELETE FROM prestadores WHERE rubroConsultaId = :rubroId AND favorito = 0")
    suspend fun borrarBusqueda(rubroId: Int)

    @Query("UPDATE prestadores SET favorito = :favorito WHERE id = :id")
    suspend fun marcarFavorito(id: Int, favorito: Boolean)
}

@Database(entities = [RubroEntity::class, PrestadorEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rubroDao(): RubroDao
    abstract fun prestadorDao(): PrestadorDao

    companion object {
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "chambape.db").build()
    }
}
