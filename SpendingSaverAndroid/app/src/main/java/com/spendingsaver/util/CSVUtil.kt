package com.spendingsaver.util

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import com.spendingsaver.data.Transaction
import java.io.*

object CSVUtil {
    fun importFromUri(context: Context, uri: Uri, accountId: String): Int {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return 0
        val reader = CSVReader(InputStreamReader(inputStream))
        var imported = 0
        reader.use { csvReader ->
            val header = csvReader.readNext() ?: return 0
            val dateIdx = header.indexOf("Date")
            val descIdx = header.indexOf("Description")
            val amountIdx = header.indexOf("Amount")
            if (dateIdx < 0 || descIdx < 0 || amountIdx < 0) return 0

            var line: Array<String>? = csvReader.readNext()
            while (line != null) {
                val date = line.getOrNull(dateIdx) ?: ""
                val desc = line.getOrNull(descIdx) ?: ""
                val amountStr = line.getOrNull(amountIdx)?.replace("$", "")?.replace(",", "") ?: ""
                val amount = amountStr.toDoubleOrNull() ?: 0.0

                if (date.isNotBlank() && desc.isNotBlank()) {
                    val tx = Transaction(
                        accountId = accountId,
                        amount = amount,
                        description = desc,
                        date = date,
                        createdAt = System.currentTimeMillis()
                    )
                    imported++
                }
                line = csvReader.readNext()
            }
        }
        return imported
    }

    fun exportToCsv(transactions: List<Transaction>): String {
        val sw = StringWriter()
        val writer = CSVWriter(sw)
        writer.writeNext(arrayOf("Date", "Description", "Category", "Amount"))
        for (tx in transactions) {
            val amount = tx.amount.toString()
            writer.writeNext(arrayOf(tx.date, tx.description, tx.categoryId?.toString() ?: "", amount))
        }
        writer.close()
        return sw.toString()
    }
}
