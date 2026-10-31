package es.oscar.comprasspe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

data class Compra(
    val id: String,
    val fecha: LocalDate,
    val total: Double,
    val nota: String,
    val creado: Long
)

val ES: Locale = Locale("es", "ES")
val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)
val MES3 = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

fun euros(v: Double): String = String.format(ES, "%,.2f €", v)
fun importeTexto(v: Double): String = String.format(ES, "%.2f", v)
fun fechaLarga(f: LocalDate) = "${f.dayOfMonth} de ${MESES[f.monthValue - 1]} de ${f.year}"

/** Del 21 del mes anterior al 20 del mes en curso: lo del 21 en adelante va a la nómina del mes siguiente. */
fun periodoDe(f: LocalDate): YearMonth =
    if (f.dayOfMonth >= 21) YearMonth.from(f).plusMonths(1) else YearMonth.from(f)

fun nombrePeriodo(p: YearMonth) = "Nómina de ${MESES[p.monthValue - 1]} ${p.year}"

fun rango(p: YearMonth): String {
    val ant = p.minusMonths(1)
    val anyoAnt = if (ant.year != p.year) " de ${ant.year}" else ""
    return "Del 21 de ${MESES[ant.monthValue - 1]}$anyoAnt al 20 de ${MESES[p.monthValue - 1]}"
}

fun nuevoId(): String =
    "c" + System.currentTimeMillis().toString(36) + (1000..9999).random().toString()

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("compras", Context.MODE_PRIVATE)

    fun load(): List<Compra> = try {
        val arr = JSONArray(prefs.getString("lista", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Compra(
                id = o.getString("id"),
                fecha = LocalDate.parse(o.getString("fecha")),
                total = o.getDouble("total"),
                nota = o.optString("nota", ""),
                creado = o.optLong("creado", 0L)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveAll(lista: List<Compra>) {
        val arr = JSONArray()
        lista.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("fecha", it.fecha.toString())
                    .put("total", it.total)
                    .put("nota", it.nota)
                    .put("creado", it.creado)
            )
        }
        prefs.edit().putString("lista", arr.toString()).apply()
    }
}
