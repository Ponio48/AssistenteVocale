class PulsingSphereView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var radius = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var rippleRadius = 0f
    private var isListening = false

    private val pulseAnimator = ValueAnimator.ofFloat(0.8f, 1.2f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { animator ->
            radius = minOf(width, height) / 3f * animator.animatedValue as Float
            invalidate()
        }
    }

    private val rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            rippleRadius = radius * 2 * animator.animatedValue as Float
            ripplePaint.alpha = ((1f - animator.animatedValue as Float) * 255).toInt()
            invalidate()
        }
    }

    init {
        spherePaint.shader = RadialGradient(
            0f, 0f, radius,
            intArrayOf(
                ContextCompat.getColor(context, R.color.sphere_gradient_start),
                ContextCompat.getColor(context, R.color.sphere_gradient_end)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        ripplePaint.style = Paint.Style.STROKE
        ripplePaint.strokeWidth = 5f
        ripplePaint.color = ContextCompat.getColor(context, R.color.ripple)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = minOf(w, h) / 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (isListening) {
            canvas.drawCircle(centerX, centerY, rippleRadius, ripplePaint)
        }
        canvas.drawCircle(centerX, centerY, radius, spherePaint)
    }

    fun startListening() {
        isListening = true
        pulseAnimator.start()
        rippleAnimator.start()
    }

    fun stopListening() {
        isListening = false
        pulseAnimator.cancel()
        rippleAnimator.cancel()
        invalidate()
    }
} 