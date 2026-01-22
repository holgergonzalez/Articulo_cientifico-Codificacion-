package com.example.disen

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.disen.ui.theme.DisenTheme
import java.util.Locale
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember      // 👈 NUEVO
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalUriHandler

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.material3.TopAppBarDefaults


// ---------------------- TTS MANAGER ----------------------

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
        }
    }

    /**
     * Modo clásico: habla siempre el texto recibido.
     */
    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
    }

    /**
     * Modo toggle:
     * - Si actualmente está hablando → se detiene.
     * - Si está en silencio → comienza a leer el texto.
     */
    fun toggleSpeak(text: String) {
        val engine = tts ?: return
        if (engine.isSpeaking) {
            engine.stop()
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

class ProgressManager(context: Context) {

    private val prefs = context.getSharedPreferences("progreso_eval", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_SCORE = "last_score"
        private const val KEY_BEST_SCORE = "best_score"
        private const val KEY_TIMES_COMPLETED = "times_completed"
    }

    fun getLastScore(): Int = prefs.getInt(KEY_LAST_SCORE, 0)
    fun getBestScore(): Int = prefs.getInt(KEY_BEST_SCORE, 0)
    fun getTimesCompleted(): Int = prefs.getInt(KEY_TIMES_COMPLETED, 0)

    fun registerResult(score: Int) {
        val currentBest = getBestScore()
        val newBest = maxOf(currentBest, score)
        val newTimes = getTimesCompleted() + 1

        prefs.edit()
            .putInt(KEY_LAST_SCORE, score)
            .putInt(KEY_BEST_SCORE, newBest)
            .putInt(KEY_TIMES_COMPLETED, newTimes)
            .apply()
    }
    // 🔹 NUEVO: borrar todo el progreso
    // 🔹 Limpia todo el progreso
    fun clearProgress() {
        prefs.edit()
            .putInt(KEY_LAST_SCORE, 0)
            .putInt(KEY_BEST_SCORE, 0)
            .putInt(KEY_TIMES_COMPLETED, 0)
            .apply()
    }
}

// ---------------------- ACTIVITY ----------------------

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var progressManager: ProgressManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ttsManager = TextToSpeechManager(this)
        progressManager = ProgressManager(this)

        // 🔥 Cada vez que se crea la Activity, dejamos el progreso en 0
        progressManager.clearProgress()

        setContent {
            DisenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiscapacidadSensorialApp(
                        ttsManager = ttsManager,
                        progressManager = progressManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
// ---------------------- NAVEGACIÓN ----------------------

sealed class Screen(val route: String, val titulo: String) {
    object MainMenu : Screen("main_menu", "Menú principal")
    object Introduccion : Screen("introduccion", "Introducción")

    object Visual : Screen("visual", "Discapacidad visual")
    object Visual2 : Screen("visual_2", "Discapacidad visual - página 2")

    object Visual3 : Screen("visual_3", "Discapacidad visual - página 3")

    object Auditiva : Screen("auditiva", "Discapacidad auditiva - página 1")
    object Auditiva2 : Screen("auditiva_2", "Discapacidad auditiva - página 2")
    object Auditiva3 : Screen("auditiva_3", "Discapacidad auditiva - página 3")

    object Tacto : Screen("tacto", "Discapacidad táctil")
    object Tacto2 : Screen("tacto_2", "Tecnología háptica")
    object Tacto3 : Screen("tacto_3", "Tecnología Braille digital")

    object Herramientas : Screen("herramientas", "Herramientas de apoyo")

    // 👇 EVALUACIÓN: 5 preguntas
    object Evaluacion : Screen("evaluacion_1", "Evaluación - Pregunta 1")
    object Evaluacion2 : Screen("evaluacion_2", "Evaluación - Pregunta 2")
    object Evaluacion3 : Screen("evaluacion_3", "Evaluación - Pregunta 3")
    object Evaluacion4 : Screen("evaluacion_4", "Evaluación - Pregunta 4")
    object Evaluacion5 : Screen("evaluacion_5", "Evaluación - Pregunta 5")

    // Resultados finales de la evaluación
    object Resultados : Screen("resultados", "Resultados")
}




@Composable
fun DiscapacidadSensorialApp(
    ttsManager: TextToSpeechManager,
    progressManager: ProgressManager
) {
    val navController = rememberNavController()

    // Puntaje actual (0–10)
    val score = remember { mutableStateOf(0) }

    // 🔹 Estados observables con el progreso guardado
    val lastScore = remember { mutableStateOf(progressManager.getLastScore()) }
    val bestScore = remember { mutableStateOf(progressManager.getBestScore()) }
    val timesCompleted = remember { mutableStateOf(progressManager.getTimesCompleted()) }
    NavHost(
        navController = navController,
        startDestination = Screen.MainMenu.route
    ) {
        composable(Screen.MainMenu.route) {
            MainMenuScreen(
                navController = navController,
                ttsManager = ttsManager,
                lastScore = lastScore.value,
                bestScore = bestScore.value,
                timesCompleted = timesCompleted.value
            )
        }
        composable(Screen.Introduccion.route) {
            IntroduccionScreen(navController, ttsManager)
        }
        composable(Screen.Herramientas.route) {
            HerramientasScreen(navController, ttsManager)
        }
        composable(Screen.Visual.route) {
            VisualScreen(navController, ttsManager)
        }
        composable(Screen.Visual2.route) {
            Visual2Screen(navController, ttsManager)
        }
        composable(Screen.Visual3.route) {
            Visual3Screen(navController, ttsManager)
        }

        composable(Screen.Auditiva.route) {
            AuditivaScreen(navController, ttsManager)
        }
        composable(Screen.Auditiva2.route) {
            Auditiva2Screen(navController, ttsManager)
        }
        composable(Screen.Auditiva3.route) {
            Auditiva3Screen(navController, ttsManager)
        }

        composable(Screen.Tacto.route) {
            TactoScreen(navController, ttsManager)
        }
        composable(Screen.Tacto2.route) {
            Tacto2Screen(navController, ttsManager)
        }

        composable(Screen.Tacto3.route) {
            Tacto3Screen(navController, ttsManager)
        }

// 👇 Añade esto
        composable(Screen.Evaluacion.route) {
            EvaluacionScreen(navController)
        }
    }
}



// ---------------------- MENÚ PRINCIPAL ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager,
    lastScore: Int,
    bestScore: Int,
    timesCompleted: Int
){
    DisposableEffect(Unit) {
        onDispose { ttsManager.stop() }
    }

    val mensajeBienvenida = """
        Bienvenido a la aplicación sobre discapacidad sensorial.
        Desde esta pantalla principal puedes acceder a la introducción teórica,
        a las herramientas de apoyo y a una evaluación para comprobar lo aprendido.
    """.trimIndent()
    val uriHandler = LocalUriHandler.current

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.mor),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Discapacidad sensorial",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "DISEN",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )


            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // 🔹 Fila de logos: UNESCO y UPS
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)   // azul pastel
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.unesco),
                                contentDescription = "Logo UNESCO",
                                modifier = Modifier
                                    .height(80.dp) // o el tamaño que estés usando
                                    .clickable {
                                        uriHandler.openUri("https://catedraunescoinclusion.org/")

                                    } .padding(2.dp),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Image(
                                painter = painterResource(id = R.drawable.upsu),
                                contentDescription = "Logo UPS",
                                modifier = Modifier
                                    .height(80.dp) // o el tamaño que estés usando
                                    .clickable {
                                        uriHandler.openUri("https://www.ups.edu.ec/en/home")

                                    } .padding(2.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }


                    Spacer(modifier = Modifier.height(12.dp))

                    // 🔹 Animación de entrada de la Card
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 600)
                        ) + slideInVertically(
                            initialOffsetY = { it / 8 }
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                Text(
                                    text = "Aplicación educativa e informativa para la comprensión de la discapacidad sensorial",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(24.dp))
// 🔹 Resumen de progreso
                                Image(
                                    painter = painterResource(id = R.drawable.img),
                                    contentDescription = "Iconos discapacidad sensorial",
                                    modifier = Modifier
                                        .height(72.dp)
                                        .fillMaxWidth(),
                                    contentScale = ContentScale.Fit
                                )


                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { ttsManager.toggleSpeak(mensajeBienvenida) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("HABLAR")
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                MenuCard(
                                    titulo = "Introducción",
                                    descripcion = "Conceptos básicos y contexto sobre la discapacidad sensorial."
                                ) {
                                    navController.navigate(Screen.Introduccion.route)
                                }

                                MenuCard(
                                    titulo = "Herramientas",
                                    descripcion = "Recursos y estrategias de apoyo para el trabajo docente."
                                ) {
                                    navController.navigate(Screen.Herramientas.route)
                                }

                                MenuCard(
                                    titulo = "Evaluación",
                                    descripcion = "Cuestionario para valorar los conocimientos adquiridos."
                                ) {
                                    navController.navigate(Screen.Evaluacion.route)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ---------------------- COMPONENTES REUTILIZABLES ----------------------

@Composable
fun MenuCard(
    titulo: String,
    descripcion: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
@Composable
fun HerramientaButton(
    texto: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE6E0E0),  // gris clarito tipo App Inventor
            contentColor = Color.Black
        )
    ) {
        Text(
            text = texto,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaContenido(
    title: String,
    descripcion: String,
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Volver al menú")
            }
        }
    }
}

// ---------------------- PANTALLAS DE CONTENIDO ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroduccionScreen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    // Texto de introducción (centrado y reutilizable)
    val textoIntroduccion = """
        La discapacidad sensorial incluye diversas condiciones que afectan uno o varios de los sentidos principales, 
        como la visión, la audición, el tacto, el gusto o el olfato, pero las más reconocidas son la discapacidad visual, 
        la cual afecta a la capacidad de ver, y la discapacidad auditiva, que afecta a la capacidad de oír. 
        Estos tipos de discapacidades pueden ser congénitas o adquiridas y tienen un impacto significativo en la forma 
        en que las personas interactúan con su entorno y procesan la información. 
        
        Por ello, es importante aclarar que las discapacidades sensoriales, bajo un tratamiento y acompañamiento adecuado, 
        no impiden a una persona llevar una vida casi normal. 
        De hecho, puede ser igual o incluso más exitosa que la de otro individuo que no debe lidiar con este desafío.
    """.trimIndent()

    // Cuando salimos de esta pantalla, detenemos cualquier lectura
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🔹 Fondo con imagen introf.jpg
        Image(
            painter = painterResource(id = R.drawable.introf),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Introducción",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )

            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,      // 🎨 color más vivo
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer       // texto que contraste bien
                    )
                )
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // 🔹 Texto centrado
                        Text(
                            text = textoIntroduccion,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth()
                        )


                        Spacer(modifier = Modifier.height(16.dp))

                        // 🔹 Imagen debajo del texto (imaintro.jpg)
                        Image(
                            painter = painterResource(id = R.drawable.sensor),
                            contentDescription = "Ilustración sobre discapacidad sensorial",
                            modifier = Modifier
                                .height(160.dp)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 🔹 Botón HABLAR (lee la introducción)
                        Button(
                            onClick = { ttsManager.toggleSpeak(textoIntroduccion) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("HABLAR INTRODUCCIÓN")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 🔹 Botón Volver al menú
                        Button(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Volver al menú")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualScreen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    // Texto profesional que se mostrará y que leerá el botón HABLAR
    val textoVisual = """
        1. Lectores de pantalla
        
        Los lectores de pantalla constituyen una de las herramientas tecnológicas más relevantes 
        para las personas con discapacidad visual. Su función principal es transformar la información 
        presentada en la pantalla en voz sintetizada o en una salida braille, permitiendo la navegación, 
        la interacción y la comprensión de contenidos digitales.
        
        Funcionalidad:
        • Conversión de texto y elementos visuales en descripciones audibles.
        • Facilitación de la lectura de documentos, aplicaciones y sitios web.
        • Uso autónomo del sistema operativo y de plataformas educativas.
        
        Software recomendado:
        NVDA (NonVisual Desktop Access) es uno de los lectores de pantalla más utilizados a nivel mundial. 
        Es gratuito, de código abierto, cuenta con soporte completo en español y es compatible con la mayoría 
        de aplicaciones educativas. Puede descargarse desde el sitio oficial https://nvda.es.
    """.trimIndent()

    // Al salir de esta pantalla, se corta cualquier lectura
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🔹 Fondo específico para visual
        Image(
            painter = painterResource(id = R.drawable.visual),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )



        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad Visual",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )


            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                // 🔹 Card con texto profesional + imagen NVDA
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),   // fondo muy claro
                        contentColor = Color(0xFF111111)      // texto oscuro (negro casi)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {

                        // TÍTULO PRINCIPAL
                        Text(
                            text = "1. Lectores de pantalla",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // PÁRRAFO PRINCIPAL
                        Text(
                            text = "Los lectores de pantalla constituyen una de las herramientas tecnológicas más relevantes para las personas con discapacidad visual. Su función principal es transformar la información presentada en la pantalla en voz sintetizada o en una salida braille, permitiendo la navegación, la interacción y la comprensión de contenidos digitales.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // SUBTÍTULO
                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // VIÑETAS
                        Text(
                            text = "• Conversión de texto y elementos visuales en descripciones audibles.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Facilita la lectura de documentos, aplicaciones y sitios web.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Permite el uso autónomo del sistema operativo y plataformas educativas.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // SUBTÍTULO
                        Text(
                            text = "Software recomendado: NVDA",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // TEXTO PROFESIONAL DE NVDA
                        Text(
                            text = "NVDA (NonVisual Desktop Access) es uno de los lectores de pantalla más utilizados a nivel mundial. Es gratuito, de código abierto, cuenta con soporte completo en español y es compatible con la mayoría de aplicaciones educativas.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // IMAGEN NVDA
                        Image(
                            painter = painterResource(id = R.drawable.nvda),

                            contentDescription = "Logo NVDA",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(4.dp))


                        // ENLACE

                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Botón HABLAR (lee el textoVisual completo)
                Button(
                    onClick = { ttsManager.toggleSpeak(textoVisual) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 Fila con REGRESAR y SIGUIENTE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.navigate(Screen.Herramientas.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("REGRESAR")
                    }

                    Button(
                        onClick = { navController.navigate(Screen.Visual2.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SIGUIENTE")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Visual2Screen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    // Texto que se mostrará y que leerá el botón HABLAR
    val textoVisual2 = """
        2. Ampliadores de pantalla
        
        Los ampliadores de pantalla son herramientas que aumentan el tamaño del texto y de las imágenes 
        que se muestran en el monitor, mejorando la legibilidad para personas con baja visión.
        
        Funcionalidad:
        • Permiten ampliar el contenido sin perder referencia del contexto de la pantalla.
        • Facilitan la lectura de documentos, páginas web y materiales educativos.
        • Suelen incorporar opciones de alto contraste y personalización del cursor.
        
        Aplicación recomendada:
        ZoomText es un software que combina ampliación de pantalla y lector de texto para 
        personas con baja visión. Es ampliamente utilizado en contextos educativos y laborales, 
        y se encuentra disponible a través del sitio web de Freedom Scientific.
    """.trimIndent()

    // Al salir de esta pantalla, se detiene cualquier lectura
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🔹 Fondo igual que en la primera página de visual
        Image(
            painter = painterResource(id = R.drawable.visual),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad Visual",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                // 🔹 Card con el texto profesional + imagen ZoomText
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),   // fondo claro
                        contentColor = Color(0xFF111111)      // texto oscuro
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {

                        // TÍTULO
                        Text(
                            text = "2. Ampliadores de pantalla",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // PÁRRAFO PRINCIPAL
                        Text(
                            text = "Los ampliadores de pantalla son herramientas que aumentan el tamaño del texto y de las imágenes que se muestran en el monitor, mejorando la legibilidad para personas con baja visión.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // SUBTÍTULO
                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // VIÑETAS
                        Text(
                            text = "• Permiten ampliar el contenido sin perder la referencia del contexto de la pantalla.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Facilitan la lectura de documentos, páginas web y materiales educativos.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Suelen incluir opciones de alto contraste y personalización del cursor.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // SUBTÍTULO
                        Text(
                            text = "Aplicación recomendada: ZoomText",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ZoomText es un software que combina ampliación de pantalla y lector de texto para personas con baja visión. Es ampliamente utilizado en contextos educativos y laborales, y se encuentra disponible en el sitio web de Freedom Scientific.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // IMAGEN de ZoomText dentro de la card
                        Image(
                            painter = painterResource(id = R.drawable.zoomtext),
                            contentDescription = "Interfaz de ZoomText",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Botón HABLAR (lee textoVisual2)
                Button(
                    onClick = { ttsManager.toggleSpeak(textoVisual2) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 Fila de navegación: REGRESAR (página 1) y HERRAMIENTAS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.navigate(Screen.Visual.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("REGRESAR") }

                    Button(
                        onClick = { navController.navigate(Screen.Visual3.route) }, // ✅ NUEVO
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("SIGUIENTE") }
                }

            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Visual3Screen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    var hablando by remember { mutableStateOf(false) }
    val textoOcr = """
        3. Reconocimiento óptico de caracteres (OCR)
        
        El OCR (Optical Character Recognition) es una tecnología que permite convertir texto presente en imágenes,
        fotografías o documentos escaneados en texto digital. Esto facilita que el contenido pueda ser leído por
        voz (TTS) o accesible mediante lectores de pantalla, incluso cuando el texto original no es seleccionable.
        
        Funcionalidad:
        • Convierte imágenes en texto digital (apuntes, libros, carteles, etiquetas).
        • Facilita la lectura de documentos escaneados (por ejemplo, PDFs tipo “foto”).
        • Mejora la autonomía y el acceso rápido a información del entorno.
        
        Software recomendado: Seeing AI (Microsoft)
        Seeing AI utiliza la cámara del dispositivo para reconocer texto y leerlo en voz alta, además de asistir
        en la identificación de contenido visual del entorno. Es una opción práctica para escenarios educativos
        y cotidianos.
    """.trimIndent()

    // ✅ Al salir, se corta cualquier lectura
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
            hablando = false
        }
    }



    Box(modifier = Modifier.fillMaxSize()) {

        // ✅ Mismo fondo que Visual y Visual2 (visual.jpg)
        Image(
            painter = painterResource(id = R.drawable.visual),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad Visual",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),
                        contentColor = Color(0xFF111111)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "3. Reconocimiento óptico de caracteres (OCR)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "El OCR (Optical Character Recognition) es una tecnología que permite convertir texto presente en imágenes, fotografías o documentos escaneados en texto digital. Esto facilita que el contenido pueda ser leído por voz (TTS) o accesible mediante lectores de pantalla, incluso cuando el texto original no es seleccionable.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("• Convierte imágenes en texto digital (apuntes, libros, carteles, etiquetas).")
                        Text("• Facilita la lectura de documentos escaneados (por ejemplo, PDFs tipo “foto”).")
                        Text("• Mejora la autonomía y el acceso rápido a información del entorno.")

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Software recomendado: Seeing AI (Microsoft)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Seeing AI utiliza la cámara del dispositivo para reconocer texto y leerlo en voz alta, además de asistir en la identificación de contenido visual del entorno. Es una opción práctica para escenarios educativos y cotidianos.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ✅ Imagen dentro de la card: see.jpg
                        Image(
                            painter = painterResource(id = R.drawable.see),
                            contentDescription = "Seeing AI / OCR",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ✅ HABLAR
                Button(
                    onClick = {
                        if (hablando) {
                            ttsManager.stop()
                            hablando = false
                        } else {
                            ttsManager.speak(textoOcr)
                            hablando = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ✅ REGRESAR a Visual2 + HERRAMIENTAS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.popBackStack() }, // vuelve a Visual2
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("REGRESAR") }

                    Button(
                        onClick = { navController.navigate(Screen.Herramientas.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("HERRAMIENTAS") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditivaScreen(   // Página 1: subtitulación automática
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    val textoAuditiva1 = """
        1. Subtitulación automática
        
        La subtitulación automática permite generar subtítulos en tiempo real a partir del contenido oral. 
        Es especialmente útil para estudiantes con discapacidad auditiva que necesitan apoyo visual para 
        comprender explicaciones, exposiciones o videos educativos.
        
        Funcionalidad:
        • Produce subtítulos en tiempo real para mejorar la comprensión del contenido oral.
        • Facilita el seguimiento de clases grabadas o en línea.
        
        Aplicación recomendada:
        En YouTube existe una función que genera subtítulos automáticos para los videos. 
        El usuario puede activarlos desde el menú de configuración de cada video, ajustando el idioma 
        y el estilo de visualización según sus necesidades.
    """.trimIndent()

    DisposableEffect(Unit) {
        onDispose { ttsManager.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.naranja),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad Auditiva",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),
                        contentColor = Color(0xFF111111)
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "1. Subtitulación automática",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "La subtitulación automática permite generar subtítulos en tiempo real a partir del contenido oral, apoyando la comprensión de estudiantes con discapacidad auditiva.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Justify
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Produce subtítulos en tiempo real.", style = MaterialTheme.typography.bodyMedium)
                        Text("• Mejora la comprensión del contenido oral en clases y videos.", style = MaterialTheme.typography.bodyMedium)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Aplicación: YouTube",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "YouTube incorpora subtítulos automáticos que pueden activarse desde el menú de configuración de cada video, permitiendo ajustar el idioma y el estilo de visualización.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Justify
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Image(
                            painter = painterResource(id = R.drawable.auditivouno),
                            contentDescription = "Ejemplo de subtítulos automáticos",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { ttsManager.toggleSpeak(textoAuditiva1) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // REGRESAR siempre a la sección de herramientas
                    Button(
                        onClick = { navController.navigate(Screen.Herramientas.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("REGRESAR")
                    }

                    // SIGUIENTE → página 2 auditiva
                    Button(
                        onClick = { navController.navigate(Screen.Auditiva2.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SIGUIENTE")
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Auditiva2Screen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    // Texto que se muestra y que leerá el botón HABLAR
    val textoAuditiva2 = """
        2. Sistemas de Bucle de Inducción
        
        Funcionalidad:
        Transmiten el sonido directamente a audífonos o implantes cocleares, 
        excluyendo en gran medida el ruido de fondo y mejorando la inteligibilidad del habla.
        
        Herramienta:
        La T-coil (bobina telefónica) es una tecnología que permite que el dispositivo auditivo 
        reciba de forma directa la señal de un sistema de bucle de inducción instalado en el aula, 
        auditorio u otro espacio educativo. 
        Para aprovecharla, es necesario activar el modo T-coil en los audífonos o implantes cocleares 
        y asegurarse de que el lugar esté equipado con un sistema de bucle de inducción correctamente configurado.
    """.trimIndent()

    // Al salir de esta pantalla se detiene cualquier lectura
    DisposableEffect(Unit) {
        onDispose { ttsManager.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🔹 Fondo naranja
        Image(
            painter = painterResource(id = R.drawable.naranja),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad Auditiva",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                // 🔹 Card con texto + imagen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),   // fondo claro
                        contentColor = Color(0xFF111111)      // texto oscuro
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {

                        Text(
                            text = "2. Sistemas de Bucle de Inducción",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Los sistemas de bucle de inducción transmiten el sonido directamente a audífonos o implantes cocleares, reduciendo el ruido de fondo y mejorando la claridad del mensaje.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "• Transmiten el sonido directamente al dispositivo auditivo.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Disminuyen el ruido ambiente y facilitan la comprensión del habla.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Herramienta: T-coil",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "La función T-coil permite que el audífono o implante coclear reciba la señal del sistema de bucle de inducción instalado en el entorno. Es importante activar el modo T-coil en el dispositivo y confirmar que el aula o salón cuente con este sistema.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 🔹 Imagen debajo del texto (auditico2.jpg)
                        Image(
                            painter = painterResource(id = R.drawable.auditico2),
                            contentDescription = "Ejemplo de uso de sistema de bucle de inducción",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Botón HABLAR (lee textoAuditiva2)
                Button(
                    onClick = { ttsManager.toggleSpeak(textoAuditiva2) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 Fila de botones: REGRESAR (pág. 1) y SIGUIENTE (pág. 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.navigate(Screen.Auditiva.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("REGRESAR")
                    }

                    Button(
                        onClick = { navController.navigate(Screen.Auditiva3.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SIGUIENTE")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Auditiva3Screen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    // Texto que se muestra y que leerá el botón HABLAR
    val textoAuditiva3 = """
        3. Aplicaciones de transcripción
        
        Funcionalidad:
        Convierte el habla en texto en tiempo real, facilitando la comunicación 
        en entornos ruidosos o silenciosos y permitiendo que las personas con 
        discapacidad auditiva sigan la conversación de forma escrita.
        
        Aplicación:
        Ava es una aplicación que transcribe conversaciones en tiempo real y está 
        disponible de forma gratuita en la Play Store para Android y en la App Store 
        para iOS. Cuenta además con opciones de suscripción para acceder a funciones 
        avanzadas, como mayor tiempo de transcripción, vocabulario personalizado y 
        herramientas de colaboración.
    """.trimIndent()

    // Al salir de esta pantalla, se detiene cualquier lectura
    DisposableEffect(Unit) {
        onDispose { ttsManager.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🔹 Fondo naranja
        Image(
            painter = painterResource(id = R.drawable.naranja),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad Auditiva",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                // 🔹 Card con texto + imagen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),   // fondo claro
                        contentColor = Color(0xFF111111)      // texto oscuro
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {

                        Text(
                            text = "3. Aplicaciones de transcripción",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Las aplicaciones de transcripción convierten el habla en texto en tiempo real, lo que facilita la comunicación en entornos ruidosos o silenciosos.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "• Convierte el habla en texto en tiempo real.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Ayuda a seguir conversaciones, clases y reuniones a través de subtítulos escritos.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Aplicación: Ava",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Ava Live Captions permite transcribir conversaciones en tiempo real y está disponible en Android y iOS. Ofrece funciones gratuitas básicas y planes de suscripción con herramientas avanzadas.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 🔹 Imagen debajo del texto (auditivo3.png)
                        Image(
                            painter = painterResource(id = R.drawable.auditivo3),
                            contentDescription = "Ejemplo de aplicación de transcripción Ava",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Botón HABLAR (lee textoAuditiva3)
                Button(
                    onClick = { ttsManager.toggleSpeak(textoAuditiva3) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 Fila de botones: REGRESAR (pág. 2) y HERRAMIENTAS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.navigate(Screen.Auditiva2.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("REGRESAR")
                    }

                    Button(
                        onClick = { navController.navigate(Screen.Herramientas.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("HERRAMIENTAS")
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TactoScreen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    val textoTacto = """
        Dispositivos hápticos
        
        Funcionalidad:
        Los dispositivos hápticos ofrecen retroalimentación táctil mediante vibraciones o pulsos, 
        mejorando la interacción con dispositivos electrónicos y permitiendo que la persona reciba 
        información a través del sentido del tacto.
        
        Herramienta:
        Los sistemas de vibración integrados en la mayoría de smartphones actuales constituyen 
        un ejemplo de tecnología háptica. A través de ajustes de accesibilidad es posible activar 
        o personalizar estas vibraciones para notificaciones, alertas o interacciones específicas, 
        facilitando el uso del dispositivo por parte de personas con discapacidad sensorial.
    """.trimIndent()

    // ✅ Al salir de la pantalla se detiene cualquier lectura en curso
    DisposableEffect(Unit) {
        onDispose { ttsManager.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🔹 Fondo: tacto.jpg
        Image(
            painter = painterResource(id = R.drawable.tacto),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad del Tacto",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                // 🔹 Card con texto + imagen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),
                        contentColor = Color(0xFF111111)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {

                        Text(
                            text = "Dispositivos hápticos",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Los dispositivos hápticos proporcionan retroalimentación táctil mediante vibraciones o pulsos, favoreciendo la interacción con equipos y recursos digitales.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text("• Ofrecen vibraciones o pulsos que el usuario percibe mediante el tacto.")
                        Text("• Permiten recibir notificaciones y alertas sin depender exclusivamente de la visión o la audición.")

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Herramienta: retroalimentación háptica en smartphones",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "La mayoría de los teléfonos inteligentes incorporan motores de vibración que pueden configurarse desde el menú de accesibilidad, adaptando la intensidad y el patrón de vibración según las necesidades del usuario.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Image(
                            painter = painterResource(id = R.drawable.brazo),
                            contentDescription = "Ejemplo de interacción háptica",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ✅ HABLAR (grande, blanco)
                Button(
                    onClick = { ttsManager.toggleSpeak(textoTacto) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ✅ REGRESAR + SIGUIENTE (como tu imagen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // REGRESAR -> Herramientas
                    Button(
                        onClick = {
                            ttsManager.stop()
                            navController.navigate(Screen.Herramientas.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF67B7E5),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("REGRESAR", fontWeight = FontWeight.SemiBold)
                    }

                    // SIGUIENTE -> Tacto2
                    Button(
                        onClick = {
                            ttsManager.stop()
                            navController.navigate(Screen.Tacto2.route)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF67B7E5),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("SIGUIENTE", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tacto2Screen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    val textoHaptica = """
        2. Tecnología háptica (retroalimentación por vibración)
        
        Los sistemas hápticos permiten transmitir información mediante vibraciones o pulsos táctiles, 
        mejorando la interacción con dispositivos electrónicos en contextos educativos y cotidianos. 
        Esta tecnología resulta útil para reforzar instrucciones, confirmar acciones y guiar al usuario 
        mediante señales táctiles, favoreciendo la autonomía y la accesibilidad.
        
        Funcionalidad:
        • Proporciona retroalimentación táctil inmediata mediante vibración o pulsos.
        • Apoya la orientación y confirmación de acciones (por ejemplo: “correcto/incorrecto”, “seleccionado”, “alerta”).
        • Permite diseñar patrones de vibración que representen distintos mensajes o estados.
        
        Software recomendado: Vibro (Vibration App / Haptic Patterns)
        Vibro permite crear y probar distintos patrones de vibración. Es útil para definir señales táctiles diferenciadas.
    """.trimIndent()

    var hablando by remember { mutableStateOf(false) }

    // ✅ Al salir, se corta cualquier lectura
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
            hablando = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ✅ Fondo: el mismo de TactoScreen
        Image(
            painter = painterResource(id = R.drawable.tacto),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad del Tacto",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),
                        contentColor = Color(0xFF111111)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {

                        Text(
                            text = "2. Tecnología háptica (retroalimentación por vibración)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Los sistemas hápticos permiten transmitir información mediante vibraciones o pulsos táctiles, mejorando la interacción con dispositivos electrónicos en contextos educativos y cotidianos. Esta tecnología resulta útil para reforzar instrucciones, confirmar acciones y guiar al usuario mediante señales táctiles, favoreciendo la autonomía y la accesibilidad.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("• Proporciona retroalimentación táctil inmediata mediante vibración o pulsos.")
                        Text("• Apoya la orientación y confirmación de acciones (correcto/incorrecto, alerta, seleccionado).")
                        Text("• Permite diseñar patrones de vibración para representar mensajes o estados.")

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Software recomendado: Vibro",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Vibro permite crear y probar distintos patrones de vibración. Es útil para definir señales táctiles diferenciadas, aportando una alternativa accesible basada en el tacto.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ✅ Imagen dentro de la card
                        Image(
                            painter = painterResource(id = R.drawable.vibro),
                            contentDescription = "Vibro / Háptica",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ✅ HABLAR con toggle
                Button(
                    onClick = {
                        if (hablando) {
                            ttsManager.stop()
                            hablando = false
                        } else {
                            ttsManager.speak(textoHaptica)
                            hablando = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) { Text("HABLAR") }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.popBackStack() }, // vuelve a TactoScreen
                        modifier = Modifier.weight(1f)
                    ) { Text("REGRESAR") }

                    Button(
                        onClick = { navController.navigate(Screen.Tacto3.route) },
                        modifier = Modifier.weight(1f)
                    ) { Text("SIGUIENTE") }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tacto3Screen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    val textoBraille = """
        3. Tecnología Braille digital (líneas Braille / teclados Braille)
        
        La tecnología braille digital permite leer y escribir mediante braille utilizando dispositivos como líneas braille 
        o teclados braille conectados al teléfono móvil o a una computadora. Esta solución facilita el acceso a contenidos 
        académicos y digitales, permitiendo una interacción táctil más precisa.
        
        Funcionalidad:
        • Permite la lectura táctil de textos mediante una línea braille conectada al móvil o PC.
        • Facilita la escritura utilizando teclado braille, mejorando la comunicación y producción académica.
        • Apoya la navegación accesible en aplicaciones, documentos y páginas web.
        
        Software recomendado: TalkBack + BrailleBack (Android)
        TalkBack, junto con BrailleBack, permite compatibilidad con pantallas braille en Android para lectura y escritura.
    """.trimIndent()

    var hablando by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
            hablando = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.tacto),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Herramientas para Discapacidad del Tacto",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.55f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFD),
                        contentColor = Color(0xFF111111)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {

                        Text(
                            text = "3. Tecnología Braille digital (líneas Braille / teclados Braille)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "La tecnología braille digital permite leer y escribir mediante braille utilizando dispositivos como líneas braille o teclados braille conectados al teléfono móvil o a una computadora. Esta solución facilita el acceso a contenidos académicos y digitales, permitiendo una interacción táctil más precisa.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Funcionalidad",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("• Permite la lectura táctil de textos mediante una línea braille conectada al móvil o PC.")
                        Text("• Facilita la escritura con teclado braille, mejorando comunicación y producción académica.")
                        Text("• Apoya la navegación accesible en apps, documentos y páginas web.")

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Software recomendado: TalkBack + BrailleBack (Android)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "TalkBack, junto con BrailleBack, permite compatibilidad con pantallas braille en Android, facilitando lectura y escritura en braille dentro del dispositivo.",
                            textAlign = TextAlign.Justify,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Image(
                            painter = painterResource(id = R.drawable.braile),
                            contentDescription = "Braille digital",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (hablando) {
                            ttsManager.stop()
                            hablando = false
                        } else {
                            ttsManager.speak(textoBraille)
                            hablando = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) { Text("HABLAR") }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { navController.popBackStack() }, // regresa a Tacto2
                        modifier = Modifier.weight(1f)
                    ) { Text("REGRESAR") }

                    Button(
                        onClick = { navController.navigate(Screen.Herramientas.route) },
                        modifier = Modifier.weight(1f)
                    ) { Text("HERRAMIENTAS") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerramientasScreen(
    navController: NavHostController,
    ttsManager: TextToSpeechManager
) {
    // Texto que se leerá al tocar HABLAR: describe la pantalla
    val textoHerramientas = """
        Esta sección presenta herramientas TIC para apoyar a estudiantes con discapacidad sensorial.
        Aquí encontrarás recursos orientados a la discapacidad visual, auditiva y táctil.
    """.trimIndent()

    // Al salir de esta pantalla, se corta cualquier lectura
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🔹 Fondo con imagen específica de herramientas
        Image(
            painter = painterResource(id = R.drawable.herramientafond),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Herramientas TIC para\nDiscapacidad Sensorial",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )

                        )
                    }
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 BOTÓN HABLAR: SOLO LEE EL TEXTO (NO NAVEGA)
                Button(
                    onClick = { ttsManager.toggleSpeak(textoHerramientas) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("HABLAR")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Card con texto descriptivo + imagen
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = textoHerramientas,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Image(
                            painter = painterResource(id = R.drawable.herramientas),
                            contentDescription = "Ilustración de herramientas TIC",
                            modifier = Modifier
                                .height(160.dp)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Botón: Herramientas para Discapacidad Visual
                Button(
                    onClick = { navController.navigate(Screen.Visual.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Herramientas para Discapacidad Visual",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 Botón: Herramientas para Discapacidad Auditiva
                Button(
                    onClick = { navController.navigate(Screen.Auditiva.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF9A825),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Herramientas para Discapacidad Auditiva",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 Botón: Herramientas para Discapacidad del Tacto
                Button(
                    onClick = { navController.navigate(Screen.Tacto.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1565C0),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Herramientas para Discapacidad del Tacto",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 🔹 REGRESAR: SIEMPRE AL MENÚ PRINCIPAL
                Button(
                    onClick = {
                        navController.navigate(Screen.MainMenu.route) {
                            popUpTo(Screen.MainMenu.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("REGRESAR")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluacionScreen(navController: NavHostController) {

    // 👉 URL de tu formulario
    val formUrl = "https://forms.gle/LzUYyq25JSdPDr9Y8"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Evaluación",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // 🌐 WebView embebido con el Google Forms
            AndroidView(
                modifier = Modifier
                    .weight(1f)              // ocupa todo el alto disponible
                    .fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true       // Forms necesita JS
                        webViewClient = WebViewClient()         // que se abra dentro de la app
                        loadUrl(formUrl)                        // cargamos el formulario
                    }
                }
            )

            // 🔙 Botón para volver al menú principal
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("Volver al menú principal")
            }
        }
    }
}
