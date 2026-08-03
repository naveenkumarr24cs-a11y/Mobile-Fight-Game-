package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Handles multi-touch input for a Fixed AAA Virtual Thumbstick (Left thumb),
 * Glass-morphic Action Buttons (Right thumb), and top-right Pause Button.
 */
class InputManager {

    // Fixed Virtual Joystick State
    var isJoystickActive: Boolean = false
    var joystickCenterX: Float = 0f
    var joystickCenterY: Float = 0f
    var joystickRadius: Float = 120f
    var joystickKnobX: Float = 0f
    var joystickKnobY: Float = 0f
    var joystickPointerId: Int = -1

    // Virtual Input States
    var moveDirX: Float = 0f
    var moveDirY: Float = 0f
    var isLightPressed: Boolean = false
    var isHeavyPressed: Boolean = false
    var isComboPressed: Boolean = false
    var isRollPressed: Boolean = false
    var isBlockPressed: Boolean = false
    var isPausePressed: Boolean = false

    // Track WHICH pointer is holding each button (fix button-release slide bug)
    private var lightPointerId:  Int = -1
    private var heavyPointerId:  Int = -1
    private var comboPointerId:  Int = -1
    private var rollPointerId:   Int = -1
    private var blockPointerId:  Int = -1

    // Action Button hitboxes
    val btnLight = RectF()
    val btnHeavy = RectF()
    val btnCombo = RectF()
    val btnRoll = RectF()
    val btnBlock = RectF()
    val btnPause = RectF()

    // Joystick screen side boundary
    var screenWidth: Float = 1920f
    var screenHeight: Float = 1080f

    /**
     * Full reset — call at the start of every round so no joystick direction or
     * button press from the previous round leaks into the new one.
     */
    fun reset(player1: Fighter) {
        // Release joystick
        isJoystickActive  = false
        joystickPointerId = -1
        joystickKnobX     = joystickCenterX
        joystickKnobY     = joystickCenterY
        moveDirX          = 0f
        moveDirY          = 0f

        // Release all button pointer IDs
        lightPointerId = -1
        heavyPointerId = -1
        comboPointerId = -1
        rollPointerId  = -1
        blockPointerId = -1

        // Clear button pressed states
        isLightPressed = false
        isHeavyPressed = false
        isComboPressed = false
        isRollPressed  = false
        isBlockPressed = false
        isPausePressed = false

        // Ensure fighter also stops moving / unblocks
        player1.move(0f)
        player1.crouch(false)
        player1.block(false)
    }

    // Pre-allocated Glass-morphic Paints
    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#350F172A")
    }
    private val glassBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.parseColor("#B0FFFFFF")
    }
    private val glowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Joystick Paints
    private val joyBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#450F172A")
        style = Paint.Style.FILL
    }
    private val joyBaseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9038BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val joyInnerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val joyKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D038BDF8")
        style = Paint.Style.FILL
    }
    private val joyKnobBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Text & Icon Paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val pauseIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    fun layoutControls(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height

        // ── Scale everything relative to the shorter side (height in landscape) ──
        // This ensures controls are comfortable on small (720p), medium (1080p),
        // large (1440p), and curved AMOLED displays alike.
        val baseSize = height  // reference dimension

        // 1. Fixed Joystick – bottom-left, size proportional to height
        joystickRadius = (baseSize * 0.175f).coerceIn(80f, 160f)
        joystickCenterX = width * 0.14f + joystickRadius * 0.2f
        joystickCenterY = height * 0.74f - joystickRadius * 0.1f
        joystickKnobX = joystickCenterX
        joystickKnobY = joystickCenterY

        // 2. Right Action Buttons – diamond layout, right side of screen
        //    bSize clamped so buttons are never too tiny (small phones) or huge (tablets)
        val bSize   = (baseSize * 0.095f).coerceIn(44f, 100f)
        val spacing = bSize * 2.2f

        val rx = width  * 0.855f
        val ry = height * 0.70f

        // Top:          HEAVY  (↑)
        btnHeavy.set(rx - bSize, ry - spacing - bSize, rx + bSize, ry - spacing + bSize)
        // Left:         LIGHT  (←)
        btnLight.set(rx - spacing - bSize, ry - bSize, rx - spacing + bSize, ry + bSize)
        // Right:        COMBO  (→)
        btnCombo.set(rx + spacing - bSize, ry - bSize, rx + spacing + bSize, ry + bSize)
        // Bottom-Left:  ROLL
        btnRoll.set(rx - spacing * 0.65f - bSize, ry + spacing * 0.85f - bSize,
                    rx - spacing * 0.65f + bSize, ry + spacing * 0.85f + bSize)
        // Bottom-Right: BLOCK
        btnBlock.set(rx + spacing * 0.65f - bSize, ry + spacing * 0.85f - bSize,
                     rx + spacing * 0.65f + bSize, ry + spacing * 0.85f + bSize)

        // 3. Pause Button – top-right corner, scaled with screen
        val pRadius = (baseSize * 0.038f).coerceIn(24f, 46f)
        val pCx = width  - pRadius * 2f
        val pCy = pRadius * 1.5f
        btnPause.set(pCx - pRadius, pCy - pRadius, pCx + pRadius, pCy + pRadius)
    }


    fun handleTouchEvent(
        action: Int,
        pointerIndex: Int,
        pointerId: Int,
        x: Float,
        y: Float,
        f1: Fighter
    ) {
        when (action) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                // Check Pause Button
                if (btnPause.contains(x, y)) {
                    isPausePressed = true
                    return
                }

                // Check Right Action Buttons – track pointer ID for reliable release
                when {
                    btnLight.contains(x, y) -> {
                        isLightPressed = true; lightPointerId = pointerId
                        f1.lightAttack(); return
                    }
                    btnHeavy.contains(x, y) -> {
                        isHeavyPressed = true; heavyPointerId = pointerId
                        f1.heavyAttack(); return
                    }
                    btnCombo.contains(x, y) -> {
                        isComboPressed = true; comboPointerId = pointerId
                        f1.comboAttack(); return
                    }
                    btnRoll.contains(x, y) -> {
                        isRollPressed = true; rollPointerId = pointerId
                        f1.roll(); return
                    }
                    btnBlock.contains(x, y) -> {
                        isBlockPressed = true; blockPointerId = pointerId
                        f1.block(true); return
                    }
                }

                // Joystick: Touch down on the LEFT half of the screen
                if (x < screenWidth * 0.48f && joystickPointerId == -1) {
                    joystickPointerId = pointerId
                    isJoystickActive = true
                    val dx = x - joystickCenterX
                    val dy = y - joystickCenterY
                    val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    updateJoystick(dx, dy, d)
                }
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                if (pointerId == joystickPointerId && isJoystickActive) {
                    val dx = x - joystickCenterX
                    val dy = y - joystickCenterY
                    val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    updateJoystick(dx, dy, d)
                }
            }

            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_POINTER_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                // Release joystick by pointer ID (not position)
                if (pointerId == joystickPointerId) {
                    joystickPointerId = -1
                    isJoystickActive  = false
                    joystickKnobX = joystickCenterX
                    joystickKnobY = joystickCenterY
                    moveDirX = 0f
                    moveDirY = 0f
                    f1.move(0f)
                    f1.crouch(false)
                }

                // Release buttons by pointer ID – works even if finger slides off button
                if (pointerId == lightPointerId) { isLightPressed = false; lightPointerId = -1 }
                if (pointerId == heavyPointerId) { isHeavyPressed = false; heavyPointerId = -1 }
                if (pointerId == comboPointerId) { isComboPressed = false; comboPointerId = -1 }
                if (pointerId == rollPointerId)  { isRollPressed  = false; rollPointerId  = -1 }
                if (pointerId == blockPointerId) {
                    isBlockPressed = false; blockPointerId = -1
                    f1.block(false)
                }
            }
        }
    }

    // updateJoystick: update knob visual + direction values only.
    // applyHeldInputs() is called separately by the game loop – NOT from here.
    private fun updateJoystick(dx: Float, dy: Float, dist: Float) {
        val maxR = joystickRadius
        val clampedDist = Math.min(dist, maxR)
        val angle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()

        joystickKnobX = joystickCenterX + Math.cos(angle.toDouble()).toFloat() * clampedDist
        joystickKnobY = joystickCenterY + Math.sin(angle.toDouble()).toFloat() * clampedDist

        // Normalise to [-1, 1] and apply a small dead zone (12%) for precision
        val normX = (dx / maxR).coerceIn(-1f, 1f)
        val normY = (dy / maxR).coerceIn(-1f, 1f)
        moveDirX = if (Math.abs(normX) < 0.12f) 0f else normX
        moveDirY = if (Math.abs(normY) < 0.12f) 0f else normY
    }

    fun applyHeldInputs(f1: Fighter) {
        if (!isJoystickActive) return

        f1.move(moveDirX)

        when {
            moveDirY < -0.42f -> f1.jump()
            moveDirY >  0.42f -> f1.crouch(true)
            else              -> f1.crouch(false)
        }
    }

    fun drawOverlay(canvas: Canvas) {
        // 1. Draw Permanent Fixed Virtual Thumbstick Base & Knob
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, joyBasePaint)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, joyBaseRingPaint)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius * 0.5f, joyInnerRingPaint)

        // Inner Thumbstick Knob
        val knobRadius = joystickRadius * 0.42f
        canvas.drawCircle(joystickKnobX, joystickKnobY, knobRadius, joyKnobPaint)
        canvas.drawCircle(joystickKnobX, joystickKnobY, knobRadius, joyKnobBorderPaint)

        // 2. Draw Glass-morphic Action Buttons
        drawGlassButton(canvas, btnHeavy, "HEAVY", isHeavyPressed, Color.parseColor("#DC2626"))
        drawGlassButton(canvas, btnLight, "LIGHT", isLightPressed, Color.parseColor("#2563EB"))
        drawGlassButton(canvas, btnCombo, "COMBO", isComboPressed, Color.parseColor("#7C3AED"))
        drawGlassButton(canvas, btnRoll, "ROLL", isRollPressed, Color.parseColor("#059669"))
        drawGlassButton(canvas, btnBlock, "BLOCK", isBlockPressed, Color.parseColor("#D97706"))

        // 3. Draw Polished Top-Right Pause Button
        drawPauseButton(canvas)
    }

    private fun drawGlassButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        isPressed: Boolean,
        accentColor: Int
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = rect.width() / 2f

        if (isPressed) {
            glowRingPaint.color = accentColor
            glowRingPaint.alpha = 200
            canvas.drawCircle(cx, cy, radius + 6f, glowRingPaint)

            glassBgPressedPaint.color = accentColor
            glassBgPressedPaint.alpha = 210
            canvas.drawCircle(cx, cy, radius, glassBgPressedPaint)
            glassBorderPaint.color = Color.WHITE
        } else {
            canvas.drawCircle(cx, cy, radius, glassBgPaint)
            glassBorderPaint.color = Color.parseColor("#B0FFFFFF")
        }

        canvas.drawCircle(cx, cy, radius, glassBorderPaint)

        textPaint.textSize = rect.height() * 0.28f
        textPaint.color = Color.WHITE
        canvas.drawText(label, cx, cy + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawPauseButton(canvas: Canvas) {
        val cx = btnPause.centerX()
        val cy = btnPause.centerY()
        val radius = btnPause.width() / 2f

        if (isPausePressed) {
            glassBgPressedPaint.color = Color.parseColor("#4B5563")
            glassBgPressedPaint.alpha = 220
            canvas.drawCircle(cx, cy, radius, glassBgPressedPaint)
            glassBorderPaint.color = Color.WHITE
        } else {
            canvas.drawCircle(cx, cy, radius, glassBgPaint)
            glassBorderPaint.color = Color.parseColor("#B0FFFFFF")
        }

        canvas.drawCircle(cx, cy, radius, glassBorderPaint)

        // Draw classic "||" pause bars icon
        val barW = 6f
        val barH = 20f
        val gap = 6f
        canvas.drawRoundRect(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f, 3f, 3f, pauseIconPaint)
        canvas.drawRoundRect(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f, 3f, 3f, pauseIconPaint)
    }
}
