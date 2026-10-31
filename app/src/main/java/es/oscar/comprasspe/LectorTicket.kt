package es.oscar.comprasspe

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.math.abs

data class Lectura(val total: Double?, val fecha: LocalDate?)

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

    suspend fun leer(ctx: Context, uri: Uri): Lectura {
        val imagen = InputImage.fromFilePath(ctx, uri)
        val resultado = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use {
            it.process(imagen).await()
        }
        val lineas = resultado.textBlocks.flatMap { it.lines }
        return Lectura(buscarTotal(lineas), buscarFecha(resultado.text))
    }

    private fun importes(s: String): List<Double> =
        IMPORTE.findAll(s).mapNotNull { parseImporte(it.value) }.toList()

    private fun esEtiquetaTotal(s: String): Boolean {
        val t = s.trim().lowercase(ES)
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
                // Por si el importe está justo debajo de la palabra Total
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
        // Último recurso: el importe más alto del ticket
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
}
