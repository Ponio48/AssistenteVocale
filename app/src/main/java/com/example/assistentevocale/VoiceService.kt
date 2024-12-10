class VoiceService : Service() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private var isListening = false
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private lateinit var appInteractionManager: AppInteractionManager
    
    // Costanti per l'ottimizzazione
    private val LISTENING_TIMEOUT = 30000L // 30 secondi
    private val SLEEP_INTERVAL = 2000L // 2 secondi di pausa tra le sessioni di ascolto

    private val userName = "Antonio"
    private val userNicknames = listOf(
        "Tony",
        "signor Tony",
        "signor Antonio",
        "boss",
        "capo"
    )

    private val webSearchManager = WebSearchManager()
    private val searchScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializeSpeechRecognizer()
        initializeTextToSpeech()
        startForeground()
        appInteractionManager = AppInteractionManager(this)
    }

    private fun initializeSpeechRecognizer() {
        try {
            Log.d("VoiceService", "Inizializzazione Speech Recognizer")
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.get(0)?.let { text ->
                        if (text.lowercase().contains("ivi")) {
                            activateFullListeningMode()
                        }
                    }
                    // Riavvia l'ascolto in modalità risparmio energetico
                    startListeningWithPowerSaving()
                }

                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // Riavvia in modalità risparmio energetico
                            startListeningWithPowerSaving()
                        }
                        else -> {
                            // Attendi prima di riprovare in caso di altri errori
                            handler.postDelayed({
                                startListeningWithPowerSaving()
                            }, SLEEP_INTERVAL)
                        }
                    }
                }

                // Altri metodi del RecognitionListener...
            })
        } catch (e: Exception) {
            Log.e("VoiceService", "Errore inizializzazione: ${e.message}")
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Imposta la lingua italiana
                textToSpeech.language = Locale.ITALIAN
                
                // Imposta una voce femminile
                val voices = textToSpeech.voices
                val femaleVoice = voices.firstOrNull { 
                    it.name.contains("female", ignoreCase = true) ||
                    it.name.contains("donna", ignoreCase = true)
                }
                femaleVoice?.let { textToSpeech.voice = it }
                
                // Personalizza i parametri base della voce
                textToSpeech.setPitch(1.1f)  // Pitch leggermente più alto per voce femminile
                textToSpeech.setSpeechRate(0.95f)  // Velocità leggermente più lenta per maggiore chiarezza
            }
        }
    }

    private fun startListeningWithPowerSaving() {
        if (!shouldStartListening()) {
            handler.postDelayed({
                startListeningWithPowerSaving()
            }, SLEEP_INTERVAL)
            return
        }

        acquireWakeLock()
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Aggiungi timeout per risparmiare batteria
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        try {
            speechRecognizer.startListening(intent)
            startListeningTimeout()
        } catch (e: Exception) {
            releaseWakeLock()
            handler.postDelayed({
                startListeningWithPowerSaving()
            }, SLEEP_INTERVAL)
        }
    }

    private fun shouldStartListening(): Boolean {
        // Verifica se il dispositivo è in carica o ha batteria sufficiente
        val batteryStatus = registerReceiver(null, 
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        } ?: 100f

        val isCharging = batteryStatus?.let { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false

        // Non ascoltare se la batteria è sotto il 15% e non in carica
        return isCharging || batteryLevel > 15
    }

    private fun acquireWakeLock() {
        wakeLock?.release()
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AssistenteVocale:VoiceServiceWakeLock"
        )?.apply {
            acquire(LISTENING_TIMEOUT)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun startListeningTimeout() {
        handler.postDelayed({
            if (isListening) {
                speechRecognizer.stopListening()
                releaseWakeLock()
                startListeningWithPowerSaving()
            }
        }, LISTENING_TIMEOUT)
    }

    private fun activateFullListeningMode() {
        when {
            isEveningTime() -> {
                val greeting = getGreeting(SpeakingMood.SENSUAL)
                speak("$greeting. Come posso renderti la serata più piacevole?", 
                    SpeakingMood.SENSUAL)
            }
            isImportantTask() -> {
                val greeting = getGreeting(SpeakingMood.ASSERTIVE)
                speak("$greeting. Ho delle notifiche importanti per te.", 
                    SpeakingMood.ASSERTIVE)
            }
            isSuggesting() -> {
                val greeting = getGreeting(SpeakingMood.ENGAGING)
                speak("$greeting! Ho delle interessanti novità da condividere!", 
                    SpeakingMood.ENGAGING)
            }
            else -> {
                val greeting = getGreeting(SpeakingMood.NORMAL)
                speak("$greeting. Come posso aiutarti?", 
                    SpeakingMood.NORMAL)
            }
        }
    }

    private fun isEveningTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 19..23
    }

    private fun isImportantTask(): Boolean {
        // Implementa la logica per determinare se il task è importante
        return false
    }

    private fun isSuggesting(): Boolean {
        // Implementa la logica per determinare se stai facendo un suggerimento
        return false
    }

    private fun startForeground() {
        val channelId = "voice_service_channel"
        val channel = NotificationChannel(
            channelId,
            "Servizio Vocale",
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistente Vocale")
            .setContentText("In ascolto...")
            .setSmallIcon(R.drawable.ic_mic)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListeningWithPowerSaving()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        handler.removeCallbacksAndMessages(null)
        speechRecognizer.destroy()
        textToSpeech.shutdown()
        searchScope.cancel()
    }

    private fun speak(text: String, mood: SpeakingMood = SpeakingMood.NORMAL) {
        val params = Bundle().apply {
            when (mood) {
                SpeakingMood.SENSUAL -> {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.8f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PITCH, 0.9f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_RATE, 0.85f)
                }
                SpeakingMood.ENGAGING -> {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PITCH, 1.1f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_RATE, 1.1f)
                }
                SpeakingMood.ASSERTIVE -> {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PITCH, 1.0f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_RATE, 1.0f)
                }
                SpeakingMood.NORMAL -> {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PITCH, 1.1f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_RATE, 0.95f)
                }
            }
        }

        textToSpeech.speak(
            addEmotionalMarkers(text, mood),
            TextToSpeech.QUEUE_FLUSH,
            params,
            "utteranceId_${System.currentTimeMillis()}"
        )
    }

    private fun addEmotionalMarkers(text: String, mood: SpeakingMood): String {
        return when (mood) {
            SpeakingMood.SENSUAL -> "<prosody rate='slow' pitch='-2st'>${text}</prosody>"
            SpeakingMood.ENGAGING -> "<prosody rate='medium' pitch='+2st'>${text}</prosody>"
            SpeakingMood.ASSERTIVE -> "<prosody rate='medium' pitch='0st' volume='+2db'>${text}</prosody>"
            SpeakingMood.NORMAL -> text
        }
    }

    private enum class SpeakingMood {
        NORMAL,     // Tono normale per conversazioni standard
        SENSUAL,    // Tono più morbido e lento
        ENGAGING,   // Tono vivace e coinvolgente
        ASSERTIVE   // Tono deciso e autorevole
    }

    private fun getGreeting(mood: SpeakingMood): String {
        val timeBasedGreeting = when {
            isMorning() -> "Buongiorno"
            isAfternoon() -> "Buon pomeriggio"
            isEvening() -> "Buonasera"
            else -> "Salve"
        }

        val nameToUse = when (mood) {
            SpeakingMood.SENSUAL -> {
                // Più informale e intimo
                listOf(
                    userName,
                    "Tony caro",
                    "tesoro"
                ).random()
            }
            SpeakingMood.ENGAGING -> {
                // Amichevole e informale
                listOf(
                    "Tony",
                    userName,
                    "capo"
                ).random()
            }
            SpeakingMood.ASSERTIVE -> {
                // Più formale
                listOf(
                    "signor $userName",
                    "signor Tony"
                ).random()
            }
            SpeakingMood.NORMAL -> userNicknames.random()
        }

        return "$timeBasedGreeting, $nameToUse"
    }

    private fun getResponsePrefix(mood: SpeakingMood): String {
        return when (mood) {
            SpeakingMood.SENSUAL -> listOf(
                "Con piacere",
                "Come desideri",
                "Sarò felice di aiutarti"
            ).random()
            SpeakingMood.ENGAGING -> listOf(
                "Certamente",
                "Ma certo",
                "Subito"
            ).random()
            SpeakingMood.ASSERTIVE -> listOf(
                "Assolutamente",
                "Immediatamente",
                "Provvedo subito"
            ).random()
            SpeakingMood.NORMAL -> listOf(
                "Ok",
                "Va bene",
                "Certo"
            ).random()
        }
    }

    private fun isMorning(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 5..11
    }

    private fun isAfternoon(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 12..18
    }

    private fun isEvening(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 19..23
    }

    private fun respondToCommand(command: String) {
        val mood = determineResponseMood(command)
        val prefix = getResponsePrefix(mood)
        val nameToUse = if (Random.nextFloat() > 0.7f) ", ${userNicknames.random()}" else ""
        
        when {
            command.contains(Regex("cerca|trovami|cercami|cos'è|chi è|dimmi")) -> {
                searchScope.launch {
                    val query = extractSearchQuery(command)
                    val searchResult = webSearchManager.searchInfo(query)
                    
                    withContext(Dispatchers.Main) {
                        val response = "$prefix$nameToUse. $searchResult"
                        speak(response, mood)
                    }
                }
            }
            command.contains("ora") -> 
                "$prefix$nameToUse. Sono le ${getCurrentTime()}"
            command.contains("meteo") -> 
                "$prefix$nameToUse. Controllo subito le previsioni del tempo"
            command.contains("musica") -> 
                "$prefix$nameToUse. Che genere di musica preferisci ascoltare?"
            // Gestione WhatsApp
            command.matches(Regex(".*manda\\s+(un\\s+)?messaggio\\s+(?:a|su\\s+whatsapp\\s+a)\\s+(.+?)\\s*:\\s*(.+)", RegexOption.IGNORE_CASE)) -> {
                val matchResult = Regex("manda\\s+(un\\s+)?messaggio\\s+(?:a|su\\s+whatsapp\\s+a)\\s+(.+?)\\s*:\\s*(.+)").find(command)
                matchResult?.let {
                    val (_, contact, message) = it.destructured
                    appInteractionManager.sendWhatsAppMessage(contact, message)
                    speak("$prefix$nameToUse. Ho inviato il messaggio a $contact", mood)
                }
            }

            // Gestione chiamate
            command.contains(Regex("chiama|telefona a", RegexOption.IGNORE_CASE)) -> {
                appInteractionManager.extractPhoneNumber(command)?.let { number ->
                    appInteractionManager.makePhoneCall(number)
                    speak("$prefix$nameToUse. Sto chiamando il numero $number", mood)
                }
            }

            // Gestione Gmail
            command.contains(Regex("(controlla|apri|leggi)\\s+(la\\s+)?posta|gmail", RegexOption.IGNORE_CASE)) -> {
                appInteractionManager.checkGmail()
                speak("$prefix$nameToUse. Sto aprendo Gmail", mood)
            }

            // Gestione sveglia
            command.contains(Regex("(imposta|metti|programma)\\s+(una\\s+)?sveglia", RegexOption.IGNORE_CASE)) -> {
                appInteractionManager.parseTimeFromText(command)?.let { (hour, minute) ->
                    appInteractionManager.setAlarm(hour, minute)
                    speak("$prefix$nameToUse. Ho impostato la sveglia per le $hour:$minute", mood)
                }
            }

            // Lettura email Gmail
            command.contains(Regex("leggi.*(email|mail|posta|gmail)", RegexOption.IGNORE_CASE)) -> {
                appInteractionManager.readGmailEmails()
                speak("$prefix$nameToUse. Ti leggo le ultime email ricevute", mood)
            }

            // Lettura messaggi WhatsApp
            command.contains(Regex("leggi.*messaggi.*whatsapp", RegexOption.IGNORE_CASE)) -> {
                val contactMatch = Regex("di\\s+(\\w+)").find(command)
                val contact = contactMatch?.groupValues?.get(1)
                appInteractionManager.readWhatsAppMessages(contact)
                speak("$prefix$nameToUse. Ti leggo gli ultimi messaggi${contact?.let { " di $it" } ?: ""}", mood)
            }

            // Riproduzione audio WhatsApp
            command.contains(Regex("riproduci.*(audio|vocale).*whatsapp", RegexOption.IGNORE_CASE)) -> {
                val contactMatch = Regex("di\\s+(\\w+)").find(command)
                val contact = contactMatch?.groupValues?.get(1)
                appInteractionManager.readWhatsAppMessages(contact)
                speak("$prefix$nameToUse. Riproduco i messaggi vocali${contact?.let { " di $it" } ?: ""}", mood)
            }

            // Aggiungi altri comandi qui
            else -> 
                "$prefix$nameToUse. Non sono sicura di aver capito. Puoi ripetere?"
        }
    }

    private fun determineResponseMood(command: String): SpeakingMood {
        return when {
            command.contains(Regex("(grazie|piacere|gentile)")) -> 
                SpeakingMood.SENSUAL
            command.contains(Regex("(urgente|importante|subito)")) -> 
                SpeakingMood.ASSERTIVE
            command.contains(Regex("(divertente|interessante|wow)")) -> 
                SpeakingMood.ENGAGING
            else -> 
                SpeakingMood.NORMAL
        }
    }

    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }

    private fun extractSearchQuery(command: String): String {
        return command.replace(Regex("(cerca|trovami|cercami|cos'è|chi è|dimmi)"), "")
            .trim()
    }
} 