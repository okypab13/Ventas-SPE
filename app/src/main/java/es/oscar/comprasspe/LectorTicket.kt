package es.oscar.comprasspe

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.abs

data class Lectura(val total: Double?, val fecha: LocalDate?, val articulos: List<Articulo>)

/** Convierte "14,67", "1.234,56" o "14.67" en número. */
fun parseImporte(s: String): Double? {
    var t = s.trim().replace(" ", "").replace("€", "")
    if (t.isEmpty()) return null
    if (t.contains(',')) t = t.replace(".", "").replace(',', '.')
    return t.toDoubleOrNull()
}

object LectorTicket {

    // Importes con dos decimales; evita trozos de fechas como 21.10.26
    private val IMPORTE =
        Regex("""(?<![\d.,])-?(?:\d{1,3}(?:\.\d{3})+,\d{2}|\d+[.,]\d{2})(?![\d.,])""")
    private val FECHA = Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})(?!\d)""")
    private val NO_ES_TOTAL = listOf("bruto", "impuesto", "iva", "descuento", "recargo", "subtotal")
    private val PIE = listOf("importe", "total", "descuento", "recargo", "impuesto", "base", "iva", "subtotal")

    suspend fun leer(ctx: Context, uri: Uri): Lectura {
        val imagen = InputImage.fromFilePath(ctx, uri)
        val resultado = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use {
            it.process(imagen).await()
        }
        val lineas = resultado.textBlocks.flatMap { it.lines }
        val articulos = try { buscarArticulos(lineas) } catch (e: Exception) { emptyList() }
        return Lectura(buscarTotal(lineas), buscarFecha(resultado.text), articulos)
    }

    private fun normal(s: String): String =
        Normalizer.normalize(s.lowercase(ES), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

    private fun importes(s: String): List<Double> =
        IMPORTE.findAll(s).mapNotNull { parseImporte(it.value) }.toList()

    private fun esEtiquetaTotal(s: String): Boolean {
        val t = normal(s)
        return t.startsWith("total") && NO_ES_TOTAL.none { t.contains(it) }
    }

    /** Busca la línea "Total" y el importe que tiene al lado (misma altura, a la derecha). */
    private fun buscarTotal(lineas: List<Text.Line>): Double? {
        val etiqueta = lineas.lastOrNull { esEtiquetaTotal(it.text) }
        if (etiqueta != null) {
            importes(etiqueta.text).lastOrNull()?.let { return it }
            val caja = etiqueta.boundingBox
            if (caja != null) {
                val cy = caja.exactCenterY()
                val alto = caja.height().coerceAtLeast(1)
                val alLado = lineas
                    .filter { it !== etiqueta }
                    .mapNotNull { l ->
                        val b = l.boundingBox ?: return@mapNotNull null
                        val v = importes(l.text).lastOrNull() ?: return@mapNotNull null
                        val dy = abs(b.exactCenterY() - cy)
                        if (dy <= alto * 1.2f && b.left >= caja.left) dy to v else null
                    }
                    .minByOrNull { it.first }
                if (alLado != null) return alLado.second
                val debajo = lineas
                    .filter { it !== etiqueta }
                    .mapNotNull { l ->
                        val b = l.boundingBox ?: return@mapNotNull null
                        val v = importes(l.text).lastOrNull() ?: return@mapNotNull null
                        val dy = b.exactCenterY() - cy
                        if (dy > 0 && dy <= alto * 2.5f) dy to v else null
                    }
                    .minByOrNull { it.first }
                if (debajo != null) return debajo.second
            }
        }
        return lineas.flatMap { importes(it.text) }.maxOrNull()
    }

    private fun buscarFecha(texto: String): LocalDate? =
        FECHA.findAll(texto).firstNotNullOfOrNull { m ->
            val (d, mes, a) = m.destructured
            var anyo = a.toInt()
            if (anyo < 100) anyo += 2000
            runCatching { LocalDate.of(anyo, mes.toInt(), d.toInt()) }
                .getOrNull()
                ?.takeIf { it.year in 2020..2100 && !it.isAfter(LocalDate.now().plusDays(1)) }
        }

    /** Un trozo de texto con su posición en la foto. */
    private class Pieza(val texto: String, val caja: Rect) {
        val cy: Float get() = caja.exactCenterY()
        val alto: Int get() = caja.height().coerceAtLeast(1)
    }

    /**
     * Lee la tabla de artículos usando las cabeceras Artículo, Unidad y Cantidad como columnas.
     * Lo que caiga bajo otras cabeceras (precio, importe…) se ignora.
     */
    private fun buscarArticulos(lineas: List<Text.Line>): List<Articulo> {
        val piezas = lineas.flatMap { l ->
            l.elements.mapNotNull { e -> e.boundingBox?.let { Pieza(e.text, it) } }
        }
        val cabArt = piezas.firstOrNull { normal(it.texto).startsWith("articul") } ?: return emptyList()
        val filaCabecera = piezas.filter { abs(it.cy - cabArt.cy) <= cabArt.alto * 0.8f }

        // Columnas de izquierda a derecha: 0 artículo, 1 unidad, 2 cantidad, -1 otra (se ignora)
        val columnas = filaCabecera.map { p ->
            val t = normal(p.texto)
            val tipo = when {
                p === cabArt -> 0
                t.startsWith("unidad") || t == "ud" || t == "uds" -> 1
                t.startsWith("cantidad") || t == "cant" || t == "cant." -> 2
                else -> -1
            }
            p to tipo
        }.sortedBy { it.first.caja.left }

        val inicio = filaCabecera.maxOf { it.caja.bottom }
        val pie = lineas
            .filter { l -> (l.boundingBox?.top ?: -1) > inicio && PIE.any { normal(l.text).startsWith(it) } }
            .minOfOrNull { it.boundingBox!!.top } ?: Int.MAX_VALUE

        val cuerpo = piezas
            .filter { p -> filaCabecera.none { it === p } && p.cy > inicio && p.cy < pie }
            .sortedBy { it.cy }

        // Agrupar trozos en filas por altura
        val filas = mutableListOf<MutableList<Pieza>>()
        for (p in cuerpo) {
            val ultima = filas.lastOrNull()
            if (ultima != null && abs(p.cy - ultima.first().cy) <= ultima.first().alto * 0.6f) ultima.add(p)
            else filas.add(mutableListOf(p))
        }

        val tolerancia = cabArt.alto
        val resultado = mutableListOf<Articulo>()
        for (fila in filas) {
            val textos = arrayOf(StringBuilder(), StringBuilder(), StringBuilder())
            for (p in fila.sortedBy { it.caja.left }) {
                val tipo = columnas.lastOrNull { p.caja.left >= it.first.caja.left - tolerancia }?.second
                    ?: columnas.first().second
                if (tipo < 0) continue
                val sb = textos[tipo]
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(p.texto)
            }
            val art = textos[0].toString().trim()
            val uni = textos[1].toString().trim()
            val can = textos[2].toString().trim()
            if (!art.any { it.isLetterOrDigit() } && uni.isEmpty() && can.isEmpty()) continue
            if (uni.isEmpty() && can.isEmpty() && resultado.isNotEmpty()) {
                // Nombre partido en dos líneas: se une al artículo anterior
                val prev = resultado.removeAt(resultado.lastIndex)
                resultado.add(prev.copy(articulo = (prev.articulo + " " + art).trim()))
                continue
            }
            resultado.add(Articulo(art, uni, can))
        }
        return resultado
    }
}
