package com.spendingsaver.tax

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Tax library entities - Room DB schema for tax data.
 *
 * Room Database: taxes.db with 7 tables
 */

/**
 * Tax type enum - 20+ tax types covering all major jurisdictions.
 */
enum class TaxType {
    // Income taxes (20+ types)
    INCOME_TAX,
    SALARY_TAX,
    WAGE_TAX,
    CAPITAL_GAINS_TAX,
    DIVIDEND_TAX,
    CORPORATE_TAX,
    BUSINESS_TAX,
    SELF_EMPLOYMENT_TAX,
    PASS_THROUGH_TAX,
    TRANSFER_TAX,
    GIFT_TAX,
    ESTATE_TAX,
    PROPERTY_TAX,
    VAT,
    SALES_TAX,
    GST,
    HST,
    POT,
    RECEIPTS_TAX,
    FIXED_ANNUITY_TAX,
    PENSION_TAX,
    SOCIAL_SECURITY_TAX,
    MEDICARE_TAX,
    UNEMPLOYMENT_TAX,
    ALIEN_TAX,
    TAX_CREDIT,
    DEDUCTION,
    BRACKET,
    RATE,

    // Legacy types
    INCOME,
    TAX;
}

// ---- Tax Library Entity Classes ----

@Entity(tableName = "tax_countries")
data class TaxCountry(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val country_code: String,
    val country_name: String,
    val region: String? = null,
    val iso_code: String? = null,
    val iso3_code: String? = null,
    val is_oecd: Boolean = false
)

@Entity(tableName = "tax_states")
data class TaxState(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val country_code: String,
    val state_code: String,
    val state_name: String,
    val is_state: Boolean = true,
    val is_province: Boolean = false
)

@Entity(tableName = "tax_towns")
data class TaxTown(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val state_id: String,
    val town_code: String? = null,
    val town_name: String,
    val county: String? = null,
    val is_city: Boolean = false,
    val is_town: Boolean = true
)

@Entity(tableName = "tax_details")
data class TaxDetail(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val country_code: String,
    val tax_type: String,
    val base_amount: Double,
    val tax_amount: Double,
    val total_amount: Double,
    val effective_rate: Double,
    val description: String? = null
)

@Entity(tableName = "tax_records")
data class TaxRecord(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val country_code: String,
    val tax_type: String,
    val date: String,
    val amount: Double,
    val notes: String? = null,
    val source_url: String? = null,
    val created_at: Long = System.currentTimeMillis()
)

@Entity(tableName = "tax_filings")
data class TaxFiling(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val country_code: String,
    val tax_year: Int,
    val tax_type: String,
    val status: String,
    val filing_due_date: String? = null,
    val filed_date: String? = null,
    val documents: String? = null
)

@Entity(tableName = "tax_audits")
data class TaxAudit(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val country_code: String,
    val audit_type: String,
    val audit_date: String,
    val amount: Double,
    val auditor_name: String? = null,
    val status: String
)
