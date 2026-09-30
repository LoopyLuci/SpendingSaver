package com.spendingsaver.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, Transaction::class, Budget::class, PendingSync::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Schema migration: v1 used auto-increment Long primary keys; v2 switches to
         * server-style String UUID keys plus the sync metadata columns
         * (updatedAt / deleted / dirty / remoteId). Existing rows are preserved by
         * re-creating each table and stringifying the old Long ids so referential
         * links (e.g. transaction.accountId -> account.id) stay intact.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                // ---- accounts ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS accounts_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, type TEXT NOT NULL, balance REAL NOT NULL, " +
                        "currency TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "deleted INTEGER NOT NULL, dirty INTEGER NOT NULL, remoteId TEXT)"
                )
                db.execSQL(
                    "INSERT INTO accounts_new (id,name,type,balance,currency,updatedAt,deleted,dirty,remoteId) " +
                        "SELECT CAST(id AS TEXT), name, type, balance, currency, $now, 0, 0, NULL FROM accounts"
                )
                db.execSQL("DROP TABLE accounts")
                db.execSQL("ALTER TABLE accounts_new RENAME TO accounts")

                // ---- categories ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, parentId TEXT, color TEXT, " +
                        "updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, " +
                        "dirty INTEGER NOT NULL, remoteId TEXT)"
                )
                db.execSQL(
                    "INSERT INTO categories_new (id,name,parentId,color,updatedAt,deleted,dirty,remoteId) " +
                        "SELECT CAST(id AS TEXT), name, CAST(parentId AS TEXT), color, $now, 0, 0, NULL FROM categories"
                )
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")

                // ---- transactions ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS transactions_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, amount REAL NOT NULL, " +
                        "description TEXT NOT NULL, categoryId TEXT, date TEXT NOT NULL, notes TEXT, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, " +
                        "dirty INTEGER NOT NULL, remoteId TEXT)"
                )
                db.execSQL(
                    "INSERT INTO transactions_new " +
                        "(id,accountId,amount,description,categoryId,date,notes,createdAt,updatedAt,deleted,dirty,remoteId) " +
                        "SELECT CAST(id AS TEXT), CAST(accountId AS TEXT), amount, description, " +
                        "CAST(categoryId AS TEXT), date, notes, createdAt, $now, 0, 0, NULL FROM transactions"
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

                // ---- budgets ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budgets_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, categoryId TEXT NOT NULL, amount REAL NOT NULL, " +
                        "period TEXT NOT NULL, startDate TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "deleted INTEGER NOT NULL, dirty INTEGER NOT NULL, remoteId TEXT)"
                )
                db.execSQL(
                    "INSERT INTO budgets_new (id,categoryId,amount,period,startDate,updatedAt,deleted,dirty,remoteId) " +
                        "SELECT CAST(id AS TEXT), CAST(categoryId AS TEXT), amount, period, startDate, " +
                        "$now, 0, 0, NULL FROM budgets"
                )
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")

                // ---- pending_sync (new table in v2) ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_sync (" +
                        "id TEXT NOT NULL PRIMARY KEY, entityType TEXT NOT NULL, operation TEXT NOT NULL, " +
                        "remoteId TEXT, localData TEXT NOT NULL, synced INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spendingsaver.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
