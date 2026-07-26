package com.tavern.lite.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * 原子地切换默认角色：清除旧默认 + 设置新默认在同一事务提交。
     * 避免两次独立写入之间被进程杀死留下零默认，或并发调用留下双默认。
     */
    @Transaction
    suspend fun switchDefault(id: Long) {
        clearAllDefaults()
        setDefault(id)
    }

    // Character-Persona junction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkCharacterPersona(link: CharacterPersonaEntity)

    @Query("DELETE FROM character_personas WHERE character_id = :characterId")
    suspend fun unlinkCharacterPersona(characterId: Long)

    /**
     * 原子地重绑角色的用户角色：解绑旧 + 绑定新在同一事务提交。
     * 避免半途失败只提交 DELETE 而丢失绑定。
     */
    @Transaction
    suspend fun relinkCharacter(characterId: Long, personaId: Long) {
        unlinkCharacterPersona(characterId)
        linkCharacterPersona(CharacterPersonaEntity(characterId, personaId))
    }

    @Query("SELECT persona_id FROM character_personas WHERE character_id = :characterId LIMIT 1")
    suspend fun getLinkedPersonaId(characterId: Long): Long?

    @Query("SELECT * FROM character_personas ORDER BY character_id ASC, persona_id ASC")
    suspend fun getAllCharacterPersonas(): List<CharacterPersonaEntity>

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
