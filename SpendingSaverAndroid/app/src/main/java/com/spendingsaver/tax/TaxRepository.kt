package com.spendingsaver.tax

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Tax Repository - provides access to tax data.
 * Wraps the Room DAO and provides a clean API for the UI layer.
 * The repository can load tax data from the local database
 * or from an external source (host engine or web API).
 */
@Singleton
class TaxRepository @Inject constructor(
    private val context: Context,
    private val taxDao: TaxDao,
) {

    // ---- Tax Countries ----

    fun observeCountries(): Flow<List<TaxCountry>> = taxDao.observeAllCountries()

    fun getCountryByCode(code: String): Flow<TaxCountry?> = taxDao.getCountryByCode(code)

    fun observeOecdCountries(): Flow<List<TaxCountry>> = taxDao.observeOecdCountries()

    suspend fun insertCountry(country: TaxCountry) = taxDao.insertCountry(country)

    // ---- Tax States ----

    fun observeStatesByCountry(countryCode: String): Flow<List<TaxState>> =
        taxDao.observeStatesByCountry(countryCode)

    fun getStateByCode(countryCode: String, code: String): Flow<TaxState?> =
        taxDao.getStateByCode(countryCode, code)

    suspend fun insertState(state: TaxState) = taxDao.insertState(state)

    // ---- Tax Towns ----

    fun observeTownsByState(stateId: String): Flow<List<TaxTown>> =
        taxDao.observeTownsByState(stateId)

    fun searchTowns(query: String): Flow<List<TaxTown>> = taxDao.searchTowns(query)

    fun getTownByName(name: String, stateId: String): Flow<TaxTown?> =
        taxDao.getTownByName(name, stateId)

    suspend fun insertTown(town: TaxTown) = taxDao.insertTown(town)

    // ---- Tax Details ----

    fun observeDetailsByCountry(countryCode: String): Flow<List<TaxDetail>> =
        taxDao.observeDetailsByCountry(countryCode)

    fun observeDetailsByType(countryCode: String, taxType: String): Flow<List<TaxDetail>> =
        taxDao.observeDetailsByType(countryCode, taxType)

    suspend fun insertDetail(detail: TaxDetail) = taxDao.insertDetail(detail)

    suspend fun insertDetails(details: List<TaxDetail>) = taxDao.insertDetails(details)

    // ---- Tax Records ----

    fun observeRecordsByCountry(countryCode: String): Flow<List<TaxRecord>> =
        taxDao.observeRecordsByCountry(countryCode)

    fun observeRecordsByType(countryCode: String, taxType: String): Flow<List<TaxRecord>> =
        taxDao.observeRecordsByType(countryCode, taxType)

    suspend fun insertRecord(record: TaxRecord) = taxDao.insertRecord(record)

    // ---- Tax Filings ----

    fun observeFilingsByCountry(countryCode: String): Flow<List<TaxFiling>> =
        taxDao.observeFilingsByCountry(countryCode)

    fun observeFilingsByYear(countryCode: String, year: Int): Flow<List<TaxFiling>> =
        taxDao.observeFilingsByYear(countryCode, year)

    suspend fun insertFiling(filing: TaxFiling) = taxDao.insertFiling(filing)

    suspend fun updateFilingStatus(id: String, status: String, filedDate: String?) =
        taxDao.updateFilingStatus(id, status, filedDate)

    // ---- Tax Audits ----

    fun observeAuditsByCountry(countryCode: String): Flow<List<TaxAudit>> =
        taxDao.observeAuditsByCountry(countryCode)

    suspend fun insertAudit(audit: TaxAudit) = taxDao.insertAudit(audit)

    // ---- Search ----

    fun searchTaxableEntities(query: String): Flow<List<SearchableTax>> =
        taxDao.searchTaxableEntities(query)

        suspend fun seedTaxData() {
        val now = System.currentTimeMillis()
        insertCountry(TaxCountry(country_code = "US", country_name = "United States", region = "North America", is_oecd = true))
        insertCountry(TaxCountry(country_code = "DE", country_name = "Germany", region = "Europe", is_oecd = true))
        insertCountry(TaxCountry(country_code = "FR", country_name = "France", region = "Europe", is_oecd = true))

        insertState(TaxState(country_code = "US", state_code = "CA", state_name = "California", is_state = true))
        insertState(TaxState(country_code = "US", state_code = "NY", state_name = "New York", is_state = true))
        insertState(TaxState(country_code = "DE", state_code = "BE", state_name = "Bavaria", is_state = true))
        insertState(TaxState(country_code = "DE", state_code = "BX", state_name = "Berlin", is_state = true))
        insertState(TaxState(country_code = "FR", state_code = "IDF", state_name = "Ile-de-France", is_state = true))
        insertState(TaxState(country_code = "FR", state_code = "AU", state_name = "Auvergne-Rhone-Alpes", is_state = true))

        insertTown(TaxTown(state_id = "CA", town_name = "San Francisco", county = "San Francisco", is_city = true))
        insertTown(TaxTown(state_id = "CA", town_name = "Los Angeles", county = "Los Angeles", is_city = true))
        insertTown(TaxTown(state_id = "NY", town_name = "New York City", county = "New York", is_city = true))
        insertTown(TaxTown(state_id = "BE", town_name = "Munich", county = "Upper Bavaria", is_city = true))
        insertTown(TaxTown(state_id = "BX", town_name = "Berlin", county = null, is_city = true))
        insertTown(TaxTown(state_id = "IDF", town_name = "Paris", county = "Paris", is_city = true))
        insertTown(TaxTown(state_id = "AU", town_name = "Lyon", county = "Rhone", is_city = true))

        insertDetails(listOf(
            TaxDetail(country_code = "US", tax_type = "SALES_TAX", base_amount = 100.0, tax_amount = 7.25, total_amount = 107.25, effective_rate = 0.0725, description = "CA state sales tax (sample)"),
            TaxDetail(country_code = "US", tax_type = "INCOME_TAX", base_amount = 10000.0, tax_amount = 1500.0, total_amount = 11500.0, effective_rate = 0.15, description = "US federal income (sample)"),
            TaxDetail(country_code = "DE", tax_type = "VAT", base_amount = 100.0, tax_amount = 19.0, total_amount = 119.0, effective_rate = 0.19, description = "German VAT (Umsatzsteuer)"),
            TaxDetail(country_code = "DE", tax_type = "INCOME_TAX", base_amount = 10000.0, tax_amount = 3000.0, total_amount = 13000.0, effective_rate = 0.30, description = "German income tax (sample)"),
            TaxDetail(country_code = "FR", tax_type = "VAT", base_amount = 100.0, tax_amount = 20.0, total_amount = 120.0, effective_rate = 0.20, description = "French VAT (TVA)"),
            TaxDetail(country_code = "FR", tax_type = "INCOME_TAX", base_amount = 10000.0, tax_amount = 2500.0, total_amount = 12500.0, effective_rate = 0.25, description = "French income tax (prélèvement à la source)"),
        ))

        insertRecord(TaxRecord(country_code = "US", tax_type = "SALES_TAX", date = "2026-11-01", amount = 107.25, notes = "CA sample sales tax", source_url = "https://renewable.example/us-ca-sales", created_at = now))
        insertRecord(TaxRecord(country_code = "DE", tax_type = "VAT", date = "2026-11-01", amount = 119.0, notes = "German VAT (sample)", source_url = "https://renewable.example/de-vat", created_at = now))
        insertRecord(TaxRecord(country_code = "FR", tax_type = "VAT", date = "2026-11-01", amount = 120.0, notes = "French VAT (sample)", source_url = "https://renewable.example/fr-tva", created_at = now))

        insertFiling(TaxFiling(country_code = "US", tax_year = 2026, tax_type = "INCOME_TAX", status = "DUE", filing_due_date = "2026-04-15", filed_date = null))
        insertFiling(TaxFiling(country_code = "DE", tax_year = 2026, tax_type = "INCOME_TAX", status = "FILED", filing_due_date = "2026-05-31", filed_date = "2026-05-20"))
        insertFiling(TaxFiling(country_code = "FR", tax_year = 2026, tax_type = "INCOME_TAX", status = "FILED", filing_due_date = "2026-05-15", filed_date = "2026-05-10"))

        insertAudit(TaxAudit(country_code = "US", audit_type = "HOME", audit_date = "2026-08-01", amount = 1000.0, status = "OPEN"))
        insertAudit(TaxAudit(country_code = "DE", audit_type = "RANDOM", audit_date = "2026-07-15", amount = 500.0, status = "CLOSED"))
        insertAudit(TaxAudit(country_code = "FR", audit_type = "HOME", audit_date = "2026-06-01", amount = 750.0, status = "OPEN"))
    }companion object {
        fun createTaxRepository(context: Context): TaxRepository {
            val appDatabase = TaxDatabase.getInstance(context)
            val taxDao = appDatabase.taxDao()
            return TaxRepository(context, taxDao)
        }
    }
}
