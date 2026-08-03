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
            lower.contains("abre ") || lower.contains("abrir ") || lower.contains("inicia ") || lower.contains("launch ") || lower.contains("busca en google") || lower.contains("chrome") -> {
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
                Pair(
                    "open_any_installed_app",
                    mapOf("app_name" to "Google")
                )
            }
        }
    }
