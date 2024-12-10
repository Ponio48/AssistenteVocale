class AppInteractionManager(private val context: Context) {
    
    fun sendWhatsAppMessage(contact: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                `package` = "com.whatsapp"
                putExtra("jid", "${contact.replace("+", "")}@s.whatsapp.net")
                putExtra(Intent.EXTRA_TEXT, message)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppInteraction", "Errore WhatsApp: ${e.message}")
        }
    }

    fun sendWhatsAppVoiceNote(contact: String, audioFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                audioFile
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                `package` = "com.whatsapp"
                putExtra("jid", "${contact.replace("+", "")}@s.whatsapp.net")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppInteraction", "Errore WhatsApp Voice: ${e.message}")
        }
    }

    fun makePhoneCall(number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppInteraction", "Errore Chiamata: ${e.message}")
        }
    }

    fun checkGmail() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppInteraction", "Errore Gmail: ${e.message}")
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "") {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppInteraction", "Errore Sveglia: ${e.message}")
        }
    }

    fun parseTimeFromText(timeText: String): Pair<Int, Int>? {
        val pattern = "(\\d{1,2})(?::|\\s*e\\s*)(\\d{2})".toRegex()
        val match = pattern.find(timeText)
        return match?.let {
            val (hour, minute) = it.destructured
            Pair(hour.toInt(), minute.toInt())
        }
    }

    fun extractPhoneNumber(text: String): String? {
        val pattern = "\\b\\d{10}\\b|\\+\\d{12}\\b".toRegex()
        return pattern.find(text)?.value
    }

    fun readGmailEmails() {
        try {
            val contentResolver = context.contentResolver
            val uri = Uri.parse("content://gmail-ls/messages")
            val projection = arrayOf(
                "sender",
                "subject",
                "snippet",
                "date"
            )
            val cursor = contentResolver.query(uri, projection, null, null, "date DESC")
            
            cursor?.use {
                val emails = mutableListOf<EmailMessage>()
                while (it.moveToNext() && emails.size < 5) { // Leggiamo le ultime 5 email
                    val sender = it.getString(it.getColumnIndexOrThrow("sender"))
                    val subject = it.getString(it.getColumnIndexOrThrow("subject"))
                    val snippet = it.getString(it.getColumnIndexOrThrow("snippet"))
                    
                    emails.add(EmailMessage(sender, subject, snippet))
                }
                readEmails(emails)
            }
        } catch (e: Exception) {
            Log.e("AppInteraction", "Errore lettura Gmail: ${e.message}")
        }
    }

    fun readWhatsAppMessages(contact: String? = null) {
        try {
            val contentResolver = context.contentResolver
            val uri = Uri.parse("content://com.whatsapp.provider.media/messages")
            val selection = contact?.let { "contact_name = ?" }
            val selectionArgs = contact?.let { arrayOf(it) }
            
            val cursor = contentResolver.query(
                uri,
                arrayOf("message_text", "timestamp", "media_type", "media_path"),
                selection,
                selectionArgs,
                "timestamp DESC"
            )

            cursor?.use {
                val messages = mutableListOf<WhatsAppMessage>()
                while (it.moveToNext() && messages.size < 5) {
                    val text = it.getString(it.getColumnIndexOrThrow("message_text"))
                    val mediaType = it.getInt(it.getColumnIndexOrThrow("media_type"))
                    val mediaPath = it.getString(it.getColumnIndexOrThrow("media_path"))
                    
                    messages.add(WhatsAppMessage(text, mediaType, mediaPath))
                }
                readWhatsAppContent(messages)
            }
        } catch (e: Exception) {
            Log.e("AppInteraction", "Errore lettura WhatsApp: ${e.message}")
        }
    }

    private fun readEmails(emails: List<EmailMessage>) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.ITALIAN
                
                emails.forEach { email ->
                    val text = "Email da ${email.sender}. Oggetto: ${email.subject}. " +
                            "Contenuto: ${email.snippet}"
                    textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null)
                }
            }
        }
    }

    private fun readWhatsAppContent(messages: List<WhatsAppMessage>) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.ITALIAN
                
                messages.forEach { message ->
                    when (message.mediaType) {
                        1 -> { // Messaggio testuale
                            textToSpeech.speak(message.text, TextToSpeech.QUEUE_ADD, null, null)
                        }
                        2 -> { // Messaggio vocale
                            playAudioMessage(message.mediaPath)
                        }
                    }
                }
            }
        }
    }

    private fun playAudioMessage(path: String?) {
        path?.let {
            try {
                val mediaPlayer = MediaPlayer().apply {
                    setDataSource(it)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("AppInteraction", "Errore riproduzione audio: ${e.message}")
            }
        }
    }

    data class EmailMessage(
        val sender: String,
        val subject: String,
        val snippet: String
    )

    data class WhatsAppMessage(
        val text: String?,
        val mediaType: Int,
        val mediaPath: String?
    )
} 