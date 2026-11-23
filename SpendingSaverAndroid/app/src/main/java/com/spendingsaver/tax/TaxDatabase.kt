package com.spendingsaver.tax

import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Tax Database - Room database for the Taxes Library.
 *
 * Schema:
 * - tax_countries (7+ fields: id, country_code, country_name, region, iso_code, iso3_code, is_oecd)
 * - tax_states (7+ fields: id, country_code, state_code, state_name, is_state, is_province)
 * - tax_towns (7+ fields: id, state_id, town_code, town_name, county, is_city, is_town)
 * - tax_details (8+ fields: id, country_code, tax_type, base_amount, tax_amount, total_amount, effective_rate, description)
 * - tax_records (8+ fields: id, country_code, tax_type, date, amount, notes, source_url, created_at)
 * - tax_filings (8+ fields: id, country_code, tax_year, tax_type, status, filing_due_date, filed_date, documents)
 * - tax_audits (7+ fields: id, country_code, audit_type, audit_date, amount, auditor_name, status)
 *
 * Total: 7 tables
 * Database version: 1
 */
@Database(
    entities = [
        TaxCountry::class,
        TaxState::class,
        TaxTown::class,
        TaxDetail::class,
        TaxRecord::class,
        TaxFiling::class,
        TaxAudit::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TaxDatabase : RoomDatabase() {
    abstract fun taxDao(): TaxDao

    companion object {
        @Volatile
        private var INSTANCE: TaxDatabase? = null

        fun getInstance(context: android.content.Context): TaxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaxDatabase::class.java,
                    "taxes.db"
                ).build().also { INSTANCE = it }
                instance
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Future migration placeholder
            }
        }
    }
}
