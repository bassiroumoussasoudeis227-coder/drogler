package com.example

import androidx.compose.ui.graphics.Color

/**
 * High-level screen/game state for Space Dodger.
 */
enum class GameState {
    START,
    PLAYING,
    PAUSED,
    GAME_OVER
}

/**
 * Types of bonus items falling from space.
 */
enum class BonusType {
    SCORE_BONUS, // Golden orb (+50 pts)
    SHIELD       // Blue orb (shields against collision)
}

/**
 * Player's spaceship state.
 */
data class PlayerShip(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 54f,
    val height: Float = 62f,
    val targetX: Float = 0f,
    val shieldActive: Boolean = false,
    val shieldEnergy: Float = 1f, // 1.0f when active
    val tilt: Float = 0f,         // -1f (left) to 1f (right)
    val invincibleFlashTimer: Float = 0f
)

/**
 * Asteroid falling obstacle.
 */
data class Obstacle(
    val id: Long,
    val x: Float,
    val y: Float,
    val radius: Float,
    val speedY: Float,
    val rotationAngle: Float,
    val rotationSpeed: Float,
    val vertexMultipliers: List<Float> // 8 radial multipliers creating organic rocky outline
)

/**
 * Collectible bonus power-up.
 */
data class BonusItem(
    val id: Long,
    val type: BonusType,
    val x: Float,
    val y: Float,
    val radius: Float = 22f,
    val speedY: Float = 200f,
    val pulsePhase: Float = 0f
)

/**
 * Visual particle for thrusters, explosions, and bonus collection.
 */
data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val life: Float,
    val maxLife: Float,
    val radius: Float
)

/**
 * Background star for deep space parallax.
 */
data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val speed: Float
)

/**
 * Floating notification text (e.g. "+50 PTS", "SHIELD UP!").
 */
data class FloatingNotice(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val color: Color,
    val alpha: Float = 1f,
    val vy: Float = -70f
)
