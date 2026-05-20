package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tavern.lite.data.db.entity.CharacterPersonaEntity
import com.tavern.lite.data.db.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {

    @Query("SELECT * FROM personas ORDER BY is_default DESC, created_at DESC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getPersonaById(id: Long): PersonaEntity?

    @Query("SELECT * FROM personas WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultPersona(): PersonaEntity?

    @Query("SELECT * FROM personas WHERE is_default = 1 LIMIT 1")
    fun getDefaultPersonaFlow(): Flow<PersonaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: PersonaEntity): Long

    @Query("UPDATE personas SET name = :name, biography = :biography, avatar_path = :avatarPath WHERE id = :id")
    suspend fun update(id: Long, name: String, biography: String, avatarPath: String?)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE personas SET is_default = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE personas SET is_default = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    // Character-Persona junction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkCharacterPersona(link: CharacterPersonaEntity)

    @Query("DELETE FROM character_personas WHERE character_id = :characterId")
    suspend fun unlinkCharacterPersona(characterId: Long)

    @Query("SELECT persona_id FROM character_personas WHERE character_id = :characterId LIMIT 1")
    suspend fun getLinkedPersonaId(characterId: Long): Long?

    @Query("""
        SELECT p.* FROM personas p
        INNER JOIN character_personas cp ON p.id = cp.persona_id
        WHERE cp.character_id = :characterId
        LIMIT 1
    """)
    suspend fun getLinkedPersona(characterId: Long): PersonaEntity?

    @Query("SELECT * FROM personas ORDER BY id ASC")
    suspend fun getAllPersonasSync(): List<PersonaEntity>
}
