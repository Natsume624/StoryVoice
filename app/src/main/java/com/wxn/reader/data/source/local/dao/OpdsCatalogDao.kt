package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wxn.reader.data.dto.OpdsCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpdsCatalogDao {

    @Query("SELECT * FROM opds_catalogs WHERE isEnabled = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllEnabledCatalogs(): Flow<List<OpdsCatalogEntity>>

    @Query("SELECT * FROM opds_catalogs ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllCatalogs(): Flow<List<OpdsCatalogEntity>>

    @Query("SELECT * FROM opds_catalogs WHERE id = :id")
    suspend fun getCatalogById(id: Long): OpdsCatalogEntity?

    @Query("SELECT * FROM opds_catalogs WHERE predefinedId = :predefinedId")
    suspend fun getCatalogByPredefinedId(predefinedId: String): OpdsCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalog(catalog: OpdsCatalogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogs(catalogs: List<OpdsCatalogEntity>)

    @Update
    suspend fun updateCatalog(catalog: OpdsCatalogEntity)

    @Query("UPDATE opds_catalogs SET isEnabled = 0 WHERE id = :id")
    suspend fun disableCatalog(id: Long)

    @Query("DELETE FROM opds_catalogs WHERE id = :id")
    suspend fun deleteCatalog(id: Long)

    @Query("SELECT COUNT(*) FROM opds_catalogs WHERE predefinedId = :predefinedId")
    suspend fun catalogExistsByPredefinedId(predefinedId: String): Int

    /**
     * 清理不再出现在预置名单中的推荐源（keepIds 为预置名单全量 id）。
     * 自定义目录（isPredefined = 0）与 predefinedId 为 NULL 的行不受影响。
     */
    @Query(
        "DELETE FROM opds_catalogs WHERE isPredefined = 1 " +
            "AND predefinedId IS NOT NULL AND predefinedId NOT IN (:keepIds)"
    )
    suspend fun deleteStalePredefinedCatalogs(keepIds: List<String>)
}
