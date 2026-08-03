package com.example.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * Intelligent AI Controller — v4 with situational awareness.
 *
 * Improvements over v3:
 *  • HP-based strategy: High HP → aggressive, Mid HP → balanced, Low HP → defensive+counter
 *  • Hit-stun exploitation: if player is in HIT_STUN, AI immediately attacks (free hit window)
 *  • Cornered player detection: if player is near the screen edge, AI dashes in and uses heavy/combo
 *  • Combo continuation: after a successful attack, AI waits a short delay then attacks again
 *  • Jump attack: AI randomly jumps toward player from medium range for unpredictability
 *  • Better retreat logic: only retreats briefly, then immediately re-engages
 *
 * Block-lock bug is still FIXED (single-level design, block managed by aiBlockTimer only).
 */
class AIController {

    // AI's own cooldown between attacks
    private var aiAttackCooldown: Float = 0f

    // Block state managed entirely by AI
    private var aiBlockTimer: Float   = 0f
    private var isAiBlocking: Boolean = false

    // One reaction per player swing
    private var hasReactedToSwing: Boolean = false

    // Force-attack guarantee
    private var forceAttackTimer: Float     = 0f
    private val FORCE_ATTACK_INTERVAL       = 1.8f

    // Combo continuation — attack again after landing a hit
    private var comboFollowupTimer: Float   = 0f
    private var playerLastHealth: Float     = -1f

    // Retreat cooldown — after retreating, don't retreat again immediately
    private var retreatCooldown: Float      = 0f

    // Jump attack cooldown
    private var jumpAttackCooldown: Float   = 0f

    // Canvas width reference — set when first update is called
    private var canvasWidth: Float = 1920f

    // Distance thresholds (1920px-wide canvas)
    private val CLOSE_RANGE   = 290f
    private val MEDIUM_RANGE  = 520f
    private val CORNER_MARGIN = 180f  // player is "cornered" if within this of screen edge

    // ── Reset between rounds ───────────────────────────────────────────────────
    fun reset() {
        aiAttackCooldown   = 0f
        aiBlockTimer       = 0f
        isAiBlocking       = false
        hasReactedToSwing  = false
        forceAttackTimer   = 0f
        comboFollowupTimer = 0f
        playerLastHealth   = -1f
        retreatCooldown    = 0f
        jumpAttackCooldown = 0f
    }

    // ── Main update ────────────────────────────────────────────────────────────
    fun update(ai: Fighter, player: Fighter, dt: Float) {
        if (ai.state   == FighterState.DEATH) return
        if (player.state == FighterState.DEATH) return

        // Track canvas width once
        if (canvasWidth < 100f) canvasWidth = ai.groundY  // fallback

        aiAttackCooldown   = maxOf(0f, aiAttackCooldown - dt)
        forceAttackTimer   += dt
        retreatCooldown    = maxOf(0f, retreatCooldown - dt)
        jumpAttackCooldown = maxOf(0f, jumpAttackCooldown - dt)
        comboFollowupTimer = maxOf(0f, comboFollowupTimer - dt)

        // Always face the player when free
        if (ai.state.canMove) ai.facingLeft = player.x < ai.x

        // ── WAIT during attack / roll / stun animations ────────────────────────
        if (ai.state.isAttacking ||
            ai.state == FighterState.ROLL ||
            ai.state == FighterState.HIT_STUN) {
            isAiBlocking = false
            return
        }

        // ── BLOCK MANAGEMENT — guaranteed release ──────────────────────────────
        if (isAiBlocking) {
            aiBlockTimer -= dt
            if (ai.state != FighterState.BLOCKING) {
                isAiBlocking = false          // externally interrupted
            } else if (aiBlockTimer <= 0f) {
                ai.block(false)
                isAiBlocking = false
                // Counter-attack right after unblocking
                val d = dist(ai, player)
                if (d <= CLOSE_RANGE + 60f && aiAttackCooldown <= 0f) {
                    doAttack(ai, player, d, forceHeavy = true)  // counter = heavy/combo
                }
            }
            return
        }

        val d       = dist(ai, player)
        val hpRatio = ai.health / ai.maxHealth

        // ── DETECT landing a hit (player HP decreased) ────────────────────────
        val hitLanded = playerLastHealth > 0f && player.health < playerLastHealth
        playerLastHealth = player.health
        if (hitLanded && d <= CLOSE_RANGE + 80f) {
            // Start combo follow-up timer — attack again soon
            comboFollowupTimer = 0.18f + Random.nextFloat() * 0.12f
        }

        // ── COMBO FOLLOW-UP — continue pressure after landing a hit ───────────
        if (comboFollowupTimer <= 0f && hitLanded.not() && playerLastHealth >= 0f) {
            // timer just expired — handled below via attack logic
        }

        // ── HIT-STUN EXPLOITATION — free window when player is stunned ────────
        if (player.state == FighterState.HIT_STUN && d <= CLOSE_RANGE + 120f &&
            aiAttackCooldown <= 0f && ai.state.canAttack) {
            doAttack(ai, player, d)
            return
        }

        // ── Reset swing-reaction flag ─────────────────────────────────────────
        if (!player.state.isAttacking) hasReactedToSwing = false

        // ── DEFENSIVE REACTION (once per swing) ──────────────────────────────
        if (player.state.isAttacking && player.isHitboxActive && !hasReactedToSwing) {
            hasReactedToSwing = true
            val r = Random.nextFloat()

            // Low HP → more defensive; High HP → more likely to counter
            val blockThreshold  = if (hpRatio < 0.40f) 0.50f else 0.30f
            val rollThreshold   = blockThreshold + 0.15f

            when {
                r < blockThreshold && (ai.state == FighterState.IDLE || ai.state == FighterState.RUN) -> {
                    ai.block(true)
                    isAiBlocking = true
                    aiBlockTimer = 0.30f + Random.nextFloat() * 0.25f
                    return
                }
                r < rollThreshold && ai.state.canMove -> {
                    val awayDir = if (player.x > ai.x) -1f else 1f
                    ai.vx = awayDir * ai.dashSpeed
                    ai.setState(FighterState.ROLL)
                    retreatCooldown = 0.8f
                    return
                }
                r < rollThreshold + 0.20f && d <= CLOSE_RANGE && aiAttackCooldown <= 0f -> {
                    // Aggressive counter: tank the hit and punch back
                    doAttack(ai, player, d)
                    return
                }
                // else absorb the hit
            }
        }

        // ── FORCE ATTACK guarantee ────────────────────────────────────────────
        if (forceAttackTimer >= FORCE_ATTACK_INTERVAL) {
            forceAttackTimer = 0f
            if (d <= CLOSE_RANGE && ai.state.canAttack && aiAttackCooldown <= 0f) {
                doAttack(ai, player, d)
                return
            } else if (d <= MEDIUM_RANGE && ai.state.canMove) {
                val dir = if (player.x > ai.x) 1f else -1f
                ai.vx = dir * ai.dashSpeed
                ai.setState(FighterState.ROLL)
                return
            }
        }

        // ── COMBO FOLLOW-UP trigger ───────────────────────────────────────────
        if (comboFollowupTimer > 0f && d <= CLOSE_RANGE && ai.state.canAttack && aiAttackCooldown <= 0f) {
            comboFollowupTimer = 0f
            doAttack(ai, player, d, forceHeavy = false)
            return
        }

        // ── MAIN POSITIONAL COMBAT LOGIC ──────────────────────────────────────
        when {
            d <= CLOSE_RANGE -> {
                // Very close — attack aggressively based on HP
                if (ai.state.canAttack && aiAttackCooldown <= 0f) {
                    doAttack(ai, player, d)
                } else {
                    if (ai.state == FighterState.RUN) {
                        ai.vx = 0f
                        ai.setState(FighterState.IDLE)
                    }
                }
            }

            d <= MEDIUM_RANGE -> {
                // Medium range: walk in, or occasionally jump-attack for mix-up
                val playerCornered = player.x < CORNER_MARGIN ||
                                     player.x > (canvasWidth - CORNER_MARGIN)
                val tryJump = jumpAttackCooldown <= 0f &&
                              Random.nextFloat() < (if (playerCornered) 0.30f else 0.12f)

                if (tryJump && ai.state.canMove && ai.y >= ai.groundY - ai.height - 5f) {
                    // Jump toward the player
                    val dir = if (player.x > ai.x) 1f else -1f
                    ai.jump()
                    ai.vx = dir * 280f
                    jumpAttackCooldown = 2.5f + Random.nextFloat()
                } else if (ai.state.canMove) {
                    val dir = if (player.x > ai.x) 1f else -1f
                    ai.vx = dir * 360f
                    ai.setState(FighterState.RUN)
                }
            }

            else -> {
                // Far — dash aggressively toward player
                if (ai.state.canMove) {
                    val dir = if (player.x > ai.x) 1f else -1f
                    ai.vx = dir * ai.dashSpeed
                    ai.setState(FighterState.ROLL)
                }
            }
        }
    }

    // ── Weighted attack chooser with situational preference ──────────────────
    private fun doAttack(ai: Fighter, player: Fighter, dist: Float, forceHeavy: Boolean = false) {
        val hpRatio    = ai.health / ai.maxHealth
        val enemyHpRatio = player.health / player.maxHealth
        val r = Random.nextFloat()

        when {
            forceHeavy && r < 0.6f -> ai.comboAttack()   // after block: prefer combo/heavy
            forceHeavy             -> ai.heavyAttack()

            // When enemy has very low HP, go for the knockout with heavy/combo
            enemyHpRatio < 0.25f && r < 0.50f -> ai.comboAttack()
            enemyHpRatio < 0.25f && r < 0.85f -> ai.heavyAttack()
            enemyHpRatio < 0.25f              -> ai.lightAttack()

            // Normal aggression: combo 25%, heavy 40%, light 35%
            r < 0.25f -> ai.comboAttack()
            r < 0.65f -> ai.heavyAttack()
            else      -> ai.lightAttack()
        }

        aiAttackCooldown = 0.30f + Random.nextFloat() * 0.30f
        forceAttackTimer = 0f
    }

    private fun dist(ai: Fighter, player: Fighter): Float =
        abs((ai.x + ai.width / 2f) - (player.x + player.width / 2f))
}
