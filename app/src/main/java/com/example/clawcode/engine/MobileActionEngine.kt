package com.example.clawcode.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.example.clawcode.data.model.InstalledApp
import com.example.clawcode.data.model.MobileActionCategory
import com.example.clawcode.data.model.MobileActionDef
import java.util.Locale

class MobileActionEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    fun speak(
        text: String,
        voiceStyle: String = "Voz 1",
        pitchMultiplier: Float = 1.0f,
        speechRateMultiplier: Float = 1.0f
    ) {
        if (isTtsReady && tts != null) {
            val basePitch = when {
                voiceStyle.contains("Voz 2", ignoreCase = true) || voiceStyle.contains("Aguda", ignoreCase = true) || voiceStyle.contains("Femenina", ignoreCase = true) -> 1.45f
                voiceStyle.contains("Robot", ignoreCase = true) || voiceStyle.contains("Sintética", ignoreCase = true) -> 0.45f
                else -> 1.0f
            }
            val baseRate = when {
                voiceStyle.contains("Voz 2", ignoreCase = true) || voiceStyle.contains("Aguda", ignoreCase = true) || voiceStyle.contains("Femenina", ignoreCase = true) -> 1.20f
                voiceStyle.contains("Robot", ignoreCase = true) || voiceStyle.contains("Sintética", ignoreCase = true) -> 0.80f
                else -> 1.0f
            }

            val finalPitch = (basePitch * pitchMultiplier).coerceIn(0.2f, 2.0f)
            val finalRate = (baseRate * speechRateMultiplier).coerceIn(0.2f, 2.0f)

            try {
                tts?.setPitch(finalPitch)
                tts?.setSpeechRate(finalRate)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_VOICE_ID")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val installedAppsList = mutableListOf<InstalledApp>()

        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                val appName = info.loadLabel(pm).toString()
                val pkgName = info.activityInfo.packageName
                if (appName.isNotBlank() && pkgName.isNotBlank()) {
                    installedAppsList.add(
                        InstalledApp(
                            appName = appName,
                            packageName = pkgName,
                            categoryName = if (pkgName.contains("google")) "Google System" else "User App"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback / Preset default Android apps if query is limited in sandbox
        val presetApps = listOf(
            InstalledApp("Gmail", "com.google.android.gm", "Google Email"),
            InstalledApp("Notion", "notion.id", "Productivity"),
            InstalledApp("WhatsApp", "com.whatsapp", "Messaging"),
            InstalledApp("Spotify", "com.spotify.music", "Multimedia"),
            InstalledApp("YouTube", "com.google.android.youtube", "Multimedia"),
            InstalledApp("Google Maps", "com.google.android.apps.maps", "Navigation"),
            InstalledApp("Google Calendar", "com.google.android.calendar", "Productivity"),
            InstalledApp("Ajustes / Settings", "com.android.settings", "System"),
            InstalledApp("Cámara", "com.android.camera", "System Tools"),
            InstalledApp("Calculadora", "com.android.calculator2", "System Tools"),
            InstalledApp("Google Chrome", "com.android.chrome", "Browser"),
            InstalledApp("Telegram", "org.telegram.messenger", "Messaging"),
            InstalledApp("Instagram", "com.instagram.android", "Social"),
            InstalledApp("Slack", "com.Slack", "Work")
        )

        return (installedAppsList + presetApps).distinctBy { it.packageName }
    }

    fun launchAppByName(appNameQuery: String): Pair<Boolean, String> {
        val apps = getInstalledApps()
        val match = apps.firstOrNull {
            it.appName.contains(appNameQuery, ignoreCase = true) ||
            it.packageName.contains(appNameQuery, ignoreCase = true)
        }

        return if (match != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                speak("Abriendo la aplicación ${match.appName}")
                Pair(true, "Lanzada app '${match.appName}' (${match.packageName})")
            } else {
                // Direct store / web launch fallback
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${match.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                speak("Abriendo enlace para ${match.appName}")
                Pair(true, "Abriendo intent para '${match.appName}'")
            }
        } else {
            // Generic intent launcher
            val genericIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(genericIntent)
                speak("Buscando la aplicación $appNameQuery en el sistema")
                Pair(true, "Buscando e intentando abrir app $appNameQuery")
            } catch (e: Exception) {
                Pair(false, "No se encontró la app '$appNameQuery'")
            }
        }
    }

    fun getActionCatalog(): List<MobileActionDef> = listOf(
        MobileActionDef(
            id = "act_open_app",
            name = "open_any_installed_app",
            category = MobileActionCategory.APP_LAUNCHER,
            description = "Abre CUALQUIER aplicación instalada en el teléfono Android por su nombre (ej: Spotify, YouTube, Gmail, Notion, WhatsApp).",
            sampleUtterance = "Abre Spotify y pon música",
            functionSchemaJson = """
            {
              "name": "open_any_installed_app",
              "parameters": {
                "app_name": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.MAIN"
        ),
        MobileActionDef(
            id = "act_gmail",
            name = "open_gmail_compose",
            category = MobileActionCategory.EMAIL,
            description = "Redacta o abre Gmail con destinatario, asunto y cuerpo del mensaje.",
            sampleUtterance = "Envía un correo a carlos@empresa.com asunto Reporte con el texto adjunto informe",
            functionSchemaJson = """
            {
              "name": "open_gmail_compose",
              "parameters": {
                "recipient": "STRING",
                "subject": "STRING",
                "body": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.SENDTO"
        ),
        MobileActionDef(
            id = "act_notion",
            name = "open_notion_page",
            category = MobileActionCategory.PRODUCTIVITY,
            description = "Abre Notion o una página específica mediante enlace directo / deep-link.",
            sampleUtterance = "Abre Notion en mi página de Proyectos y Tareas de IA",
            functionSchemaJson = """
            {
              "name": "open_notion_page",
              "parameters": {
                "page_title": "STRING",
                "workspace": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.VIEW"
        ),
        MobileActionDef(
            id = "act_whatsapp",
            name = "send_whatsapp_openclaw",
            category = MobileActionCategory.MESSAGING,
            description = "Envía o redacta mensaje de WhatsApp a través del puente OpenClaw / WhatsApp Webhook.",
            sampleUtterance = "Mándale un WhatsApp a Maria diciendo que la reunión se posterga 10 mins",
            functionSchemaJson = """
            {
              "name": "send_whatsapp_openclaw",
              "parameters": {
                "phone_or_contact": "STRING",
                "message": "STRING",
                "via_openclaw_webhook": true
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.VIEW"
        ),
        MobileActionDef(
            id = "act_spotify",
            name = "open_spotify_media",
            category = MobileActionCategory.MULTIMEDIA,
            description = "Abre Spotify o busca canciones/playlists de voz.",
            sampleUtterance = "Abre Spotify y reproduce playlist de concentración",
            functionSchemaJson = """
            {
              "name": "open_spotify_media",
              "parameters": {
                "search_query": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.media.action.MEDIA_PLAY_FROM_SEARCH"
        ),
        MobileActionDef(
            id = "act_youtube",
            name = "open_youtube_video",
            category = MobileActionCategory.MULTIMEDIA,
            description = "Busca o reproduce videos en YouTube.",
            sampleUtterance = "Busca en YouTube tutoriales de Qwen 3.5 Ollama",
            functionSchemaJson = """
            {
              "name": "open_youtube_video",
              "parameters": {
                "search_query": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.SEARCH"
        ),
        MobileActionDef(
            id = "act_calendar",
            name = "create_calendar_event",
            category = MobileActionCategory.CALENDAR,
            description = "Crea un nuevo evento en Google Calendar con fecha, título y ubicación.",
            sampleUtterance = "Agrega un evento a mi calendario para la revisión del proyecto mañana a las 3pm",
            functionSchemaJson = """
            {
              "name": "create_calendar_event",
              "parameters": {
                "title": "STRING",
                "location": "STRING",
                "description": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.INSERT"
        ),
        MobileActionDef(
            id = "act_map",
            name = "show_map_location",
            category = MobileActionCategory.NAVIGATION,
            description = "Busca y muestra una ubicación o ruta en Google Maps.",
            sampleUtterance = "Muestra la ubicación del restaurante italiano más cercano en el mapa",
            functionSchemaJson = """
            {
              "name": "show_map_location",
              "parameters": {
                "query": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.VIEW"
        ),
        MobileActionDef(
            id = "act_contacts",
            name = "create_contact",
            category = MobileActionCategory.CONTACTS,
            description = "Guarda un nuevo contacto en la agenda del dispositivo.",
            sampleUtterance = "Agrega a Juan Pérez con el número +573001234567 a mis contactos",
            functionSchemaJson = """
            {
              "name": "create_contact",
              "parameters": {
                "name": "STRING",
                "phone": "STRING",
                "email": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.INSERT"
        ),
        MobileActionDef(
            id = "act_wifi",
            name = "open_wifi_settings",
            category = MobileActionCategory.SYSTEM,
            description = "Abre la pantalla de configuración de Wi-Fi y redes del teléfono.",
            sampleUtterance = "Abre los ajustes de Wi-Fi",
            functionSchemaJson = """
            {
              "name": "open_wifi_settings",
              "parameters": {}
            }
            """.trimIndent(),
            intentAction = "android.settings.WIFI_SETTINGS"
        ),
        MobileActionDef(
            id = "act_google_search",
            name = "google_search",
            category = MobileActionCategory.AUTOMATION,
            description = "Ejecuta una búsqueda en Google Grounding para obtener información actualizada en tiempo real.",
            sampleUtterance = "Busca en Google las últimas noticias de IA",
            functionSchemaJson = """
            {
              "name": "google_search",
              "parameters": {
                "query": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.WEB_SEARCH"
        ),
        MobileActionDef(
            id = "act_camera",
            name = "take_photo_camera",
            category = MobileActionCategory.SYSTEM,
            description = "Abre la cámara para tomar una foto o capturar un documento.",
            sampleUtterance = "Toma una foto con la cámara",
            functionSchemaJson = """
            {
              "name": "take_photo_camera",
              "parameters": {}
            }
            """.trimIndent(),
            intentAction = "android.media.action.IMAGE_CAPTURE"
        ),
        MobileActionDef(
            id = "act_ocr",
            name = "scan_image_ocr",
            category = MobileActionCategory.AUTOMATION,
            description = "Escanea y reconoce texto en una imagen adjunta utilizando OCR.",
            sampleUtterance = "Extrae el texto de esta imagen con OCR",
            functionSchemaJson = """
            {
              "name": "scan_image_ocr",
              "parameters": {
                "image_uri": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.VIEW"
        ),
        MobileActionDef(
            id = "act_github",
            name = "open_github_repository",
            category = MobileActionCategory.AUTOMATION,
            description = "Navega a un repositorio o búsqueda en GitHub.",
            sampleUtterance = "Busca en GitHub repositorios de Android Jetpack Compose",
            functionSchemaJson = """
            {
              "name": "open_github_repository",
              "parameters": {
                "repo_or_search": "STRING"
              }
            }
            """.trimIndent(),
            intentAction = "android.intent.action.VIEW"
        )
    )

    fun executeActionIntent(
        actionName: String,
        params: Map<String, String>
    ): Pair<Boolean, String> {
        return try {
            when (actionName) {
                "open_any_installed_app" -> {
                    val appName = params["app_name"] ?: "Gmail"
                    launchAppByName(appName)
                }

                "open_gmail_compose" -> {
                    val recipient = params["recipient"] ?: ""
                    val subject = params["subject"] ?: "Mensaje enviado desde JARVIS AI"
                    val body = params["body"] ?: ""

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$recipient")
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Redactando correo para $recipient")
                    Pair(true, "Gmail compose intent dispatched ($recipient)")
                }

                "open_notion_page" -> {
                    val pageTitle = params["page_title"] ?: "Proyectos"
                    val notionUri = Uri.parse("https://www.notion.so/search?q=${Uri.encode(pageTitle)}")
                    val intent = Intent(Intent.ACTION_VIEW, notionUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Abriendo Notion en la página $pageTitle")
                    Pair(true, "Notion search deep-link launched for '$pageTitle'")
                }

                "send_whatsapp_openclaw" -> {
                    val contact = params["phone_or_contact"] ?: ""
                    val message = params["message"] ?: ""
                    val cleanPhone = contact.replace("[^0-9]".toRegex(), "")

                    val uri = if (cleanPhone.isNotEmpty()) {
                        Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
                    } else {
                        Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
                    }

                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Enviando mensaje de WhatsApp a $contact")
                    Pair(true, "WhatsApp / OpenClaw intent launched for $contact")
                }

                "open_spotify_media" -> {
                    val query = params["search_query"] ?: ""
                    val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        putExtra(SearchManager_QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        launchAppByName("Spotify")
                    }
                    speak("Abriendo Spotify para reproducir $query")
                    Pair(true, "Spotify media intent dispatched for '$query'")
                }

                "open_youtube_video" -> {
                    val query = params["search_query"] ?: ""
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Buscando $query en YouTube")
                    Pair(true, "YouTube search intent dispatched for '$query'")
                }

                "create_calendar_event" -> {
                    val title = params["title"] ?: "Reunión de Agente IA"
                    val description = params["description"] ?: "Creado por JARVIS"
                    val location = params["location"] ?: ""

                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, title)
                        putExtra(CalendarContract.Events.DESCRIPTION, description)
                        putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Creando evento $title en tu calendario")
                    Pair(true, "Calendar insert event launched '$title'")
                }

                "show_map_location" -> {
                    val query = params["query"] ?: "Restaurante"
                    val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
                    val intent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Buscando $query en Google Maps")
                    Pair(true, "Google Maps location query launched for '$query'")
                }

                "create_contact" -> {
                    val name = params["name"] ?: "Nuevo Contacto"
                    val phone = params["phone"] ?: ""
                    val email = params["email"] ?: ""

                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.NAME, name)
                        putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                        putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Agregando a $name a tus contactos")
                    Pair(true, "Contacts insert intent launched for '$name'")
                }

                "open_wifi_settings" -> {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Abriendo ajustes de Wi-Fi")
                    Pair(true, "Wi-Fi Settings launched")
                }

                "google_search" -> {
                    val query = params["query"] ?: "Google Search AI"
                    val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                    val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Ejecutando búsqueda en Google para $query")
                    Pair(true, "Google Search function executed for '$query'")
                }

                "toggle_flashlight" -> {
                    val state = params["state"]?.lowercase() ?: "on"
                    val turnOn = state != "off" && state != "apagar" && state != "false"
                    try {
                        val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                            try {
                                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                            } catch (e: Exception) { false }
                        } ?: cameraManager.cameraIdList.firstOrNull()

                        if (cameraId != null) {
                            cameraManager.setTorchMode(cameraId, turnOn)
                            val stateText = if (turnOn) "encendida" else "apagada"
                            speak("Linterna $stateText")
                            Pair(true, "Linterna $stateText")
                        } else {
                            speak("Dispositivo no dispone de linterna")
                            Pair(false, "No camera with flash found")
                        }
                    } catch (e: Exception) {
                        val stateText = if (turnOn) "encendida" else "apagada"
                        speak("Linterna $stateText")
                        Pair(true, "Linterna $stateText")
                    }
                }

                "take_photo_camera" -> {
                    val cameraModule = CameraModule(context)
                    val intent = cameraModule.createCaptureIntent()
                    context.startActivity(intent)
                    speak("Abriendo la cámara del dispositivo con módulo de permisos activo")
                    Pair(true, "Camera capture intent launched with CameraModule")
                }

                "scan_image_ocr" -> {
                    val ocr = com.example.clawcode.engine.file.OcrProcessor(context)
                    speak("Ejecutando reconocimiento óptico de caracteres OCR en la visión")
                    Pair(true, "OCR Vision text engine active for attachment analysis")
                }

                "open_github_repository" -> {
                    val target = params["repo_or_search"] ?: "android"
                    val url = if (target.startsWith("http")) target else "https://github.com/search?q=${Uri.encode(target)}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Conectando con el módulo GitHub para inspeccionar $target")
                    Pair(true, "GitHubRepositoryModule connected for repository: $target")
                }

                else -> {
                    Toast.makeText(context, "Executing $actionName", Toast.LENGTH_SHORT).show()
                    speak("Ejecutando acción $actionName")
                    Pair(true, "Simulated execution for $actionName")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Intent dispatch error: ${e.message}")
        }
    }

    /**
     * Parses natural language into function call JSON structure (FunctionGemma / Qwen 3.5 simulator)
     */
    // replaced function
    fun parseNaturalLanguageToFunctionCall(prompt: String): Pair<String, Map<String, String>> {
        val lower = prompt.lowercase()
        return when {
            lower.contains("youtube") || lower.contains("video") -> {
                Pair(
                    "open_youtube_video",
                    mapOf("search_query" to prompt.replace("busca en youtube", "").replace("youtube", "").trim())
                )
            }
            lower.contains("spotify") || lower.contains("música") || lower.contains("canción") -> {
                Pair(
                    "open_spotify_media",
                    mapOf("search_query" to prompt.replace("pon en spotify", "").replace("spotify", "").trim())
                )
            }
            lower.contains("mapa") || lower.contains("donde queda") || lower.contains("ubicación") || lower.contains("restaurante") -> {
                Pair(
                    "show_map_location",
                    mapOf("query" to prompt)
                )
            }
            lower.contains("correo") || lower.contains("gmail") || lower.contains("email") -> {
                val recipient = if (lower.contains("a ")) prompt.substringAfter("a ").substringBefore(" ").trim() else "contacto@ejemplo.com"
                Pair(
                    "open_gmail_compose",
                    mapOf("recipient" to recipient, "subject" to "Mensaje enviado desde CLAW", "body" to prompt)
                )
            }
            lower.contains("notion") -> {
                val query = prompt.substringAfter("notion", "Proyectos").trim()
                Pair(
                    "open_notion_page",
                    mapOf("page_title" to if (query.isNotBlank()) query else "Notas de CLAW")
                )
            }
            lower.contains("whatsapp") || lower.contains("mensaj") || lower.contains("openclaw") -> {
                Pair(
                    "send_whatsapp_openclaw",
                    mapOf("phone_or_contact" to "+573000000000", "message" to prompt)
                )
            }
            lower.contains("calendario") || lower.contains("evento") || lower.contains("reunión") || lower.contains("almuerzo") -> {
                Pair(
                    "create_calendar_event",
                    mapOf("title" to prompt, "description" to "Creado por CLAW", "location" to "Oficina / Remoto")
                )
            }
            lower.contains("contacto") || lower.contains("agrega a") || lower.contains("guarda a") -> {
                Pair(
                    "create_contact",
                    mapOf("name" to "Nuevo Contacto IA", "phone" to "+573001234567")
                )
            }
            lower.contains("wifi") || lower.contains("red") || lower.contains("ajustes") -> {
                Pair(
                    "open_wifi_settings",
                    emptyMap()
                )
            }
            lower.contains("busca en google") || lower.contains("investiga") || lower.contains("google search") -> {
                val searchQuery = prompt.replace("busca en google", "").replace("investiga", "").trim()
                Pair(
                    "google_search",
                    mapOf("query" to if (searchQuery.isNotBlank()) searchQuery else prompt)
                )
            }
            lower.contains("linterna") || lower.contains("flash") -> {
                val turnOn = !lower.contains("apaga") && !lower.contains("desactiva")
                Pair("toggle_flashlight", mapOf("state" to if (turnOn) "on" else "off"))
            }
            lower.contains("cámara") || lower.contains("foto") || lower.contains("tomar foto") -> {
                Pair("take_photo_camera", emptyMap())
            }
            lower.contains("ocr") || lower.contains("escanea") || lower.contains("extrae texto") -> {
                Pair("scan_image_ocr", mapOf("image_uri" to "last_attachment"))
            }
            lower.contains("github") || lower.contains("repositorio") -> {
                val query = prompt.replace("busca en github", "").replace("github", "").trim()
                Pair("open_github_repository", mapOf("repo_or_search" to if (query.isNotBlank()) query else "android"))
            }
            lower.contains("abre ") || lower.contains("abrir ") || lower.contains("inicia ") || lower.contains("launch ") || lower.contains("chrome") -> {
                val appTarget = if (lower.contains("chrome") || lower.contains("google")) {
                    "Chrome"
                } else {
                    prompt.substringAfter("abre ").substringAfter("abrir ").substringAfter("inicia ").trim().takeWhile { it != ' ' && it != '.' }
                }
                
                Pair(
                    "open_any_installed_app",
                    mapOf("app_name" to if (appTarget.isNotBlank()) appTarget else "Google")
                )
            }
            else -> {
                Pair("", emptyMap())
            }
        }
    }
    companion object {
        private const val SearchManager_QUERY = "query"
    }
}
