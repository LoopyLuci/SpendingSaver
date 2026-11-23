package com.spendingsaver.tax

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Tax DAO - Room Data Access Object for the Taxes Library.
 * Provides CRUD operations for all tax entities.
 */
@Dao
interface TaxDao {
    // ---- Tax Countries ----

    @Query("SELECT * FROM tax_countries ORDER BY country_name")
    fun observeAllCountries(): Flow<List<TaxCountry>>

    @Query("SELECT * FROM tax_countries WHERE country_code = :code")
    fun getCountryByCode(code: String): Flow<TaxCountry?>

    @Query("SELECT * FROM tax_countries WHERE is_oecd = 1 ORDER BY country_name")
    fun observeOecdCountries(): Flow<List<TaxCountry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountry(country: TaxCountry)

    // ---- Tax States ----

    @Query("SELECT * FROM tax_states WHERE country_code = :countryCode ORDER BY state_name")
    fun observeStatesByCountry(countryCode: String): Flow<List<TaxState>>

    @Query("SELECT * FROM tax_states WHERE country_code = :countryCode AND state_code = :code")
    fun getStateByCode(countryCode: String, code: String): Flow<TaxState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: TaxState)

    // ---- Tax Towns ----

    @Query("SELECT * FROM tax_towns WHERE state_id = :stateId ORDER BY town_name")
    fun observeTownsByState(stateId: String): Flow<List<TaxTown>>

    @Query("SELECT * FROM tax_towns WHERE town_name LIKE :query OR county LIKE :query LIMIT 50")
    fun searchTowns(query: String): Flow<List<TaxTown>>

    @Query("SELECT * FROM tax_towns WHERE town_name = :name AND state_id = :stateId LIMIT 1")
    fun getTownByName(name: String, stateId: String): Flow<TaxTown?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTown(town: TaxTown)

    // ---- Tax Details (tax breakdowns) ----

    @Query("SELECT * FROM tax_details WHERE country_code = :countryCode ORDER BY tax_type")
    fun observeDetailsByCountry(countryCode: String): Flow<List<TaxDetail>>

    @Query("SELECT * FROM tax_details WHERE country_code = :countryCode AND tax_type = :taxType")
    fun observeDetailsByType(countryCode: String, taxType: String): Flow<List<TaxDetail>>

    @Query("SELECT * FROM tax_details WHERE country_code = :countryCode AND tax_type = :taxType AND base_amount = :baseAmount")
    fun getDetailByBaseAmount(countryCode: String, taxType: String, baseAmount: Double): Flow<TaxDetail?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetail(detail: TaxDetail)

    // ---- Bulk insert helper for tax details ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetails(details: List<TaxDetail>)

    // ---- Tax Records ----

    @Query("SELECT * FROM tax_records WHERE country_code = :countryCode ORDER BY date DESC")
    fun observeRecordsByCountry(countryCode: String): Flow<List<TaxRecord>>

    @Query("SELECT * FROM tax_records WHERE country_code = :countryCode AND tax_type = :taxType ORDER BY date DESC")
    fun observeRecordsByType(countryCode: String, taxType: String): Flow<List<TaxRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TaxRecord)

    // ---- Tax Filings ----

    @Query("SELECT * FROM tax_filings WHERE country_code = :countryCode ORDER BY tax_year DESC")
    fun observeFilingsByCountry(countryCode: String): Flow<List<TaxFiling>>

    @Query("SELECT * FROM tax_filings WHERE country_code = :countryCode AND tax_year = :year")
    fun observeFilingsByYear(countryCode: String, year: Int): Flow<List<TaxFiling>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiling(filing: TaxFiling)

    @Query("UPDATE tax_filings SET status = :status, filed_date = :filedDate WHERE id = :id")
    suspend fun updateFilingStatus(id: String, status: String, filedDate: String?)

    // ---- Tax Audits ----

    @Query("SELECT * FROM tax_audits WHERE country_code = :countryCode ORDER BY audit_date DESC")
    fun observeAuditsByCountry(countryCode: String): Flow<List<TaxAudit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: TaxAudit)

    // ---- Search Projection ----

    @Query("""
        SELECT tc.country_code, tc.country_name, ts.state_name, tt.town_name
        FROM tax_countries tc
        LEFT JOIN tax_states ts ON tc.country_code = ts.country_code
        LEFT JOIN tax_towns tt ON ts.id = tt.state_id
        WHERE tc.country_name LIKE :query OR ts.state_name LIKE :query OR tt.town_name LIKE :query
        LIMIT 100
    """)
    fun searchTaxableEntities(query: String): Flow<List<SearchableTax>>
}

/**
 * SearchableTax Projection - a flattened view for search interface.
 */
data class SearchableTax(
    val country_code: String,
    val country_name: String,
    val state_name: String? = null,
    val town_name: String? = null,
)
