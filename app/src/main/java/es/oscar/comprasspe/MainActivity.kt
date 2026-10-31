package es.oscar.comprasspe

import android.Manifest
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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

class ArticuloEdit(a: String, u: String, c: String) {
    var articulo by mutableStateOf(a)
    var unidad by mutableStateOf(u)
    var cantidad by mutableStateOf(c)
}

/** Lo que se está revisando o editando. */
class EditorState(
    val id: String?,
    fecha: LocalDate,
    total: String,
    val nota: String,
    val fotoNueva: Uri?,
    val fotoGuardada: String?,
    val creado: Long,
    iniciales: List<Articulo>
) {
    var fecha by mutableStateOf(fecha)
    var total by mutableStateOf(total)
    val articulos = mutableStateListOf<ArticuloEdit>().apply {
        iniciales.forEach { add(ArticuloEdit(it.articulo, it.unidad, it.cantidad)) }
    }
    var quitarFoto by mutableStateOf(false)
    var leyendo by mutableStateOf(false)
    var guardando by mutableStateOf(false)
    var aviso by mutableStateOf<String?>(null)
    var error by mutableStateOf(false)

    fun tieneFoto() = fotoNueva != null || fotoGuardada != null

    fun fotoVisible(ctx: Context): Uri? =
        if (quitarFoto) null else fotoNueva ?: fotoGuardada?.let { Fotos.uri(ctx, it) }

    fun articulosLimpios(): List<Articulo> = articulos
        .map { Articulo(it.articulo.trim(), it.unidad.trim(), it.cantidad.trim()) }
        .filter { it.articulo.isNotEmpty() || it.cantidad.isNotEmpty() }

    companion object {
        fun de(c: Compra) = EditorState(
            c.id, c.fecha, importeTexto(c.total), c.nota, null, c.foto, c.creado, c.articulos
        )
        fun nueva(foto: Uri? = null) = EditorState(
            null, LocalDate.now(), "", "", foto, null, System.currentTimeMillis(), emptyList()
        )
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx).also { limpiarTemporales(ctx) } }
    var compras by remember { mutableStateOf(store.load()) }
    val actual = remember { periodoDe(LocalDate.now()) }
    var periodo by rememberSaveable { mutableStateOf(actual) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var fotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    fun guardarLista(nueva: List<Compra>) {
        compras = nueva
        store.saveAll(nueva)
    }

    /** Abre la revisión con la foto y lee el ticket. */
    fun leerTicket(uri: Uri) {
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
            if (r == null) {
                st.error = true
                st.aviso = "No se ha podido leer la foto. Rellena los datos a mano; la foto se guardará igualmente."
                return@launch
            }
            r.fecha?.let { st.fecha = it }
            r.total?.let { st.total = importeTexto(it) }
            st.articulos.clear()
            r.articulos.forEach { st.articulos.add(ArticuloEdit(it.articulo, it.unidad, it.cantidad)) }
            val faltan = buildList {
                if (r.total == null) add("el total")
                if (r.fecha == null) add("la fecha (se ha puesto la de hoy)")
                if (r.articulos.isEmpty()) add("los artículos")
            }
            st.error = r.total == null
            st.aviso = if (faltan.isEmpty()) "Ticket leído. Comprueba los datos antes de guardar."
            else "No se ha encontrado " + faltan.joinToString(", ") + ". Compruébalo y complétalo a mano."
        }
    }

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = fotoUri
        if (ok && uri != null) leerTicket(uri)
    }

    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { origen ->
        if (origen != null) {
            scope.launch {
                val copia = withContext(Dispatchers.IO) { copiarATemporal(ctx, origen) }
                if (copia != null) leerTicket(copia)
                else Toast.makeText(ctx, "No se ha podido abrir la foto.", Toast.LENGTH_LONG).show()
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
            onSave = { total ->
                scope.launch {
                    st.guardando = true
                    val id = st.id ?: nuevoId()
                    var foto: String? = if (st.quitarFoto) null else st.fotoGuardada
                    val nueva = st.fotoNueva
                    if (nueva != null && !st.quitarFoto) {
                        val nombre = withContext(Dispatchers.IO) { Fotos.guardar(ctx, nueva, id) }
                        if (nombre != null) foto = nombre
                        else Toast.makeText(ctx, "No se ha podido guardar la foto.", Toast.LENGTH_LONG).show()
                    }
                    val vieja = st.fotoGuardada
                    if (vieja != null && vieja != foto) Fotos.borrar(ctx, vieja)
                    val c = Compra(id, st.fecha, total, st.nota, st.creado, st.articulosLimpios(), foto)
                    guardarLista(compras.filter { it.id != id } + c)
                    periodo = periodoDe(c.fecha)
                    editor = null
                }
            },
            onDelete = {
                val id = st.id
                if (id != null) {
                    st.fotoGuardada?.let { Fotos.borrar(ctx, it) }
                    guardarLista(compras.filter { it.id != id })
                }
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
            onGaleria = {
                elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onManual = { editor = EditorState.nueva() },
            onEditar = { editor = EditorState.de(it) },
            onExportar = { exportarCsv(ctx, periodo, it) },
            onBorrarFotos = { lista ->
                val ids = lista.map { it.id }.toSet()
                lista.forEach { c -> c.foto?.let { Fotos.borrar(ctx, it) } }
                guardarLista(compras.map { if (it.id in ids) it.copy(foto = null) else it })
            }
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
    onGaleria: () -> Unit,
    onManual: () -> Unit,
    onEditar: (Compra) -> Unit,
    onExportar: (List<Compra>) -> Unit,
    onBorrarFotos: (List<Compra>) -> Unit
) {
    val lista = compras
        .filter { periodoDe(it.fecha) == periodo }
        .sortedWith(compareByDescending<Compra> { it.fecha }.thenByDescending { it.creado })
    val total = lista.sumOf { it.total }
    var verFotos by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("Hacer foto", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                    icon = { Icon(painterResource(R.drawable.ic_camara), contentDescription = null) },
                    onClick = onFoto,
                    containerColor = AMARILLO,
                    contentColor = TINTA
                )
                ExtendedFloatingActionButton(
                    text = { Text("Galería", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                    icon = { Icon(painterResource(R.drawable.ic_galeria), contentDescription = null) },
                    onClick = onGaleria,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { Cabecera(periodo, actual, total, lista.size, onPeriodo) }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onManual) { Text("Añadir a mano") }
                    if (lista.isNotEmpty()) {
                        OutlinedButton(onClick = { onExportar(lista) }) { Text("Exportar CSV") }
                    }
                    OutlinedButton(onClick = { verFotos = true }) { Text("Fotos") }
                }
            }
            if (lista.isEmpty()) {
                item {
                    Text(
                        if (periodo == actual) "Aún no hay compras en este periodo. Haz una foto al ticket o elige una de la galería."
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

    if (verFotos) {
        GestionFotos(compras, actual, onClose = { verFotos = false }, onBorrar = {
            onBorrarFotos(it)
            verFotos = false
        })
    }
}

@Composable
fun GestionFotos(
    compras: List<Compra>,
    actual: YearMonth,
    onClose: () -> Unit,
    onBorrar: (List<Compra>) -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val conFoto = compras.filter { it.foto != null }
    val anteriores = conFoto.filter { periodoDe(it.fecha) < actual }
    val megas = remember { Fotos.espacio(ctx) / 1048576.0 }
    var confirmar by remember { mutableStateOf<List<Compra>?>(null) }

    val pendiente = confirmar
    if (pendiente != null) {
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(if (pendiente.size == 1) "¿Borrar 1 foto?" else "¿Borrar ${pendiente.size} fotos?") },
            text = { Text("Las compras se mantienen; solo se borran las fotos.") },
            confirmButton = {
                TextButton(onClick = { confirmar = null; onBorrar(pendiente) }) { Text("Borrar fotos", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Fotos guardadas") },
        text = {
            Column {
                Text(
                    when (conFoto.size) {
                        0 -> "No hay fotos guardadas."
                        1 -> "1 foto, ocupa " + String.format(ES, "%.1f MB.", megas)
                        else -> "${conFoto.size} fotos, ocupan " + String.format(ES, "%.1f MB.", megas)
                    }
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { confirmar = anteriores }, enabled = anteriores.isNotEmpty()) {
                    Text("Borrar las de nóminas anteriores (${anteriores.size})")
                }
                TextButton(onClick = { confirmar = conFoto }, enabled = conFoto.isNotEmpty()) {
                    Text("Borrar todas las fotos", color = if (conFoto.isNotEmpty()) cs.error else cs.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } }
    )
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
    val texto = resumen(c)
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
            Column(Modifier.weight(1f)) {
                Text(
                    texto.ifBlank { "Compra SPE" },
                    color = if (texto.isBlank()) cs.onSurfaceVariant else cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (c.articulos.isNotEmpty() || c.foto != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.articulos.isNotEmpty()) {
                            Text(
                                if (c.articulos.size == 1) "1 artículo" else "${c.articulos.size} artículos",
                                fontSize = 13.sp, color = cs.onSurfaceVariant
                            )
                        }
                        if (c.foto != null) {
                            if (c.articulos.isNotEmpty()) Spacer(Modifier.width(8.dp))
                            Icon(
                                painterResource(R.drawable.ic_camara),
                                contentDescription = "Con foto",
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(euros(c.total), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Editor(
    st: EditorState,
    onCancel: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    BackHandler { onCancel() }

    val fotoUri = st.fotoVisible(ctx)
    var miniatura by remember(fotoUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(fotoUri) {
        miniatura = if (fotoUri != null) withContext(Dispatchers.IO) { decodificar(ctx, fotoUri, 1200)?.asImageBitmap() } else null
    }
    var confirmar by remember { mutableStateOf(false) }
    var ampliar by remember { mutableStateOf(false) }

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

        miniatura?.let {
            Image(
                bitmap = it,
                contentDescription = "Foto del ticket. Toca para ampliar",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clickable { ampliar = true }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Toca la foto para ampliarla", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { st.quitarFoto = true }) { Text("Quitar foto", color = cs.error) }
            }
        }
        if (st.quitarFoto && st.tieneFoto()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("La foto se borrará al guardar.", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { st.quitarFoto = false }) { Text("Deshacer") }
            }
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

        Text(
            "Artículos",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        if (st.articulos.isEmpty() && !st.leyendo) {
            Text("Sin artículos.", color = cs.onSurfaceVariant, fontSize = 14.sp)
        }
        st.articulos.forEachIndexed { i, a ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cs.surface,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = a.articulo,
                            onValueChange = { a.articulo = it },
                            label = { Text("Artículo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { st.articulos.removeAt(i) }) {
                            Text("×", fontSize = 24.sp, color = cs.error)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = a.unidad,
                            onValueChange = { a.unidad = it },
                            label = { Text("Unidad") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = a.cantidad,
                            onValueChange = { a.cantidad = it },
                            label = { Text("Cantidad") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { st.articulos.add(ArticuloEdit("", "", "")) },
            modifier = Modifier.padding(top = 10.dp)
        ) { Text("Añadir artículo") }

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
                    } else onSave(t)
                },
                enabled = !st.leyendo && !st.guardando,
                colors = ButtonDefaults.buttonColors(containerColor = AMARILLO, contentColor = TINTA)
            ) { Text(if (st.guardando) "Guardando…" else "Guardar compra", fontWeight = FontWeight.SemiBold) }
        }
    }

    if (ampliar && fotoUri != null) VisorFoto(fotoUri) { ampliar = false }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Borrar esta compra?") },
            text = { Text("Se borrará la compra del ${fechaLarga(st.fecha)} y su foto.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    onDelete()
                }) { Text("Borrar", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}

/** Foto a pantalla completa; se amplía pellizcando. */
@Composable
fun VisorFoto(uri: Uri, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var img by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) { img = withContext(Dispatchers.IO) { decodificar(ctx, uri, 2400)?.asImageBitmap() } }
    var escala by remember { mutableFloatStateOf(1f) }
    var desplaza by remember { mutableStateOf(Offset.Zero) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        escala = (escala * zoom).coerceIn(1f, 6f)
                        desplaza = if (escala == 1f) Offset.Zero else desplaza + pan
                    }
                }
        ) {
            img?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Foto del ticket",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = escala
                            scaleY = escala
                            translationX = desplaza.x
                            translationY = desplaza.y
                        }
                )
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 8.dp)
            ) { Text("Cerrar", color = Color.White, fontSize = 17.sp) }
        }
    }
}

/** Copia la foto elegida de la galería a la app para poder leerla y guardarla. */
fun copiarATemporal(ctx: Context, origen: Uri): Uri? {
    return try {
        val f = File(ctx.cacheDir, "ticket_${System.currentTimeMillis()}.jpg")
        val entrada = ctx.contentResolver.openInputStream(origen) ?: return null
        entrada.use { input -> f.outputStream().use { input.copyTo(it) } }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    } catch (e: Exception) {
        null
    }
}

fun limpiarTemporales(ctx: Context) {
    val limite = System.currentTimeMillis() - 24L * 3600 * 1000
    ctx.cacheDir.listFiles()
        ?.filter { it.name.startsWith("ticket_") && it.lastModified() < limite }
        ?.forEach { it.delete() }
}

fun exportarCsv(ctx: Context, p: YearMonth, lista: List<Compra>) {
    fun campo(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val sb = StringBuilder("\uFEFFFecha;Artículo;Unidad;Cantidad;Total ticket\n")
    lista.sortedBy { it.fecha }.forEach { c ->
        val f = "%02d/%02d/%d".format(c.fecha.dayOfMonth, c.fecha.monthValue, c.fecha.year)
        val arts = c.articulos.ifEmpty { listOf(Articulo(c.nota, "", "")) }
        // El total va solo en la primera línea de cada ticket, así la columna suma bien
        arts.forEachIndexed { i, a ->
            sb.append(f).append(';')
                .append(campo(a.articulo)).append(';')
                .append(campo(a.unidad)).append(';')
                .append(campo(a.cantidad)).append(';')
                .append(if (i == 0) importeTexto(c.total) else "").append('\n')
        }
    }
    sb.append(";;;Total ").append(nombrePeriodo(p).lowercase(ES)).append(';')
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
