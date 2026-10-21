package com.ledgerleaf.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LedgerApp() } }
    }
}

@Composable
fun LedgerApp() {
    val ctx = LocalContext.current
    val db = remember { TransactionStore(ctx) }
    var transactions by remember { mutableStateOf(db.all()) }
    var tab by remember { mutableStateOf(0) }
    var editor by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val refresh = { transactions = db.all() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) {
            try {
                val raw = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val rows = csvRows(raw)
                if(rows.isEmpty()) throw IllegalArgumentException("Empty CSV")
                val headings = rows.first().map { it.trim().lowercase() }
                fun idx(vararg names: String): Int = headings.indexOfFirst { it in names }
                val dateIdx=idx("date"); val nameIdx=idx("merchant","description","payee"); val amountIdx=idx("amount")
                if(dateIdx<0 || nameIdx<0 || amountIdx<0) throw IllegalArgumentException("Required columns: date, merchant, amount")
                val kindIdx=idx("type","kind"); val catIdx=idx("category"); val noteIdx=idx("explanation","note")
                var count=0; var skipped=0
                for(row in rows.drop(1)) {
                    try {
                        if(row.size <= maxOf(dateIdx,nameIdx,amountIdx)) throw IllegalArgumentException()
                        val parsedDate=LocalDate.parse(row[dateIdx].trim()).toString()
                        val rawMoney=row[amountIdx].trim().replace("$", "").replace(",", "")
                        val negative=rawMoney.startsWith("-") || (rawMoney.startsWith("(") && rawMoney.endsWith(")"))
                        val money=parseMoneyCents(rawMoney.removePrefix("-").removePrefix("(").removeSuffix(")")) ?: throw IllegalArgumentException()
                        val kind=if(kindIdx>=0 && row.getOrNull(kindIdx)?.trim()?.lowercase() in listOf("income","expense")) row[kindIdx].trim().lowercase() else if(negative) "expense" else "income"
                        db.save(Entry(date=parsedDate,merchant=row[nameIdx].trim(),amountCents=money,kind=kind,category=if(catIdx>=0) row.getOrNull(catIdx)?.ifBlank { "Uncategorized" } ?: "Uncategorized" else "Uncategorized",explanation=if(noteIdx>=0) row.getOrNull(noteIdx) ?: "" else ""))
                        count++
                    } catch (_: Exception) { skipped++ }
                }
                refresh(); message="Imported $count rows; skipped $skipped. Check categories and duplicates."
            } catch(e: Exception) { message="Import failed: ${e.message}" }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri != null) try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(toCsv(transactions).toByteArray(Charsets.UTF_8)) }
            message="CSV exported successfully."
        } catch(e: Exception) { message="Export failed: ${e.message}" }
    }

    Scaffold(topBar = { Surface(tonalElevation=4.dp) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("LedgerLeaf", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
        Text("Offline Android bookkeeping prototype • Sample data recommended", style=MaterialTheme.typography.labelSmall)
    } } }, bottomBar={ NavigationBar {
        NavigationBarItem(selected=tab==0,onClick={tab=0},icon={Text("▦")},label={Text("Overview")})
        NavigationBarItem(selected=tab==1,onClick={tab=1},icon={Text("≡")},label={Text("Transactions")})
        NavigationBarItem(selected=tab==2,onClick={tab=2},icon={Text("↕")},label={Text("Import / Export")})
    } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)) {
            if(message.isNotBlank()) { Text(message,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(vertical=8.dp)); TextButton(onClick={message=""}) { Text("Dismiss") } }
            when(tab) {
                0 -> Overview(transactions,onAdd={ adding=true; editor=null },onOpenTransactions={tab=1})
                1 -> {
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Transactions",style=MaterialTheme.typography.titleLarge)
                        Button(onClick={adding=true;editor=null}) { Text("+ Add") }
                    }
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(transactions,key={it.id}) { e -> ElevatedCard(onClick={editor=e;adding=true},modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(e.merchant,fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
                                    Text((if(e.kind=="expense") "−" else "+")+centsString(e.amountCents))
                                }
                                Text("${e.date} • ${e.category}",style=MaterialTheme.typography.bodySmall)
                                if(e.explanation.isNotBlank()) Text(e.explanation,style=MaterialTheme.typography.bodySmall,maxLines=2)
                                if(e.receiptPath.isNotBlank()) Text("Receipt image attached",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            }
                        } }
                    }
                }
                else -> Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(top=20.dp)) {
                    Text("Move records on or off this device",style=MaterialTheme.typography.titleLarge)
                    Text("Imports accept CSV columns date (YYYY-MM-DD), merchant, amount; optional type, category, explanation. Positive amounts default to income, negative to expense unless type is given.")
                    Button(onClick={importLauncher.launch(arrayOf("text/*","application/csv","application/octet-stream"))},modifier=Modifier.fillMaxWidth()) { Text("Import transaction CSV") }
                    OutlinedButton(onClick={exportLauncher.launch("ledgerleaf-transactions.csv")},modifier=Modifier.fillMaxWidth()) { Text("Export transaction CSV") }
                    Text("Important: This CSV includes transaction notes, but receipt image files are NOT included. It is not a full backup.",style=MaterialTheme.typography.bodySmall)
                    Text("Do not import sensitive customer records until encryption, backup/recovery, and accounting validation are implemented.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(adding) { TransactionEditor(initial=editor,onDismiss={adding=false},onSave={ entry->
        db.save(entry); refresh(); adding=false; message="Transaction saved on this device."
    },onDelete={e->db.delete(e.id);refresh();adding=false;message="Transaction deleted."}) }
}

@Composable
fun Overview(entries: List<Entry>, onAdd:()->Unit, onOpenTransactions:()->Unit) {
    val income=entries.filter{it.kind=="income"}.sumOf{it.amountCents}
    val expense=entries.filter{it.kind=="expense"}.sumOf{it.amountCents}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.padding(top=18.dp)) {
        Text("Your business at a glance",style=MaterialTheme.typography.titleLarge)
        Text("Totals for all entered dates; not a tax return or reconciled accounting statement.",style=MaterialTheme.typography.bodySmall)
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("Income",style=MaterialTheme.typography.labelLarge); Text(centsString(income),style=MaterialTheme.typography.headlineMedium)
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            Text("Expenses",style=MaterialTheme.typography.labelLarge); Text(centsString(expense),style=MaterialTheme.typography.headlineMedium)
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            Text("Net cash activity (simplified)",style=MaterialTheme.typography.labelLarge)
            Text(centsString(income-expense),style=MaterialTheme.typography.headlineMedium)
        } }
        Text("${entries.size} locally stored transactions")
        Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()) { Text("Record a purchase or income") }
        OutlinedButton(onClick=onOpenTransactions,modifier=Modifier.fillMaxWidth()) { Text("Review transactions") }
        Text("Coming later: balanced ledger, bank reconciliation, offline OCR, AI suggestions, secure bank integrations and encrypted backup.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TransactionEditor(initial: Entry?, onDismiss:()->Unit, onSave:(Entry)->Unit, onDelete:(Entry)->Unit) {
    val ctx=LocalContext.current
    var date by remember(initial) { mutableStateOf(initial?.date?:today()) }
    var merchant by remember(initial) { mutableStateOf(initial?.merchant?:"") }
    var amount by remember(initial) { mutableStateOf(initial?.let{ "%.2f".format(java.util.Locale.US,it.amountCents/100.0) }?:"") }
    var kind by remember(initial) { mutableStateOf(initial?.kind?:"expense") }
    var category by remember(initial) { mutableStateOf(initial?.category?:"Uncategorized") }
    var explanation by remember(initial) { mutableStateOf(initial?.explanation?:"") }
    var receiptPath by remember(initial) { mutableStateOf(initial?.receiptPath?:"") }
    var pendingPhoto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val photoLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if(ok) receiptPath=pendingPhoto else {
            if(pendingPhoto.isNotBlank()) File(pendingPhoto).delete()
            pendingPhoto=""
        }
    }
    val takePhoto={
        val dir=File(ctx.filesDir,"receipts").apply { mkdirs() }
        val file=File(dir,"receipt-${UUID.randomUUID()}.jpg")
        pendingPhoto=file.absolutePath
        val uri=FileProvider.getUriForFile(ctx,"${ctx.packageName}.files",file)
        photoLauncher.launch(uri)
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"New transaction" else "Edit transaction")},text={
        Column(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=date,onValueChange={date=it},label={Text("Date YYYY-MM-DD")},singleLine=true)
            OutlinedTextField(value=merchant,onValueChange={merchant=it},label={Text("Merchant or payer")},singleLine=true)
            OutlinedTextField(value=amount,onValueChange={amount=it},label={Text("Amount in dollars")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=kind=="expense",onClick={kind="expense"},label={Text("Expense")})
                FilterChip(selected=kind=="income",onClick={kind="income"},label={Text("Income")})
            }
            OutlinedTextField(value=category,onValueChange={category=it},label={Text("Category")},singleLine=true)
            OutlinedTextField(value=explanation,onValueChange={explanation=it},label={Text("Business purpose / explanation")},minLines=2)
            OutlinedButton(onClick=takePhoto) { Text(if(receiptPath.isNotBlank()) "Replace receipt photo ✓" else "Photograph receipt (optional)") }
            if(receiptPath.isNotBlank()) Text("Receipt image stored locally.",style=MaterialTheme.typography.labelSmall)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            if(initial!=null) TextButton(onClick={onDelete(initial)}) { Text("Delete transaction",color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton(onClick={
        val cents=parseMoneyCents(amount)
        if(cents==null || cents<=0 || merchant.isBlank() || category.isBlank() || runCatching{LocalDate.parse(date)}.isFailure)
            error="Enter a valid date, merchant, category, and positive amount."
        else onSave(Entry(initial?.id?:0,date,merchant.trim(),cents,kind,category.trim(),explanation.trim(),receiptPath))
    }){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
