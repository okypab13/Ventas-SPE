package es.oscar.comprasspe

import android.Manifest
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private val ACERO = Color(0xFF1F3A5F)
private val AMARILLO = Color(0xFFF2B705)
private val TINTA = Color(0xFF16202B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { Tema { App() } }
    }
}

@Composable
fun Tema(content: @Composable () -> Unit) {
    val esquema = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = AMARILLO, onPrimary = TINTA,
            background = Color(0xFF10161D), onBackground = Color(0xFFE6EBF0),
            surface = Color(0xFF18212B), onSurface = Color(0xFFE6EBF0),
            onSurfaceVariant = Color(0xFF94A1AE), error = Color(0xFFF97066)
        )
    } else {
        lightColorScheme(
            primary = ACERO, onPrimary = Color.White,
            background = Color(0xFFEEF1F4), onBackground = TINTA,
            surface = Color.White, onSurface = TINTA,
            onSurfaceVariant = Color(0xFF5B6875), error = Color(0xFFB42318)
        )
    }
    MaterialTheme(colorScheme = esquema, content = content)
}

/** Lo que se está revisando o editando. */
class EditorState(
    val id: String?,
    fecha: LocalDate,
    total: String,
    nota: String,
    val foto: Uri?,
    val creado: Long
) {
    var fecha by mutableStateOf(fecha)
    var total by mutableStateOf(total)
    var nota by mutableStateOf(nota)
    var leyendo by mutableStateOf(false)
    var aviso by mutableStateOf<String?>(null)
    var error by mutableStateOf(false)

    companion object {
        fun de(c: Compra) = EditorState(c.id, c.fecha, importeTexto(c.total), c.nota, null, c.creado)
        fun nueva(foto: Uri? = null) =
            EditorState(null, LocalDate.now(), "", "", foto, System.currentTimeMillis())
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx).also { limpiarFotosViejas(ctx) } }
    var compras by remember { mutableStateOf(store.load()) }
    val actual = remember { periodoDe(LocalDate.now()) }
    var periodo by rememberSaveable { mutableStateOf(actual) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var fotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = fotoUri
        if (ok && uri != null) {
            val st = EditorState.nueva(uri)
            st.leyendo = true
            editor = st
            scope.launch {
                val r = try {
                    LectorTicket.leer(ctx, uri)
                } catch (e: Exception) {
                    null
                }
                st.leyendo = false
                when {
                    r == null -> {
                        st.error = true
                        st.aviso = "No se ha podido leer la foto. Escribe el total a mano."
                    }
                    r.total == null -> {
                        r.fecha?.let { st.fecha = it }
                        st.error = true
                        st.aviso = "No se ha encontrado el total. Prueba con otra foto más nítida o escríbelo a mano."
                    }
                    else -> {
                        st.total = importeTexto(r.total)
                        r.fecha?.let { st.fecha = it }
                        st.aviso = "Ticket leído. Comprueba el total y la fecha antes de guardar." +
                            if (r.fecha == null) " No se ha encontrado la fecha: se ha puesto la de hoy." else ""
                    }
                }
            }
        }
    }

    fun abrirCamara() {
        val f = File(ctx.cacheDir, "ticket_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        fotoUri = u
        tomarFoto.launch(u)
    }

    val pedirCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) abrirCamara()
        else Toast.makeText(ctx, "Sin permiso de cámara. Puedes añadir la compra a mano.", Toast.LENGTH_LONG).show()
    }

    val st = editor
    if (st != null) {
        Editor(
            st = st,
            onCancel = { editor = null },
            onSave = { c ->
                compras = compras.filter { it.id != c.id } + c
                store.saveAll(compras)
                periodo = periodoDe(c.fecha)
                editor = null
            },
            onDelete = { id ->
                compras = compras.filter { it.id != id }
                store.saveAll(compras)
                editor = null
            }
        )
    } else {
        Principal(
            compras = compras,
            periodo = periodo,
            actual = actual,
            onPeriodo = { periodo = it },
            onFoto = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) abrirCamara() else pedirCamara.launch(Manifest.permission.CAMERA)
            },
            onManual = { editor = EditorState.nueva() },
            onEditar = { editor = EditorState.de(it) },
            onExportar = { exportarCsv(ctx, periodo, it) }
        )
    }
}

@Composable
fun Principal(
    compras: List<Compra>,
    periodo: YearMonth,
    actual: YearMonth,
    onPeriodo: (YearMonth) -> Unit,
    onFoto: () -> Unit,
    onManual: () -> Unit,
    onEditar: (Compra) -> Unit,
    onExportar: (List<Compra>) -> Unit
) {
    val lista = compras
        .filter { periodoDe(it.fecha) == periodo }
        .sortedWith(compareByDescending<Compra> { it.fecha }.thenByDescending { it.creado })
    val total = lista.sumOf { it.total }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Fotografiar ticket", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                icon = { Icon(painterResource(R.drawable.ic_camara), contentDescription = null) },
                onClick = onFoto,
                containerColor = AMARILLO,
                contentColor = TINTA,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { Cabecera(periodo, actual, total, lista.size, onPeriodo) }
            item {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onManual) { Text("Añadir a mano") }
                    if (lista.isNotEmpty()) {
                        OutlinedButton(onClick = { onExportar(lista) }) { Text("Exportar CSV") }
                    }
                }
            }
            if (lista.isEmpty()) {
                item {
                    Text(
                        if (periodo == actual) "Aún no hay compras en este periodo. Fotografía un ticket para empezar."
                        else "No hubo compras en este periodo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
            }
            items(lista, key = { it.id }) { c -> Fila(c) { onEditar(c) } }
            item { Spacer(Modifier.navigationBarsPadding().height(110.dp)) }
        }
    }
}

@Composable
fun Cabecera(p: YearMonth, actual: YearMonth, total: Double, n: Int, onPeriodo: (YearMonth) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ACERO)
            .statusBarsPadding()
            .padding(start = 18.dp, end = 8.dp, top = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(nombrePeriodo(p), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(rango(p), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            TextButton(onClick = { onPeriodo(p.minusMonths(1)) }) {
                Text("‹", color = Color.White, fontSize = 28.sp)
            }
            TextButton(onClick = { onPeriodo(p.plusMonths(1)) }, enabled = p != actual) {
                Text("›", color = Color.White.copy(alpha = if (p != actual) 1f else 0.3f), fontSize = 28.sp)
            }
        }
        Text(
            euros(total),
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        ) {
            Text(
                when (n) { 0 -> "Sin compras"; 1 -> "1 compra"; else -> "$n compras" },
                color = Color.White.copy(alpha = 0.9f)
            )
            if (p == actual) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "Periodo abierto",
                    color = TINTA,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(AMARILLO, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
        }
    }
    // Franja discontinua amarilla, como una cinta de seguridad
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRect(ACERO)
        val w = 14.dp.toPx()
        val g = 8.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(AMARILLO, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w + g
        }
    }
    }
}

@Composable
fun Fila(c: Compra, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surface,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${c.fecha.dayOfMonth}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(MES3[c.fecha.monthValue - 1], fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                c.nota.ifBlank { "Compra SPE" },
                color = if (c.nota.isBlank()) cs.onSurfaceVariant else cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(euros(c.total), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Editor(
    st: EditorState,
    onCancel: () -> Unit,
    onSave: (Compra) -> Unit,
    onDelete: (String) -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    BackHandler { onCancel() }

    var foto by remember(st.foto) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(st.foto) {
        val u = st.foto
        if (u != null) foto = withContext(Dispatchers.IO) { cargarMiniatura(ctx, u) }
    }
    var confirmar by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (st.id == null) "Revisar compra" else "Editar compra",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }

        foto?.let {
            Image(
                bitmap = it,
                contentDescription = "Foto del ticket",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            )
        }

        if (st.leyendo) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Leyendo el ticket…", color = cs.onBackground)
            }
        }
        st.aviso?.let {
            Text(
                it,
                color = if (st.error) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Text("Fecha del ticket", fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    ctx,
                    { _, y, m, d -> st.fecha = LocalDate.of(y, m + 1, d) },
                    st.fecha.year, st.fecha.monthValue - 1, st.fecha.dayOfMonth
                ).show()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) { Text(fechaLarga(st.fecha), fontSize = 17.sp) }
        Text(
            "Cuenta para la " + nombrePeriodo(periodoDe(st.fecha)).replaceFirstChar { it.lowercase() },
            fontSize = 14.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        OutlinedTextField(
            value = st.total,
            onValueChange = { v -> st.total = v.filter { it.isDigit() || it == ',' || it == '.' || it == '-' } },
            label = { Text("Total pagado (€)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        OutlinedTextField(
            value = st.nota,
            onValueChange = { st.nota = it },
            label = { Text("Qué has comprado (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (st.id != null) {
                TextButton(onClick = { confirmar = true }) { Text("Borrar compra", color = cs.error) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val t = parseImporte(st.total)
                    if (t == null) {
                        st.error = true
                        st.aviso = "Pon el total pagado."
                    } else {
                        onSave(Compra(st.id ?: nuevoId(), st.fecha, t, st.nota.trim(), st.creado))
                    }
                },
                enabled = !st.leyendo,
                colors = ButtonDefaults.buttonColors(containerColor = AMARILLO, contentColor = TINTA)
            ) { Text("Guardar compra", fontWeight = FontWeight.SemiBold) }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Borrar esta compra?") },
            text = { Text("Se borrará la compra del ${fechaLarga(st.fecha)}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    st.id?.let(onDelete)
                }) { Text("Borrar", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}

fun cargarMiniatura(ctx: Context, uri: Uri): ImageBitmap? {
    return try {
        val cr = ctx.contentResolver
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
        var escala = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / (escala * 2) >= 1200) escala *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = escala })
        } ?: return null
        val giro = cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val girada = if (giro != 0) {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(giro.toFloat()) }, true)
        } else bmp
        girada.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

fun limpiarFotosViejas(ctx: Context) {
    val limite = System.currentTimeMillis() - 24L * 3600 * 1000
    ctx.cacheDir.listFiles()
        ?.filter { it.name.startsWith("ticket_") && it.lastModified() < limite }
        ?.forEach { it.delete() }
}

fun exportarCsv(ctx: Context, p: YearMonth, lista: List<Compra>) {
    val sb = StringBuilder("\uFEFFFecha;Concepto;Total\n")
    lista.sortedBy { it.fecha }.forEach {
        val f = "%02d/%02d/%d".format(it.fecha.dayOfMonth, it.fecha.monthValue, it.fecha.year)
        sb.append(f).append(";\"").append(it.nota.replace("\"", "\"\"")).append("\";")
            .append(importeTexto(it.total)).append('\n')
    }
    sb.append(";Total ").append(nombrePeriodo(p).lowercase(ES)).append(';')
        .append(importeTexto(lista.sumOf { it.total })).append('\n')
    val archivo = File(ctx.cacheDir, "compras-spe-%d-%02d.csv".format(p.year, p.monthValue))
    archivo.writeText(sb.toString())
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", archivo)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Compras SPE: " + nombrePeriodo(p))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(envio, "Enviar compras"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No hay ninguna app para enviar el archivo.", Toast.LENGTH_LONG).show()
    }
}
