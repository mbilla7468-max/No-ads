package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Query("SELECT * FROM app_rules ORDER BY isGame DESC, appName ASC")
    fun getAllAppRulesFlow(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun getAppRule(pkg: String): AppRuleEntity?

    @Query("SELECT COUNT(*) FROM app_rules")
    suspend fun getAppCount(): Int

    @Query("SELECT SUM(adsBlockedCount) FROM app_rules")
    fun getTotalAdsBlockedFlow(): Flow<Int?>

    @Query("SELECT SUM(dataSavedKb) FROM app_rules")
    fun getTotalDataSavedKbFlow(): Flow<Long?>

    @Query("UPDATE app_rules SET isAdBlocked = :isBlocked WHERE packageName = :packageName")
    suspend fun updateAdBlockedStatus(packageName: String, isBlocked: Boolean)

    @Query("UPDATE app_rules SET isAdBlocked = :isBlocked")
    suspend fun setAllBlockedStatus(isBlocked: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<AppRuleEntity>)

    @Update
    suspend fun update(rule: AppRuleEntity)

    @Query("SELECT * FROM app_rules")
    suspend fun getAllAppRulesList(): List<AppRuleEntity>

    @Query("DELETE FROM app_rules WHERE packageName NOT IN (:packages)")
    suspend fun deletePackagesNotIn(packages: List<String>)

    @Query("UPDATE app_rules SET adsBlockedCount = 0, dataSavedKb = 0")
    suspend fun resetAllStats()

    @Query("UPDATE app_rules SET adsBlockedCount = adsBlockedCount + :count, dataSavedKb = dataSavedKb + :dataKb WHERE packageName = :packageName")
    suspend fun incrementBlockedStats(packageName: String, count: Int, dataKb: Long)
}
