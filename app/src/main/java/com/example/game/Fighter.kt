package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Represents a playable or AI-controlled Knight fighter in the 2D arena.
 */
class Fighter(
    val isPlayer1: Boolean,
    var x: Float,
    var y: Float,
    var groundY: Float
) {
    // Fighter dimensions
    var width: Float = 280f
    var height: Float = 360f

    // Physics
    var vx: Float = 0f
    var vy: Float = 0f
    val speed: Float = 460f
    val jumpVelocity: Float = -920f
    val gravity: Float = 2300f
    val dashSpeed: Float = 850f

    // Orientation & State
    var facingLeft: Boolean = !isPlayer1
    var state: FighterState = FighterState.IDLE
        private set

    // Health & Combat Attributes
    val maxHealth: Float = 100f
    var health: Float = maxHealth
    var displayHealth: Float = maxHealth // Smooth trailing visual health
    var wins: Int = 0

    // Hitboxes & Hurtboxes
    val hurtbox: RectF = RectF()
    val hitbox: RectF = RectF()
    var isHitboxActive: Boolean = false

    // Timers & Counters
    var stateTimer: Float = 0f
    var hitStunTimer: Float = 0f
    var attackCooldown: Float = 0f
    var comboHits: Int = 0
    var comboWindowTimer: Float = 0f
    var lastHitDamage: Float = 0f

    // Animation framing
    var currentFrameIndex: Int = 0
    private var frameTimeCounter: Float = 0f
    private val frameDuration: Float = 0.08f // 80ms per frame

    // Color debug paint
    private val debugHurtboxPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val debugHitboxPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f }

    init {
        updateBoxes()
    }

    fun resetForRound(startX: Float) {
        x = startX
        y = groundY - height
        vx = 0f
        vy = 0f
        health = maxHealth
        displayHealth = maxHealth
        facingLeft = !isPlayer1
        state = FighterState.IDLE
        stateTimer = 0f
        hitStunTimer = 0f
        attackCooldown = 0f
        comboHits = 0
        comboWindowTimer = 0f
        isHitboxActive = false
        updateBoxes()
    }

    fun update(dt: Float, screenWidth: Float, opponent: Fighter, spriteLoader: SpriteLoader) {
        // Smooth trailing health animation
        if (displayHealth > health) {
            displayHealth -= (displayHealth - health) * 5f * dt
            if (displayHealth < health) displayHealth = health
        }

        // Combo window decay
        if (comboHits > 0) {
            comboWindowTimer -= dt
            if (comboWindowTimer <= 0f) {
                comboHits = 0
            }
        }

        // Attack cooldown decay
        if (attackCooldown > 0f) {
            attackCooldown -= dt
        }

        // State Machine logic update
        when (state) {
            FighterState.HIT_STUN -> {
                hitStunTimer -= dt
                vx *= 0.82f // Friction during knockback — decays fast
                if (hitStunTimer <= 0f) {
                    vx = 0f   // ← zero residual velocity so player STOPS immediately after stun
                    if (health <= 0f) {
                        setState(FighterState.DEATH)
                    } else {
                        setState(FighterState.IDLE)
                    }
                }
            }
            FighterState.LIGHT_ATTACK -> {
                stateTimer += dt
                val maxFrames = spriteLoader.getFrameCount(isPlayer1, state)
                val duration = if (maxFrames > 0) maxFrames * frameDuration else 0.32f
                isHitboxActive = stateTimer in (duration * 0.25f)..(duration * 0.7f)
                if (stateTimer >= duration) {
                    setState(FighterState.IDLE)
                }
            }
            FighterState.HEAVY_ATTACK -> {
                stateTimer += dt
                val maxFrames = spriteLoader.getFrameCount(isPlayer1, state)
                val duration = if (maxFrames > 0) maxFrames * frameDuration else 0.48f
                isHitboxActive = stateTimer in (duration * 0.3f)..(duration * 0.7f)
                if (stateTimer >= duration) {
                    setState(FighterState.IDLE)
                }
            }
            FighterState.COMBO_ATTACK -> {
                stateTimer += dt
                val maxFrames = spriteLoader.getFrameCount(isPlayer1, state)
                val duration = if (maxFrames > 0) maxFrames * frameDuration else 0.60f
                isHitboxActive = stateTimer in (duration * 0.2f)..(duration * 0.8f)
                if (stateTimer >= duration) {
                    setState(FighterState.IDLE)
                }
            }
            FighterState.CROUCH_ATTACK -> {
                stateTimer += dt
                val maxFrames = spriteLoader.getFrameCount(isPlayer1, state)
                val duration = if (maxFrames > 0) maxFrames * frameDuration else 0.36f
                isHitboxActive = stateTimer in (duration * 0.25f)..(duration * 0.7f)
                if (stateTimer >= duration) {
                    setState(FighterState.CROUCH)
                }
            }
            FighterState.ROLL -> {
                stateTimer += dt
                val maxFrames = spriteLoader.getFrameCount(isPlayer1, state)
                val duration = if (maxFrames > 0) maxFrames * frameDuration else 0.38f
                if (stateTimer >= duration) {
                    vx = 0f
                    setState(FighterState.IDLE)
                }
            }
            FighterState.DEATH -> {
                vx = 0f
            }
            else -> {
                // Auto face opponent when movable
                if (state.canMove && state != FighterState.CROUCH) {
                    facingLeft = x > opponent.x
                }
            }
        }

        // Apply Physics & Gravity
        vy += gravity * dt
        x += vx * dt
        y += vy * dt

        // Ground Collision
        if (y >= groundY - height) {
            y = groundY - height
            vy = 0f
            if (state == FighterState.JUMP || state == FighterState.FALL) {
                setState(FighterState.IDLE)
            }
        } else if (vy > 0f && state.canMove) {
            state = FighterState.FALL
        }

        // Screen Boundary Constraints
        val margin = 20f
        if (x < margin) x = margin
        if (x > screenWidth - width - margin) x = screenWidth - width - margin

        // Animation frame progression — clamp to prevent index overflow (missing frames bug)
        frameTimeCounter += dt
        if (frameTimeCounter >= frameDuration) {
            frameTimeCounter -= frameDuration
            val maxFrames = spriteLoader.getFrameCount(isPlayer1, state)
            if (maxFrames > 0) {
                currentFrameIndex = (currentFrameIndex + 1).coerceAtMost(maxFrames - 1)
            } else {
                currentFrameIndex++   // fallback: no sprite loaded yet
            }
        }

        updateBoxes()
    }

    /**
     * Prevents fighters from clipping through each other.
     * RULE: If `other` (the attacker) is mid-attack, only push the ATTACKER back
     * (not the defender). This stops the "walking attack push" bug where the AI
     * continuously rams the player backward during its attack animation.
     */
    fun resolveCollisionWith(other: Fighter) {
        val overlapX = (x + width / 2f) - (other.x + other.width / 2f)
        val minDistance = (width + other.width) * 0.42f

        if (Math.abs(overlapX) < minDistance) {
            val totalPush = minDistance - Math.abs(overlapX)

            // If the other fighter is actively attacking, absorb all push on their side
            // so THIS fighter (defender) doesn't get shoved backward by contact alone.
            val myShare    = if (other.state.isAttacking) 0.0f else 0.5f
            val otherShare = 1.0f - myShare

            if (overlapX > 0) {
                x        += totalPush * myShare
                other.x  -= totalPush * otherShare
            } else {
                x        -= totalPush * myShare
                other.x  += totalPush * otherShare
            }
        }
    }

    fun setState(newState: FighterState) {
        if (state == FighterState.DEATH) return // Cannot exit death state
        if (state == newState) return

        state = newState
        stateTimer = 0f
        currentFrameIndex = 0
        frameTimeCounter = 0f

        if (!newState.isAttacking) {
            isHitboxActive = false
        }
    }

    // --- Action Inputs ---

    fun move(dirX: Float) {
        if (!state.canMove) return

        if (dirX < -0.2f) {
            vx = -speed
            if (y >= groundY - height) setState(FighterState.RUN)
        } else if (dirX > 0.2f) {
            vx = speed
            if (y >= groundY - height) setState(FighterState.RUN)
        } else {
            vx = 0f
            if (y >= groundY - height && state == FighterState.RUN) {
                setState(FighterState.IDLE)
            }
        }
    }

    fun jump() {
        if (y >= groundY - height && state.canMove) {
            vy = jumpVelocity
            setState(FighterState.JUMP)
        }
    }

    fun crouch(isCrouching: Boolean) {
        if (state == FighterState.IDLE || state == FighterState.RUN || state == FighterState.CROUCH) {
            if (isCrouching) {
                vx = 0f
                setState(FighterState.CROUCH)
            } else if (state == FighterState.CROUCH) {
                setState(FighterState.IDLE)
            }
        }
    }

    fun lightAttack() {
        if (state.canAttack && attackCooldown <= 0f) {
            vx = 0f   // stand still when attacking — no sliding into opponent
            setState(if (state == FighterState.CROUCH) FighterState.CROUCH_ATTACK else FighterState.LIGHT_ATTACK)
            attackCooldown = 0.2f
        }
    }

    fun heavyAttack() {
        if (state.canAttack && attackCooldown <= 0f) {
            vx = 0f   // stand still when attacking — no sliding into opponent
            setState(FighterState.HEAVY_ATTACK)
            attackCooldown = 0.35f
        }
    }

    fun comboAttack() {
        if (state.canAttack && attackCooldown <= 0f) {
            vx = 0f   // stand still when attacking — no sliding into opponent
            setState(FighterState.COMBO_ATTACK)
            attackCooldown = 0.5f
        }
    }

    fun roll() {
        if (state.canMove) {
            if (Math.abs(vx) > 0f) {
                vx = if (vx > 0) dashSpeed else -dashSpeed
            }
            setState(FighterState.ROLL)
        }
    }

    fun block(isBlocking: Boolean) {
        if (state == FighterState.IDLE || state == FighterState.RUN || state == FighterState.BLOCKING) {
            if (isBlocking) {
                vx = 0f
                setState(FighterState.BLOCKING)
            } else if (state == FighterState.BLOCKING) {
                setState(FighterState.IDLE)
            }
        }
    }

    fun takeDamage(damage: Float, knockbackX: Float, stunTime: Float): Boolean {
        if (state.isInvulnerable || state == FighterState.DEATH) return false

        val isBlocking = state == FighterState.BLOCKING
        val actualDamage = if (isBlocking) damage * 0.15f else damage
        health -= actualDamage
        if (health < 0f) health = 0f
        lastHitDamage = actualDamage

        if (isBlocking) {
            // Blocked hit: tiny pushback only (no stun, no full knockback)
            vx = knockbackX * 0.08f
        } else {
            // Full knockback + hit-stun
            vx = knockbackX
            hitStunTimer = stunTime
            setState(FighterState.HIT_STUN)
        }

        return true
    }

    private fun updateBoxes() {
        // ── Hurtbox — covers the visible character torso ──────────────────────
        // Crouch: shift top down by 35% of height (shorter hitzone when crouching)
        val crouchShift = if (state == FighterState.CROUCH ||
                              state == FighterState.CROUCH_ATTACK) height * 0.30f else 0f
        hurtbox.set(
            x + width * 0.22f,                         // left edge (slim sides)
            y + crouchShift + height * 0.10f,           // top (skip head area slightly)
            x + width * 0.78f,                         // right edge
            y + height * 0.96f                          // bottom (near feet)
        )

        // ── Weapon Hitbox — only active during attack states ──────────────────
        if (isHitboxActive) {
            // Reach extends from the character's hand/sword tip outward.
            // The weapon starts at ~65% of body width from the near edge.
            val reach = when (state) {
                FighterState.HEAVY_ATTACK  -> 195f   // wide sword swing
                FighterState.COMBO_ATTACK  -> 225f   // longest lunge
                FighterState.CROUCH_ATTACK -> 110f   // short low hit
                else                       -> 140f   // light attack
            }
            // Vertical band: weapon hits mid-body (30%–75% of height),
            // matching where the sword visually connects with the opponent.
            val hitTop    = y + height * 0.28f
            val hitBottom = y + height * 0.75f

            if (facingLeft) {
                // Sword extends LEFT from the character's left hand
                val swordStart = x + width * 0.30f   // hand position
                hitbox.set(swordStart - reach, hitTop, swordStart, hitBottom)
            } else {
                // Sword extends RIGHT from the character's right hand
                val swordStart = x + width * 0.70f   // hand position
                hitbox.set(swordStart, hitTop, swordStart + reach, hitBottom)
            }
        } else {
            hitbox.setEmpty()
        }
    }

    fun drawDebugBoxes(canvas: Canvas) {
        canvas.drawRect(hurtbox, debugHurtboxPaint)
        if (isHitboxActive) {
            canvas.drawRect(hitbox, debugHitboxPaint)
        }
    }
}
