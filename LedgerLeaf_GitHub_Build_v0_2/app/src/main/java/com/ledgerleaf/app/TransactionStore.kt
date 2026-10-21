package com.ledgerleaf.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

data class Entry(
    val id: Long = 0,
    val date: String,
    val merchant: String,
    val amountCents: Long,
    val kind: String,
    val category: String,
    val explanation: String,
    val receiptPath: String = ""
)

/** Device-local SQLite store. PROTOTYPE ONLY: database is not application-level encrypted. */
class TransactionStore(context: Context) : SQLiteOpenHelper(context, "ledgerleaf.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE entries (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          date TEXT NOT NULL, merchant TEXT NOT NULL, amount_cents INTEGER NOT NULL,
          kind TEXT NOT NULL, category TEXT NOT NULL, explanation TEXT NOT NULL,
          receipt_path TEXT NOT NULL DEFAULT ''
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun all(): List<Entry> {
        val result = mutableListOf<Entry>()
        readableDatabase.rawQuery("SELECT id,date,merchant,amount_cents,kind,category,explanation,receipt_path FROM entries ORDER BY date DESC,id DESC", null).use { c ->
            while(c.moveToNext()) result.add(Entry(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7)))
        }
        return result
    }
    fun save(e: Entry): Long {
        val v = ContentValues().apply {
            put("date", e.date); put("merchant", e.merchant); put("amount_cents", e.amountCents)
            put("kind", e.kind); put("category", e.category); put("explanation", e.explanation)
            put("receipt_path", e.receiptPath)
        }
        return if(e.id == 0L) writableDatabase.insertOrThrow("entries", null, v) else {
            writableDatabase.update("entries", v, "id=?", arrayOf(e.id.toString())); e.id
        }
    }
    fun delete(id: Long) { writableDatabase.delete("entries", "id=?", arrayOf(id.toString())) }
}

fun parseMoneyCents(input: String): Long? {
    val cleaned = input.trim().replace("$", "").replace(",", "")
    if (!Regex("^(?:[0-9]+)(?:\\.[0-9]{1,2})?$").matches(cleaned)) return null
    return try { java.math.BigDecimal(cleaned).movePointRight(2).longValueExact() } catch (_: Exception) { null }
}

fun centsString(cents: Long): String = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(cents / 100.0)
fun today(): String = LocalDate.now().toString()

/** RFC-style CSV quoting parser; supports quoted commas/newlines and escaped quotes. */
fun csvRows(csv: String): List<List<String>> {
    val rows = mutableListOf<List<String>>(); val row = mutableListOf<String>(); val value = StringBuilder()
    var quoted = false; var i = 0
    while(i < csv.length) {
        val ch = csv[i]
        when {
            ch == '"' && quoted && i+1 < csv.length && csv[i+1] == '"' -> { value.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { row.add(value.toString()); value.clear() }
            (ch == '\n' || ch == '\r') && !quoted -> {
                if(ch == '\r' && i+1 < csv.length && csv[i+1] == '\n') i++
                row.add(value.toString()); value.clear(); if(row.any { it.isNotBlank() }) rows.add(row.toList()); row.clear()
            }
            else -> value.append(ch)
        }
        i++
    }
    row.add(value.toString()); if(row.any { it.isNotBlank() }) rows.add(row.toList())
    return rows
}

fun toCsv(entries: List<Entry>): String {
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val header = "date,merchant,amount,type,category,explanation,receipt_attached"
    return (listOf(header) + entries.map { e ->
        listOf(e.date,e.merchant,"%.2f".format(java.util.Locale.US, e.amountCents/100.0),e.kind,e.category,e.explanation,if(e.receiptPath.isNotBlank()) "yes" else "no").joinToString(",", transform=::q)
    }).joinToString("\r\n") + "\r\n"
}
