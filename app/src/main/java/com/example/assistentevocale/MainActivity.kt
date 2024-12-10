class MainActivity : AppCompatActivity() {
    private lateinit var sphereView: PulsingSphereView
    private lateinit var statusText: TextView
    private lateinit var settingsButton: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupBottomSheet()
        checkAndRequestPermissions()
    }

    private fun initializeViews() {
        sphereView = findViewById(R.id.sphereView)
        statusText = findViewById(R.id.statusText)
        settingsButton = findViewById(R.id.settingsButton)

        settingsButton.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupBottomSheet() {
        val bottomSheet = findViewById<View>(R.id.settingsBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        
        // Setup sliders
        val pitchSlider = findViewById<Slider>(R.id.pitchSlider)
        val speedSlider = findViewById<Slider>(R.id.speedSlider)
        
        pitchSlider.addOnChangeListener { _, value, _ ->
            // Implementa la modifica del pitch
        }

        speedSlider.addOnChangeListener { _, value, _ ->
            // Implementa la modifica della velocità
        }

        // Setup background switch
        val backgroundSwitch = findViewById<SwitchMaterial>(R.id.backgroundSwitch)
        backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Implementa l'attivazione/disattivazione del background
        }

        // Setup permissions button
        findViewById<Button>(R.id.permissionsButton).setOnClickListener {
            openAppSettings()
        }
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionsToRequest = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startVoiceService()
                sphereView.startListening()
            } else {
                showPermissionsExplanationDialog()
            }
        }
    }

    private fun startVoiceService() {
        Intent(this, VoiceService::class.java).also { intent ->
            startForegroundService(intent)
        }
        statusText.text = "In ascolto..."
    }

    private fun showPermissionsExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permessi Necessari")
            .setMessage("Per funzionare correttamente, l'assistente vocale ha bisogno di tutti i permessi richiesti. Vuoi concederli ora?")
            .setPositiveButton("Sì") { _, _ -> 
                // Apri direttamente le impostazioni dei permessi
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("No") { _, _ -> finish() }
            .show()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Verifica ogni permesso
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        // Se ci sono permessi da richiedere
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            // Tutti i permessi sono già concessi
            startVoiceService()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 123
        private val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
} 