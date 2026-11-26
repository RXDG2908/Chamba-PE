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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    /** HU-12: promedio de 1 a 5 estrellas y cuántas calificaciones lo forman. */
    val calificacionPromedio: Double? = null,
    val calificacionesTotal: Int = 0,
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

/** Versión 2: el prestador guarda su calificación (HU-12). La caché conserva favoritos al migrar. */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE prestadores ADD COLUMN calificacionPromedio REAL")
        db.execSQL("ALTER TABLE prestadores ADD COLUMN calificacionesTotal INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [RubroEntity::class, PrestadorEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rubroDao(): RubroDao
    abstract fun prestadorDao(): PrestadorDao

    companion object {
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "chambape.db")
                .addMigrations(MIGRACION_1_2)
                .build()
    }
}
