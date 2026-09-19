package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive

/**
 * Main Space Dodger game screen featuring:
 * - 60 FPS coroutine tick loop with withFrameNanos
 * - Tap Left/Right & Horizontal Drag Controls
 * - Start Screen, HUD, Pause Overlay, and Game Over Dialog
 */
@Composable
fun SpaceDodgerApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember { GameEngine(context) }

    // State-driven Game Loop: 60 FPS V-Sync tick
    LaunchedEffect(engine.gameState) {
        if (engine.gameState == GameState.PLAYING) {
            var lastTimeNanos = withFrameNanos { it }
            while (isActive && engine.gameState == GameState.PLAYING) {
                withFrameNanos { nowNanos ->
                    val dt = ((nowNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastTimeNanos = nowNanos
                    engine.update(dt)
                }
            }
        }
    }

    // Ambient stars animation while on menus
    LaunchedEffect(engine.gameState) {
        if (engine.gameState != GameState.PLAYING) {
            var lastTimeNanos = withFrameNanos { it }
            while (isActive && engine.gameState != GameState.PLAYING) {
                withFrameNanos { nowNanos ->
                    val dt = ((nowNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastTimeNanos = nowNanos
                    engine.update(dt)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030511))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        engine.updateScreenSize(widthPx, heightPx)

        // 1. Primary Game Canvas
        GameCanvas(
            engine = engine,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Touch Controls Layer (Tap left/right halves or drag anywhere)
        if (engine.gameState == GameState.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_touch_controller")
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Tap left or right half
                            if (offset.x < size.width / 2f) {
                                engine.steerLeft(step = 65f)
                            } else {
                                engine.steerRight(step = 65f)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                engine.setTargetX(offset.x)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                engine.setTargetX(change.position.x)
                            }
                        )
                    }
            )

            // 3. Playing Screen HUD
            GameHud(
                engine = engine,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            // Subtle Steer Controls at Bottom for accessible one-handed play
            SteeringAssistZones(
                onSteerLeft = { engine.steerLeft(step = 50f) },
                onSteerRight = { engine.steerRight(step = 50f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            )
        }

        // 4. Start Screen Overlay
        AnimatedVisibility(
            visible = engine.gameState == GameState.START,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f),
            modifier = Modifier.fillMaxSize()
        ) {
            StartScreen(
                highScore = engine.highScore,
                onStartGame = { engine.startGame() }
            )
        }

        // 5. Pause Screen Overlay
        AnimatedVisibility(
            visible = engine.gameState == GameState.PAUSED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PauseOverlay(
                onResume = { engine.resumeGame() },
                onRestart = { engine.startGame() },
                onMainMenu = { engine.gameState = GameState.START }
            )
        }

        // 6. Game Over Screen Overlay
        AnimatedVisibility(
            visible = engine.gameState == GameState.GAME_OVER,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.fillMaxSize()
        ) {
            GameOverScreen(
                score = engine.score,
                highScore = engine.highScore,
                isNewHighScore = engine.isNewHighScore,
                asteroidsDodged = engine.asteroidsDodged,
                bonusesCollected = engine.bonusesCollected,
                onRestart = { engine.startGame() },
                onMainMenu = { engine.gameState = GameState.START }
            )
        }
    }
}

/**
 * Top HUD displaying score, high score, shield status, and pause button.
 */
@Composable
private fun GameHud(
    engine: GameEngine,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0x990A0E23),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score Column
            Column {
                Text(
                    text = "SCORE",
                    color = Color(0xFF80DEEA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${engine.score}",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("hud_current_score")
                )
            }

            // Shield Status Badge
            ShieldStatusBadge(active = engine.ship.shieldActive)

            // High Score
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BEST",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "${engine.highScore}",
                    color = Color(0xFFFFECB3),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("hud_high_score")
                )
            }

            // Pause Button
            IconButton(
                onClick = { engine.pauseGame() },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x22FFFFFF), CircleShape)
                    .testTag("hud_pause_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/**
 * Visual badge indicating whether kinetic shield is active.
 */
@Composable
private fun ShieldStatusBadge(active: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Surface(
        color = if (active) Color(0x3300E5FF) else Color(0x22FFFFFF),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .border(
                width = 1.5.dp,
                color = if (active) Color(0xFF00E5FF).copy(alpha = glowAlpha) else Color(0x44FFFFFF),
                shape = RoundedCornerShape(50)
            )
            .testTag("shield_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Shield Status",
                tint = if (active) Color(0xFF00E5FF) else Color(0x66FFFFFF),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (active) "SHIELD ON" else "NO SHIELD",
                color = if (active) Color(0xFF00E5FF) else Color(0x77FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Subtle on-screen steering assist touch zones.
 */
@Composable
private fun SteeringAssistZones(
    onSteerLeft: () -> Unit,
    onSteerRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            color = Color(0x2200E5FF),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .size(width = 80.dp, height = 52.dp)
                .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSteerLeft
                )
                .testTag("steer_left_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "◀ LEFT",
                    color = Color(0xFF80DEEA),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Surface(
            color = Color(0x2200E5FF),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .size(width = 80.dp, height = 52.dp)
                .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSteerRight
                )
                .testTag("steer_right_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "RIGHT ▶",
                    color = Color(0xFF80DEEA),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Start Screen with Title, High Score, How-to-play info, and Start button.
 */
@Composable
private fun StartScreen(
    highScore: Int,
    onStartGame: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_button")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC030511))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Neon Game Title
            Text(
                text = "SPACE DODGER",
                color = Color(0xFF00E5FF),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "ASTEROID HAZARD DRIFT",
                color = Color(0xFF80DEEA),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // High Score Banner
            Surface(
                color = Color(0x33FFD700),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .border(1.dp, Color(0x66FFD700), RoundedCornerShape(16.dp))
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECORD: $highScore PTS",
                        color = Color(0xFFFFECB3),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Instructions Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x440D1B2A)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(20.dp))
                    .padding(bottom = 32.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "FLIGHT BRIEFING",
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    InstructionRow(
                        iconColor = Color(0xFF00E5FF),
                        title = "Steering",
                        desc = "Tap left/right screen halves or drag to maneuver"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InstructionRow(
                        iconColor = Color(0xFFFFD700),
                        title = "Golden Orbs",
                        desc = "Collect to score +50 bonus points"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InstructionRow(
                        iconColor = Color(0xFF29B6F6),
                        title = "Blue Shield Orbs",
                        desc = "Provides kinetic shield absorbing 1 collision"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InstructionRow(
                        iconColor = Color(0xFFFF5252),
                        title = "Asteroids",
                        desc = "Dodge oncoming space rocks as speed increases"
                    )
                }
            }

            // Start Game Button
            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .scale(pulseScale)
                    .fillMaxWidth(0.85f)
                    .height(58.dp)
                    .testTag("start_game_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF00363A)
                ),
                shape = RoundedCornerShape(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TAP TO START",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun InstructionRow(
    iconColor: Color,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(iconColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Pause Overlay dialog.
 */
@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD030511)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C142E)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MISSION PAUSED",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("resume_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF00363A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("RESUME MISSION", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pause_restart_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("RESTART", color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onMainMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pause_main_menu_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("MAIN MENU", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Game Over Screen with score summary and play again option.
 */
@Composable
private fun GameOverScreen(
    score: Int,
    highScore: Int,
    isNewHighScore: Boolean,
    asteroidsDodged: Int,
    bonusesCollected: Int,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE05020B)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D22)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.5.dp, Color(0xFFFF3D00), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Game Over Header
                Text(
                    text = "GAME OVER",
                    color = Color(0xFFFF3D00),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                if (isNewHighScore) {
                    Surface(
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "★ NEW BEST RECORD! ★",
                            color = Color(0xFF3E2723),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Score Stat Card
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "FINAL SCORE",
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$score",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("game_over_final_score")
                        )
                        Text(
                            text = "HIGH SCORE: $highScore",
                            color = Color(0xFFFFD54F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detailed Statistics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$asteroidsDodged",
                            color = Color(0xFF80DEEA),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dodged",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$bonusesCollected",
                            color = Color(0xFFFFD54F),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bonuses",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Restart Button
                Button(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("play_again_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF00363A)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PLAY AGAIN",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onMainMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("game_over_main_menu_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "MAIN MENU",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
