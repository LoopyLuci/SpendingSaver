package com.spendingsaver.ai

import com.spendingsaver.data.Account
import com.spendingsaver.data.Budget
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

internal class AssistantEngineTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val food = Category(id = "c1", name = "Food")
    private val rent = Category(id = "c2", name = "Housing")
    private val acct = Account(id = "a1", name = "Checking", type = "checking", balance = 1200.0)
    private val data = FinanceSnapshot(
        accounts = listOf(acct),
        categories = listOf(food, rent),
        transactions = listOf(
            Transaction(accountId = "a1", amount = -40.0, description = "Grocery Mart", categoryId = "c1", date = "2026-11-10"),
            Transaction(accountId = "a1", amount = -60.0, description = "Pizza", categoryId = "c1", date = "2026-11-12"),
            Transaction(accountId = "a1", amount = -900.0, description = "Rent", categoryId = "c2", date = "2026-11-01"),
            Transaction(accountId = "a1", amount = -25.0, description = "Old", categoryId = "c1", date = "2026-01-05"),
            Transaction(accountId = "a1", amount = 2000.0, description = "Salary", date = "2026-11-03"),
        ),
        budgets = listOf(Budget(categoryId = "c1", amount = 80.0, period = "monthly", startDate = "")),
    )

    private fun ask(q: String) = AssistantEngine.answer(q, data, today)

    @Test fun balance() = assertTrue(ask("What is my balance?").contains("$1,200.00"))

    @Test fun monthlySpendingExcludesOlderMonths() = assertTrue(ask("How much did I spend this month?").contains("$1,000.00"))

    @Test fun yearSpendingIncludesOlder() = assertTrue(ask("how much did i spend this year").contains("$1,025.00"))

    @Test fun categoryQuestion() = assertTrue(ask("how much did I spend on food").contains("$100.00"))

    @Test fun descriptionQuestion() = assertTrue(ask("how much did I spend on pizza").contains("$60.00"))

    @Test fun budgetStatusShowsOverage() = assertTrue(ask("How are my budgets?").contains("over by $20.00"))

    @Test fun topCategoriesAreRanked() {
        val a = ask("top spending categories")
        assertTrue(a.indexOf("Housing") in 0 until a.indexOf("Food"))
    }

    @Test fun incomeAndEmptyData() {
        assertTrue(ask("what is my income").contains("$2,000.00"))
        val empty = AssistantEngine.answer("what is my balance", FinanceSnapshot(emptyList(), emptyList(), emptyList(), emptyList()), today)
        assertTrue(empty.contains("no accounts"))
    }

    @Test fun unknownQuestionGetsHelpNotAnError() = assertTrue(ask("qwerty zzz").contains("Try:"))

    /** The assistant must be unable to reach any service: no networking vocabulary in its package. */
    @Test fun aiPackageContainsNoNetworkCode() {
        val dir = listOf("src/main/java/com/spendingsaver/ai", "app/src/main/java/com/spendingsaver/ai").map(::File).first { it.isDirectory }
        val banned = listOf("okhttp", "java.net", "javax.net", "http://", "https://", "HttpURLConnection", "Socket", "retrofit", "ktor", "WebView", "android.net")
        val files = dir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val text = f.readText().lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }.joinToString("\n")
            for (b in banned) assertFalse(text.contains(b, ignoreCase = true), "${f.name} mentions \"$b\"")
        }
    }
}
