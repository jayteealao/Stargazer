package uk.adedamola.stargazer.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "search_presets")
data class SearchPreset(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val sortBy: String, // "stars", "forks", "updated", "created", "name"
    val filterLanguage: String?,
    val filterMinStars: Int?,
    val filterMaxStars: Int?,
    val filterHasDescription: Boolean = false,
    val filterHasHomepage: Boolean = false,
    val filterHasTopics: Boolean = false,
    val filterFavoritesOnly: Boolean = false,
    val filterPinnedOnly: Boolean = false,
    val searchQuery: String?,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SearchPresetDao {
    @Query("SELECT * FROM search_presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<SearchPreset>>

    @Query("SELECT * FROM search_presets WHERE id = :id")
    suspend fun getPresetById(id: Int): SearchPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: SearchPreset): Long

    @Delete
    suspend fun deletePreset(preset: SearchPreset)

    @Query("DELETE FROM search_presets WHERE id = :id")
    suspend fun deletePresetById(id: Int)
}
