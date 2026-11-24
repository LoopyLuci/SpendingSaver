package com.spendingsaver.ai

import com.spendingsaver.data.Account
import com.spendingsaver.data.AppSettings
import com.spendingsaver.data.Budget
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import com.spendingsaver.util.BudgetPeriod
import com.spendingsaver.util.Format
import java.time.LocalDate

/** Everything the assistant may look at. It is a plain snapshot of data already on this device. */
data class FinanceSnapshot(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val settings: AppSettings = AppSettings(),
)

/**
 * The on-device assistant. It is deterministic and pure: no network, no files, no model download.
 * It reads the [FinanceSnapshot] it is given and answers in plain language. Because it imports nothing
 * from the networking stack and takes no client, nothing it does can leave the phone.
 */
object AssistantEngine {

    val SUGGESTIONS = listOf(
        "What is my balance?",
        "How much did I spend this month?",
        "Top spending categories",
        "How are my budgets?",
        "Biggest expenses",
        "Recent transactions",
    )

    fun answer(question: String, data: FinanceSnapshot, today: LocalDate = LocalDate.now()): String {
        val q = question.lowercase().trim()
        if (q.isBlank()) return help()
        val cur = data.settings.defaultCurrency
        val money = { v: Double -> Format.money(v, cur) }

        return when {
            has(q, "help", "what can you") -> help()
            has(q, "budget") -> budgets(data, money)
            has(q, "balance", "net worth", "how much do i have", "total money") -> balance(data, money)
            has(q, "categories", "by category", "top categor", "where does my money", "where did my money") ->
                topCategories(data, money, today, windowFor(q))
            has(q, "biggest", "largest", "most expensive") -> biggest(data, money, today, windowFor(q))
            has(q, "recent", "latest", "last transactions") -> recent(data, money)
            has(q, "income", "earn", "salary") -> income(data, money, today, windowFor(q))
            has(q, "saving", "saved") -> savings(data, money, today, windowFor(q))
            has(q, "how many") && has(q, "transaction") ->
                "You have ${data.transactions.size} transactions across ${data.accounts.size} accounts."
            has(q, "spend", "spent", "expense", "cost") -> spending(q, data, money, today)
            else -> byName(q, data, money, today) ?: ("I did not understand that. " + help())
        }
    }

    private fun has(q: String, vararg words: String) = words.any { q.contains(it) }

    private enum class Window(val label: String) { WEEK("this week"), MONTH("this month"), YEAR("this year"), ALL("overall") }

    private fun windowFor(q: String) = when {
        has(q, "week") -> Window.WEEK
        has(q, "year") -> Window.YEAR
        has(q, "all time", "overall", "ever") -> Window.ALL
        else -> Window.MONTH
    }

    private fun inWindow(tx: Transaction, w: Window, today: LocalDate): Boolean {
        if (w == Window.ALL) return true
        val d = runCatching { LocalDate.parse(tx.date.take(10)) }.getOrNull() ?: return false
        val period = when (w) { Window.WEEK -> "weekly"; Window.YEAR -> "yearly"; else -> "monthly" }
        return d >= BudgetPeriod.windowStart(period, today) && d <= BudgetPeriod.windowEnd(period, today)
    }

    private fun help() = "I run entirely on this phone and only look at your own data. Try: " +
        SUGGESTIONS.joinToString("; ") { "\"$it\"" } +
        ". You can also ask \"how much did I spend on groceries\" or add \"this week\" or \"this year\"."

    private fun balance(d: FinanceSnapshot, money: (Double) -> String): String {
        if (d.accounts.isEmpty()) return "You have no accounts yet. Add one on the Accounts screen."
        val total = d.accounts.sumOf { it.balance }
        val lines = d.accounts.sortedByDescending { it.balance }.joinToString("\n") { "• ${it.name}: ${money(it.balance)}" }
        return "Your total balance is ${money(total)} across ${d.accounts.size} account${if (d.accounts.size == 1) "" else "s"}:\n$lines"
    }

    private fun expenses(d: FinanceSnapshot, w: Window, today: LocalDate) =
        d.transactions.filter { it.amount < 0 && inWindow(it, w, today) }

    private fun spending(q: String, d: FinanceSnapshot, money: (Double) -> String, today: LocalDate): String {
        val w = windowFor(q)
        byName(q, d, money, today)?.let { return it }
        val ex = expenses(d, w, today)
        if (ex.isEmpty()) return "No expenses recorded ${w.label}."
        return "You spent ${money(ex.sumOf { -it.amount })} ${w.label} in ${ex.size} transaction${if (ex.size == 1) "" else "s"}."
    }

    private fun byName(q: String, d: FinanceSnapshot, money: (Double) -> String, today: LocalDate): String? {
        val w = windowFor(q)
        val cat = d.categories.filter { !it.deleted && q.contains(it.name.lowercase()) }.maxByOrNull { it.name.length }
        if (cat != null) {
            val ex = expenses(d, w, today).filter { it.categoryId == cat.id }
            return if (ex.isEmpty()) "Nothing spent on ${cat.name} ${w.label}."
            else "You spent ${money(ex.sumOf { -it.amount })} on ${cat.name} ${w.label} (${ex.size} transaction${if (ex.size == 1) "" else "s"})."
        }
        val words = q.split(Regex("[^a-z0-9]+")).filter { it.length >= 4 && it !in STOP }
        val hit = words.firstOrNull { wd -> d.transactions.any { it.description.lowercase().contains(wd) } } ?: return null
        val tx = expenses(d, w, today).filter { it.description.lowercase().contains(hit) }
        return if (tx.isEmpty()) "No spending matching \"$hit\" ${w.label}."
        else "You spent ${money(tx.sumOf { -it.amount })} on \"$hit\" ${w.label} (${tx.size} transaction${if (tx.size == 1) "" else "s"})."
    }

    private val STOP = setOf("much", "spend", "spent", "this", "month", "week", "year", "what", "have", "money", "show", "tell", "about", "with", "from")

    private fun topCategories(d: FinanceSnapshot, money: (Double) -> String, today: LocalDate, w: Window): String {
        val ex = expenses(d, w, today)
        if (ex.isEmpty()) return "No expenses recorded ${w.label}."
        val names = d.categories.associate { it.id to it.name }
        val rows = ex.groupBy { it.categoryId }.map { (id, l) -> (names[id] ?: "Uncategorized") to l.sumOf { -it.amount } }
            .sortedByDescending { it.second }.take(5)
        val total = ex.sumOf { -it.amount }
        return "Top spending ${w.label} (total ${money(total)}):\n" +
            rows.joinToString("\n") { "• ${it.first}: ${money(it.second)} (${(it.second / total * 100).toInt()}%)" }
    }

    private fun biggest(d: FinanceSnapshot, money: (Double) -> String, today: LocalDate, w: Window): String {
        val ex = expenses(d, w, today).sortedBy { it.amount }.take(5)
        if (ex.isEmpty()) return "No expenses recorded ${w.label}."
        return "Biggest expenses ${w.label}:\n" + ex.joinToString("\n") { "• ${it.description}: ${money(-it.amount)} on ${it.date}" }
    }

    private fun recent(d: FinanceSnapshot, money: (Double) -> String): String {
        val t = d.transactions.sortedByDescending { it.date }.take(5)
        if (t.isEmpty()) return "No transactions yet."
        return "Most recent transactions:\n" + t.joinToString("\n") { "• ${it.date} ${it.description}: ${money(it.amount)}" }
    }

    private fun income(d: FinanceSnapshot, money: (Double) -> String, today: LocalDate, w: Window): String {
        val inc = d.transactions.filter { it.amount > 0 && inWindow(it, w, today) }
        if (inc.isEmpty()) return "No income recorded ${w.label}."
        return "You received ${money(inc.sumOf { it.amount })} ${w.label} in ${inc.size} deposit${if (inc.size == 1) "" else "s"}."
    }

    private fun savings(d: FinanceSnapshot, money: (Double) -> String, today: LocalDate, w: Window): String {
        val tx = d.transactions.filter { inWindow(it, w, today) }
        val inc = tx.filter { it.amount > 0 }.sumOf { it.amount }
        val out = tx.filter { it.amount < 0 }.sumOf { -it.amount }
        if (inc == 0.0 && out == 0.0) return "Nothing recorded ${w.label}."
        val rate = if (inc > 0) " Savings rate: ${((inc - out) / inc * 100).toInt()}%." else ""
        return "${w.label.replaceFirstChar { it.uppercase() }}: income ${money(inc)}, spending ${money(out)}, net ${money(inc - out)}.$rate"
    }

    private fun budgets(d: FinanceSnapshot, money: (Double) -> String): String {
        val live = d.budgets.filter { !it.deleted }
        if (live.isEmpty()) return "You have no budgets yet. Create one on the Budgets screen."
        val names = d.categories.associate { it.id to it.name }
        return "Budget status:\n" + live.joinToString("\n") { b ->
            val spent = BudgetPeriod.spent(d.transactions, b.categoryId, b.period)
            val left = b.amount - spent
            val name = names[b.categoryId] ?: "Unknown"
            val state = if (left < 0) "over by ${money(-left)}" else "${money(left)} left"
            "• $name (${b.period}): ${money(spent)} of ${money(b.amount)}, $state"
        }
    }
}
