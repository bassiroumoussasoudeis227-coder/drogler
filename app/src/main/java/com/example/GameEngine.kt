package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * State-driven game engine for Space Dodger.
 * Manages simulation physics, collision detection, object spawning, and high scores.
 */
class GameEngine(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("space_dodger_prefs", Context.MODE_PRIVATE)

    var screenWidth by mutableFloatStateOf(1080f)
        private set
    var screenHeight by mutableFloatStateOf(1920f)
        private set

    var gameState by mutableStateOf(GameState.START)
    var score by mutableIntStateOf(0)
        private set
    var highScore by mutableIntStateOf(prefs?.getInt("high_score", 0) ?: 0)
        private set
    var isNewHighScore by mutableStateOf(false)
        private set
    var asteroidsDodged by mutableIntStateOf(0)
        private set
    var bonusesCollected by mutableIntStateOf(0)
        private set

    var ship by mutableStateOf(PlayerShip())
        private set

    val asteroids = mutableStateListOf<Obstacle>()
    val bonusItems = mutableStateListOf<BonusItem>()
    val particles = mutableStateListOf<Particle>()
    val stars = mutableStateListOf<Star>()
    val notices = mutableStateListOf<FloatingNotice>()

    private var nextEntityId = 1L
    private var asteroidSpawnTimer = 0f
    private var bonusSpawnTimer = 0f
    private var timeSurvived = 0f
    private var passiveScoreTimer = 0f

    init {
        initStarfield(45)
    }

    fun updateScreenSize(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            val wasDefault = screenWidth == 1080f && screenHeight == 1920f
            screenWidth = width
            screenHeight = height
            if (wasDefault || ship.y == 0f) {
                ship = ship.copy(
                    x = width / 2f,
                    y = height - 180f,
                    targetX = width / 2f
                )
            }
            if (stars.isEmpty()) {
                initStarfield(50)
            }
        }
    }

    private fun initStarfield(count: Int) {
        stars.clear()
        for (i in 0 until count) {
            stars.add(
                Star(
                    x = Random.nextFloat() * screenWidth,
                    y = Random.nextFloat() * screenHeight,
                    size = Random.nextFloat() * 3f + 1.5f,
                    alpha = Random.nextFloat() * 0.7f + 0.3f,
                    speed = Random.nextFloat() * 60f + 30f
                )
            )
        }
    }

    fun startGame() {
        score = 0
        asteroidsDodged = 0
        bonusesCollected = 0
        isNewHighScore = false
        timeSurvived = 0f
        asteroidSpawnTimer = 0f
        bonusSpawnTimer = 3f // First bonus spawns soon after starting
        passiveScoreTimer = 0f

        asteroids.clear()
        bonusItems.clear()
        particles.clear()
        notices.clear()

        val startX = screenWidth / 2f
        val startY = if (screenHeight > 300f) screenHeight - 180f else 600f
        ship = PlayerShip(
            x = startX,
            y = startY,
            targetX = startX,
            shieldActive = false,
            shieldEnergy = 0f,
            tilt = 0f
        )

        gameState = GameState.PLAYING
    }

    fun pauseGame() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED
        }
    }

    fun resumeGame() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING
        }
    }

    fun setTargetX(target: Float) {
        if (gameState == GameState.PLAYING) {
            val margin = ship.width / 2f + 12f
            val clamped = target.coerceIn(margin, screenWidth - margin)
            ship = ship.copy(targetX = clamped)
        }
    }

    fun moveShipRelative(deltaX: Float) {
        if (gameState == GameState.PLAYING) {
            val margin = ship.width / 2f + 12f
            val newTarget = (ship.targetX + deltaX).coerceIn(margin, screenWidth - margin)
            ship = ship.copy(targetX = newTarget)
        }
    }

    fun steerLeft(step: Float = 45f) {
        moveShipRelative(-step)
    }

    fun steerRight(step: Float = 45f) {
        moveShipRelative(step)
    }

    /**
     * Main simulation loop tick, executed at ~60 FPS.
     * @param dt Delta time in seconds
     */
    fun update(dt: Float) {
        // Always animate background stars for a live ambiance
        updateStarfield(dt)

        if (gameState != GameState.PLAYING) {
            updateParticles(dt)
            updateNotices(dt)
            return
        }

        timeSurvived += dt
        passiveScoreTimer += dt
        if (passiveScoreTimer >= 0.25f) {
            score += 1
            checkHighScore()
            passiveScoreTimer = 0f
        }

        // Dynamic difficulty factor: based on time survived and current score
        val difficulty = min(2.6f, 1.0f + (timeSurvived * 0.015f) + (score * 0.0012f))

        // 1. Update Player Ship Position and Tilt
        updateShip(dt)

        // 2. Spawn & Update Asteroids
        updateAsteroids(dt, difficulty)

        // 3. Spawn & Update Bonus Items
        updateBonuses(dt)

        // 4. Update Visual Particles & Notices
        updateParticles(dt)
        updateNotices(dt)

        // 5. Check Collisions
        checkCollisions()
    }

    private fun updateStarfield(dt: Float) {
        val speedMult = if (gameState == GameState.PLAYING) 1.5f else 0.8f
        for (i in stars.indices) {
            val star = stars[i]
            var newY = star.y + (star.speed * speedMult * dt)
            var newX = star.x
            if (newY > screenHeight) {
                newY = 0f
                newX = Random.nextFloat() * screenWidth
            }
            stars[i] = star.copy(x = newX, y = newY)
        }
    }

    private fun updateShip(dt: Float) {
        val dx = ship.targetX - ship.x
        val moveSpeed = 16f
        val newX = ship.x + dx * (moveSpeed * dt).coerceAtMost(1f)
        val targetTilt = (dx / 40f).coerceIn(-1f, 1f)
        val newTilt = ship.tilt + (targetTilt - ship.tilt) * (14f * dt).coerceAtMost(1f)

        ship = ship.copy(
            x = newX,
            tilt = newTilt
        )

        // Thruster flame particles
        if (Random.nextFloat() < 0.75f) {
            val exhaustX = newX + (Random.nextFloat() - 0.5f) * 10f
            val exhaustY = ship.y + ship.height / 2f
            particles.add(
                Particle(
                    x = exhaustX,
                    y = exhaustY,
                    vx = (Random.nextFloat() - 0.5f) * 30f - (newTilt * 40f),
                    vy = Random.nextFloat() * 140f + 160f,
                    color = if (Random.nextBoolean()) Color(0xFF00E5FF) else Color(0xFFFF9100),
                    life = 0f,
                    maxLife = 0.28f,
                    radius = Random.nextFloat() * 4f + 2f
                )
            )
        }
    }

    private fun updateAsteroids(dt: Float, difficulty: Float) {
        asteroidSpawnTimer += dt
        // Interval decreases as difficulty increases: 1.1s down to ~0.38s
        val spawnInterval = max(0.38f, 1.15f / difficulty)
        if (asteroidSpawnTimer >= spawnInterval) {
            asteroidSpawnTimer = 0f
            spawnAsteroid(difficulty)
        }

        val iterator = asteroids.listIterator()
        while (iterator.hasNext()) {
            val ast = iterator.next()
            val newY = ast.y + (ast.speedY * difficulty * dt)
            val newAngle = (ast.rotationAngle + ast.rotationSpeed * dt) % 360f

            if (newY - ast.radius > screenHeight) {
                // Dodged!
                iterator.remove()
                asteroidsDodged++
                score += 5
                checkHighScore()
            } else {
                iterator.set(ast.copy(y = newY, rotationAngle = newAngle))
            }
        }
    }

    private fun spawnAsteroid(difficulty: Float) {
        val radius = Random.nextFloat() * 18f + 20f // 20 to 38 radius
        val x = Random.nextFloat() * (screenWidth - radius * 2f - 24f) + radius + 12f
        val speedY = Random.nextFloat() * 160f + 260f // 260 to 420 px/s base

        // 8 irregular radii points for rocky asteroid shape
        val vertices = List(8) {
            0.78f + Random.nextFloat() * 0.44f
        }

        asteroids.add(
            Obstacle(
                id = nextEntityId++,
                x = x,
                y = -radius - 10f,
                radius = radius,
                speedY = speedY,
                rotationAngle = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 160f,
                vertexMultipliers = vertices
            )
        )
    }

    private fun updateBonuses(dt: Float) {
        bonusSpawnTimer += dt
        // Bonus spawns every 7 - 10 seconds
        if (bonusSpawnTimer >= 8f) {
            bonusSpawnTimer = 0f
            val isShield = !ship.shieldActive && Random.nextFloat() < 0.45f
            val type = if (isShield) BonusType.SHIELD else BonusType.SCORE_BONUS
            val radius = 24f
            val x = Random.nextFloat() * (screenWidth - radius * 2f - 40f) + radius + 20f

            bonusItems.add(
                BonusItem(
                    id = nextEntityId++,
                    type = type,
                    x = x,
                    y = -radius - 10f,
                    radius = radius,
                    speedY = 190f,
                    pulsePhase = 0f
                )
            )
        }

        val iterator = bonusItems.listIterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val newY = item.y + (item.speedY * dt)
            val newPhase = (item.pulsePhase + dt * 4.5f)

            if (newY - item.radius > screenHeight) {
                iterator.remove()
            } else {
                iterator.set(item.copy(y = newY, pulsePhase = newPhase))
            }
        }
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.listIterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val newLife = p.life + dt
            if (newLife >= p.maxLife) {
                iterator.remove()
            } else {
                iterator.set(
                    p.copy(
                        x = p.x + p.vx * dt,
                        y = p.y + p.vy * dt,
                        life = newLife
                    )
                )
            }
        }
    }

    private fun updateNotices(dt: Float) {
        val iterator = notices.listIterator()
        while (iterator.hasNext()) {
            val n = iterator.next()
            val newAlpha = n.alpha - dt * 1.4f
            if (newAlpha <= 0f) {
                iterator.remove()
            } else {
                iterator.set(
                    n.copy(
                        y = n.y + n.vy * dt,
                        alpha = newAlpha
                    )
                )
            }
        }
    }

    private fun checkCollisions() {
        val shipRadius = ship.width * 0.42f
        val shipX = ship.x
        val shipY = ship.y

        // Check Bonus Items
        val bonusIterator = bonusItems.listIterator()
        while (bonusIterator.hasNext()) {
            val item = bonusIterator.next()
            val dist = hypot(shipX - item.x, shipY - item.y)
            if (dist < (shipRadius + item.radius + 6f)) {
                bonusIterator.remove()
                bonusesCollected++
                when (item.type) {
                    BonusType.SCORE_BONUS -> {
                        score += 50
                        checkHighScore()
                        spawnCollectParticles(item.x, item.y, Color(0xFFFFD700))
                        addNotice("+50 PTS", item.x, item.y - 20f, Color(0xFFFFD700))
                    }
                    BonusType.SHIELD -> {
                        ship = ship.copy(shieldActive = true, shieldEnergy = 1f)
                        spawnCollectParticles(item.x, item.y, Color(0xFF00E5FF))
                        addNotice("SHIELD UP!", item.x, item.y - 20f, Color(0xFF00E5FF))
                    }
                }
            }
        }

        // Check Asteroids
        val asteroidIterator = asteroids.listIterator()
        while (asteroidIterator.hasNext()) {
            val ast = asteroidIterator.next()
            val collisionDist = hypot(shipX - ast.x, shipY - ast.y)
            val effectiveShipRadius = if (ship.shieldActive) ship.width * 0.65f else shipRadius

            if (collisionDist < (effectiveShipRadius + ast.radius * 0.85f)) {
                if (ship.shieldActive) {
                    // Shield absorbs collision!
                    ship = ship.copy(shieldActive = false, shieldEnergy = 0f)
                    asteroidIterator.remove()
                    spawnShieldBurstParticles(shipX, shipY)
                    spawnAsteroidDestructionParticles(ast.x, ast.y, ast.radius)
                    addNotice("SHIELD BROKEN!", shipX, shipY - 45f, Color(0xFF00E5FF))
                } else {
                    // Fatal collision -> Game Over
                    asteroidIterator.remove()
                    spawnShipExplosionParticles(shipX, shipY)
                    spawnAsteroidDestructionParticles(ast.x, ast.y, ast.radius)
                    addNotice("CRITICAL HIT!", shipX, shipY - 40f, Color(0xFFFF3D00))
                    triggerGameOver()
                    break
                }
            }
        }
    }

    private fun triggerGameOver() {
        gameState = GameState.GAME_OVER
        checkHighScore()
    }

    private fun checkHighScore() {
        if (score > highScore) {
            highScore = score
            isNewHighScore = true
            prefs?.edit()?.putInt("high_score", highScore)?.apply()
        }
    }

    private fun addNotice(text: String, x: Float, y: Float, color: Color) {
        notices.add(
            FloatingNotice(
                id = nextEntityId++,
                text = text,
                x = x.coerceIn(80f, screenWidth - 80f),
                y = y,
                color = color
            )
        )
    }

    private fun spawnCollectParticles(x: Float, y: Float, color: Color) {
        for (i in 0 until 18) {
            val angle = Random.nextFloat() * 6.283f
            val speed = Random.nextFloat() * 180f + 60f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = color,
                    life = 0f,
                    maxLife = 0.5f,
                    radius = Random.nextFloat() * 4f + 2f
                )
            )
        }
    }

    private fun spawnShieldBurstParticles(x: Float, y: Float) {
        for (i in 0 until 28) {
            val angle = (i.toFloat() / 28f) * 6.283f
            val speed = Random.nextFloat() * 220f + 120f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = Color(0xFF00E5FF),
                    life = 0f,
                    maxLife = 0.55f,
                    radius = Random.nextFloat() * 5f + 3f
                )
            )
        }
    }

    private fun spawnAsteroidDestructionParticles(x: Float, y: Float, radius: Float) {
        val count = (radius * 0.7f).toInt().coerceIn(12, 26)
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 6.283f
            val speed = Random.nextFloat() * 200f + 40f
            val shade = Random.nextFloat() * 0.4f + 0.5f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = Color(shade, shade, shade + 0.05f),
                    life = 0f,
                    maxLife = 0.6f,
                    radius = Random.nextFloat() * 4.5f + 2f
                )
            )
        }
    }

    private fun spawnShipExplosionParticles(x: Float, y: Float) {
        for (i in 0 until 40) {
            val angle = Random.nextFloat() * 6.283f
            val speed = Random.nextFloat() * 320f + 80f
            val colors = listOf(Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFFFFEA00), Color.White)
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = colors[Random.nextInt(colors.size)],
                    life = 0f,
                    maxLife = 0.85f,
                    radius = Random.nextFloat() * 6f + 3f
                )
            )
        }
    }
}
