package com.spendingsaver

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendingsaver.data.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the exported Room schema (app/schemas). When the schema version is bumped,
 * keep the old JSON, add the Migration here and extend this test with
 * `runMigrationsAndValidate(name, newVersion, true, AppDatabase.MIGRATION_x_y)`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun currentSchemaCreatesAndValidates() {
        helper.createDatabase("migration-test", 3).close()
        helper.runMigrationsAndValidate("migration-test", 3, true).close()
    }
}
