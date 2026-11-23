package com.spendingsaver.tax

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import io.mockk.every
import org.junit.jupiter.api.Test

/** Unit tests for [TaxRepository]. The Room DAO is mocked so the repository is
 * exercised in isolation; no database is required.
 */
internal class TaxRepositoryTest {

    @Test
    fun taxDao_observeAllCountries_returns_empty_flow() = runTest {
        val taxDao = mockk<TaxDao>(relaxed = true)
        every { taxDao.observeAllCountries() } returns flowOf(emptyList())

        val context = mockk<android.content.Context>(relaxed = true)
        val repository = TaxRepository(context, taxDao)

        val result = repository.observeCountries().first()
        assert(result.isEmpty())
    }

    @Test
    fun taxDao_observeAllCountries_returns_countries() = runTest {
        val country = TaxCountry(country_code = "US", country_name = "United States", region = "North America", is_oecd = true)
        val de = TaxCountry(country_code = "DE", country_name = "Germany", region = "Europe", is_oecd = true)

        val taxDao = mockk<TaxDao>(relaxed = true)
        every { taxDao.observeAllCountries() } returns flowOf(listOf(country, de))

        val context = mockk<android.content.Context>(relaxed = true)
        val repository = TaxRepository(context, taxDao)

        val result = repository.observeCountries().first()
        assert(result.size == 2)
        assert(result.any { it.country_code == "US" })
    }

    @Test
    fun taxRepository_insertCountry() = runTest {
        val taxDao = mockk<TaxDao>(relaxed = true)

        val context = mockk<android.content.Context>(relaxed = true)
        val repository = TaxRepository(context, taxDao)

        repository.insertCountry(TaxCountry(country_code = "US", country_name = "United States", region = "North America", is_oecd = true))

        coVerify { taxDao.insertCountry(any()) }
    }

    @Test
    fun taxRepository_insertState() = runTest {
        val taxDao = mockk<TaxDao>(relaxed = true)

        val context = mockk<android.content.Context>(relaxed = true)
        val repository = TaxRepository(context, taxDao)

        repository.insertState(TaxState(country_code = "US", state_code = "CA", state_name = "California", is_state = true))

        coVerify { taxDao.insertState(any()) }
    }

    @Test
    fun taxRepository_seedTaxData() = runTest {
        val taxDao = mockk<TaxDao>(relaxed = true)

        val context = mockk<android.content.Context>(relaxed = true)
        val repository = TaxRepository(context, taxDao)

        repository.seedTaxData()

        coVerify(exactly = 3) { taxDao.insertCountry(any()) }
        coVerify(exactly = 6) { taxDao.insertState(any()) }
        coVerify(exactly = 7) { taxDao.insertTown(any()) }
        coVerify(exactly = 1) { taxDao.insertDetails(any()) }
        coVerify(exactly = 3) { taxDao.insertRecord(any()) }
        coVerify(exactly = 3) { taxDao.insertFiling(any()) }
        coVerify(exactly = 3) { taxDao.insertAudit(any()) }
    }
}
