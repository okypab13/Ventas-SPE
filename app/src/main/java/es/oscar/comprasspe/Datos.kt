package es.oscar.comprasspe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

data class Articulo(val articulo: String, val unidad: String, val cantidad: String)

data class Compra(
    val id: String,
    val fecha: LocalDate,
    val total: Double,
    val nota: String,
    val creado: Long,
    val articulos: List<Articulo> = emptyList(),
    val foto: String? = null
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

fun resumen(c: Compra): String =
    c.articulos.map { it.articulo }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { c.nota }

fun nuevoId(): String =
    "c" + System.currentTimeMillis().toString(36) + (1000..9999).random().toString()

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("compras", Context.MODE_PRIVATE)

    fun load(): List<Compra> = try {
        val arr = JSONArray(prefs.getString("lista", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val arts = o.optJSONArray("articulos")
            Compra(
                id = o.getString("id"),
                fecha = LocalDate.parse(o.getString("fecha")),
                total = o.getDouble("total"),
                nota = o.optString("nota", ""),
                creado = o.optLong("creado", 0L),
                articulos = if (arts == null) emptyList() else (0 until arts.length()).map { j ->
                    val a = arts.getJSONObject(j)
                    Articulo(a.optString("a", ""), a.optString("u", ""), a.optString("c", ""))
                },
                foto = o.optString("foto", "").ifBlank { null }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveAll(lista: List<Compra>) {
        val arr = JSONArray()
        lista.forEach { c ->
            val arts = JSONArray()
            c.articulos.forEach { arts.put(JSONObject().put("a", it.articulo).put("u", it.unidad).put("c", it.cantidad)) }
            val o = JSONObject()
                .put("id", c.id)
                .put("fecha", c.fecha.toString())
                .put("total", c.total)
                .put("nota", c.nota)
                .put("creado", c.creado)
                .put("articulos", arts)
            if (c.foto != null) o.put("foto", c.foto)
            arr.put(o)
        }
        prefs.edit().putString("lista", arr.toString()).apply()
    }
}

/** Abre una foto reducida y girada según la cámara. */
fun decodificar(ctx: Context, uri: Uri, maxLado: Int): Bitmap? {
    return try {
        val cr = ctx.contentResolver
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
        var escala = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (escala * 2) >= maxLado) escala *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = escala })
        } ?: return null
        val giro = cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (giro != 0) {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(giro.toFloat()) }, true)
        } else bmp
    } catch (e: Exception) {
        null
    }
}

/** Fotos de los tickets, guardadas dentro de la app. */
object Fotos {
    private fun carpeta(ctx: Context) = File(ctx.filesDir, "fotos").apply { mkdirs() }

    fun archivo(ctx: Context, nombre: String) = File(carpeta(ctx), nombre)

    fun uri(ctx: Context, nombre: String): Uri = Uri.fromFile(archivo(ctx, nombre))

    /** Guarda la foto reducida (unos 2000 px, suficiente para leerla) y devuelve su nombre. */
    fun guardar(ctx: Context, origen: Uri, id: String): String? {
        val bmp = decodificar(ctx, origen, 2000) ?: return null
        val nombre = "${id}_${System.currentTimeMillis()}.jpg"
        return try {
            FileOutputStream(archivo(ctx, nombre)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            nombre
        } catch (e: Exception) {
            null
        }
    }

    fun borrar(ctx: Context, nombre: String) {
        archivo(ctx, nombre).delete()
    }

    fun espacio(ctx: Context): Long = carpeta(ctx).listFiles()?.sumOf { it.length() } ?: 0L
}
