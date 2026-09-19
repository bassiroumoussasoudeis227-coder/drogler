package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the complete Space Dodger game canvas with vector graphics,
 * dynamic lighting effects, and particle trails.
 */
@Composable
fun GameCanvas(
    engine: GameEngine,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        engine.updateScreenSize(size.width, size.height)

        // 1. Draw Deep Space Background
        drawSpaceBackground(size)

        // 2. Draw Parallax Stars
        for (star in engine.stars) {
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.size,
                center = Offset(star.x, star.y)
            )
        }

        // 3. Draw Particles (under entities)
        for (p in engine.particles) {
            val progress = p.life / p.maxLife
            val alpha = (1f - progress).coerceIn(0f, 1f)
            val currentRadius = p.radius * (1f - progress * 0.4f)
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = currentRadius,
                center = Offset(p.x, p.y)
            )
        }

        // 4. Draw Bonus Items (Golden Orbs & Shield Orbs)
        for (item in engine.bonusItems) {
            drawBonusItem(item)
        }

        // 5. Draw Falling Asteroids
        for (ast in engine.asteroids) {
            drawAsteroid(ast)
        }

        // 6. Draw Player Spaceship (if not dead or in start/playing)
        if (engine.gameState != GameState.GAME_OVER) {
            drawSpaceship(engine.ship)
        }

        // 7. Draw Floating Notice Texts
        for (notice in engine.notices) {
            drawFloatingNotice(notice, textMeasurer)
        }
    }
}

/**
 * Draws the cosmic space gradient.
 */
private fun DrawScope.drawSpaceBackground(size: Size) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF030511),
                Color(0xFF070B1F),
                Color(0xFF0B1232),
                Color(0xFF050716)
            ),
            startY = 0f,
            endY = size.height
        ),
        size = size
    )

    // Subtle ambient cosmic nebula glow in the center-top
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x221E88E5),
                Color(0x117C4DFF),
                Color.Transparent
            ),
            center = Offset(size.width * 0.5f, size.height * 0.35f),
            radius = size.width * 0.7f
        ),
        radius = size.width * 0.7f,
        center = Offset(size.width * 0.5f, size.height * 0.35f)
    )
}

/**
 * Draws the vector spaceship with engine glow, wings, cockpit, and active energy shield.
 */
private fun DrawScope.drawSpaceship(ship: PlayerShip) {
    val cx = ship.x
    val cy = ship.y
    val w = ship.width
    val h = ship.height
    val halfW = w / 2f
    val halfH = h / 2f

    // Rotate spaceship according to banking tilt
    rotate(degrees = ship.tilt * 22f, pivot = Offset(cx, cy)) {
        // Thruster flame base
        val flamePath = Path().apply {
            moveTo(cx - halfW * 0.35f, cy + halfH * 0.8f)
            lineTo(cx, cy + halfH * 1.35f)
            lineTo(cx + halfW * 0.35f, cy + halfH * 0.8f)
            close()
        }
        drawPath(
            path = flamePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF00E5FF), Color(0xFFFF9100), Color.Transparent),
                startY = cy + halfH * 0.8f,
                endY = cy + halfH * 1.45f
            )
        )

        // Main Spaceship Hull
        val hullPath = Path().apply {
            moveTo(cx, cy - halfH)                       // Nose tip
            lineTo(cx + halfW * 0.35f, cy - halfH * 0.2f) // Forward fuselage
            lineTo(cx + halfW, cy + halfH * 0.75f)       // Right wingtip
            lineTo(cx + halfW * 0.55f, cy + halfH * 0.65f) // Right engine intake
            lineTo(cx + halfW * 0.3f, cy + halfH)        // Right engine exhaust
            lineTo(cx - halfW * 0.3f, cy + halfH)        // Left engine exhaust
            lineTo(cx - halfW * 0.55f, cy + halfH * 0.65f) // Left engine intake
            lineTo(cx - halfW, cy + halfH * 0.75f)       // Left wingtip
            lineTo(cx - halfW * 0.35f, cy - halfH * 0.2f) // Forward fuselage left
            close()
        }

        // Hull body fill
        drawPath(
            path = hullPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE0F7FA),
                    Color(0xFF80DEEA),
                    Color(0xFF0097A7),
                    Color(0xFF006064)
                ),
                startY = cy - halfH,
                endY = cy + halfH
            )
        )

        // Hull outline
        drawPath(
            path = hullPath,
            color = Color(0xFFE0F7FA),
            style = Stroke(width = 2f)
        )

        // Wing racing stripes (Neon Cyan)
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(cx - halfW * 0.7f, cy + halfH * 0.55f),
            end = Offset(cx - halfW * 0.25f, cy + halfH * 0.1f),
            strokeWidth = 2.5f
        )
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(cx + halfW * 0.7f, cy + halfH * 0.55f),
            end = Offset(cx + halfW * 0.25f, cy + halfH * 0.1f),
            strokeWidth = 2.5f
        )

        // Glass Cockpit Canopy
        val cockpitPath = Path().apply {
            moveTo(cx, cy - halfH * 0.6f)
            lineTo(cx + halfW * 0.2f, cy - halfH * 0.05f)
            lineTo(cx, cy + halfH * 0.25f)
            lineTo(cx - halfW * 0.2f, cy - halfH * 0.05f)
            close()
        }
        drawPath(
            path = cockpitPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF26C6DA), Color(0xFF006064)),
                startY = cy - halfH * 0.6f,
                endY = cy + halfH * 0.25f
            )
        )

        // Active Energy Shield Aura
        if (ship.shieldActive) {
            val shieldRadius = halfW * 1.55f
            // Outer glowing aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x3300E5FF),
                        Color(0x8800E5FF)
                    ),
                    center = Offset(cx, cy),
                    radius = shieldRadius
                ),
                radius = shieldRadius,
                center = Offset(cx, cy)
            )
            // Crisp shield ring
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = shieldRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f)
            )
            // Accent energy arc
            drawArc(
                color = Color.White,
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - shieldRadius, cy - shieldRadius),
                size = Size(shieldRadius * 2f, shieldRadius * 2f),
                style = Stroke(width = 3.5f)
            )
        }
    }
}

/**
 * Draws an irregular rocky asteroid with rotation and crater details.
 */
private fun DrawScope.drawAsteroid(ast: Obstacle) {
    val cx = ast.x
    val cy = ast.y
    val r = ast.radius
    val numPoints = ast.vertexMultipliers.size

    withTransform({
        rotate(degrees = ast.rotationAngle, pivot = Offset(cx, cy))
    }) {
        val rockPath = Path()
        for (i in 0 until numPoints) {
            val angle = (i.toFloat() / numPoints) * 6.28318f
            val rad = r * ast.vertexMultipliers[i]
            val px = cx + cos(angle) * rad
            val py = cy + sin(angle) * rad
            if (i == 0) rockPath.moveTo(px, py) else rockPath.lineTo(px, py)
        }
        rockPath.close()

        // Base Rock Shading (Dark stone with highlights)
        drawPath(
            path = rockPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF8D6E63),
                    Color(0xFF5D4037),
                    Color(0xFF3E2723),
                    Color(0xFF231612)
                ),
                center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                radius = r * 1.2f
            )
        )

        // Rocky Outline
        drawPath(
            path = rockPath,
            color = Color(0xFFA1887F),
            style = Stroke(width = 2f)
        )

        // Internal Craters
        drawCircle(
            color = Color(0x77231612),
            radius = r * 0.22f,
            center = Offset(cx - r * 0.3f, cy - r * 0.2f),
            style = Fill
        )
        drawCircle(
            color = Color(0x55A1887F),
            radius = r * 0.22f,
            center = Offset(cx - r * 0.3f, cy - r * 0.2f),
            style = Stroke(width = 1.2f)
        )

        drawCircle(
            color = Color(0x66231612),
            radius = r * 0.18f,
            center = Offset(cx + r * 0.25f, cy + r * 0.3f),
            style = Fill
        )
    }
}

/**
 * Draws bonus collectibles (Golden Score Orb or Blue Shield Orb).
 */
private fun DrawScope.drawBonusItem(item: BonusItem) {
    val cx = item.x
    val cy = item.y
    val r = item.radius
    val pulse = (sin(item.pulsePhase) + 1f) * 0.5f // 0f to 1f

    when (item.type) {
        BonusType.SCORE_BONUS -> {
            // Golden Orb +50 pts
            val haloRadius = r * (1.25f + pulse * 0.25f)
            // Outer golden aura
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = 0.25f + pulse * 0.25f),
                radius = haloRadius,
                center = Offset(cx, cy)
            )
            // Sphere body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF9C4),
                        Color(0xFFFFD54F),
                        Color(0xFFFF8F00)
                    ),
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
            // Golden Rim
            drawCircle(
                color = Color(0xFFFFF59D),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )
            // Diamond / Star Emblem
            val starPath = Path().apply {
                moveTo(cx, cy - r * 0.55f)
                lineTo(cx + r * 0.35f, cy)
                lineTo(cx, cy + r * 0.55f)
                lineTo(cx - r * 0.35f, cy)
                close()
            }
            drawPath(path = starPath, color = Color.White)
        }

        BonusType.SHIELD -> {
            // Cyan/Blue Shield Orb
            val haloRadius = r * (1.3f + pulse * 0.25f)
            // Outer cyan aura
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.3f + pulse * 0.25f),
                radius = haloRadius,
                center = Offset(cx, cy)
            )
            // Sphere body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE0F7FA),
                        Color(0xFF00E5FF),
                        Color(0xFF0277BD)
                    ),
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
            // Shield Crest Icon
            val crestPath = Path().apply {
                moveTo(cx, cy - r * 0.5f)
                lineTo(cx + r * 0.4f, cy - r * 0.25f)
                lineTo(cx + r * 0.35f, cy + r * 0.2f)
                lineTo(cx, cy + r * 0.52f)
                lineTo(cx - r * 0.35f, cy + r * 0.2f)
                lineTo(cx - r * 0.4f, cy - r * 0.25f)
                close()
            }
            drawPath(path = crestPath, color = Color.White)
            drawCircle(
                color = Color(0xFFE0F7FA),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )
        }
    }
}

/**
 * Draws floating feedback texts using pure Compose text drawing.
 */
private fun DrawScope.drawFloatingNotice(notice: FloatingNotice, textMeasurer: TextMeasurer) {
    val layout = textMeasurer.measure(
        text = notice.text,
        style = TextStyle(
            color = notice.color.copy(alpha = notice.alpha.coerceIn(0f, 1f)),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            x = notice.x - layout.size.width / 2f,
            y = notice.y - layout.size.height / 2f
        )
    )
}
