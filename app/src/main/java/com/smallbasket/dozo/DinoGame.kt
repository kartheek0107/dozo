package com.smallbasket.dozo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive

private val AmberAccent = Color(0xFFF59E0B)
private val OrangeObstacle = Color(0xFFF97316)
private val GroundColor = Color(0xFFE5E7EB)
private val CloudColor = Color(0xFFF3F4F6)
private val DinoGrey = Color(0xFF535353)

data class DinoState(
    val y: Float = 0f,
    val dy: Float = 0f,
    val jumpCount: Int = 0
)

data class Obstacle(
    val x: Float,
    val width: Float,
    val height: Float
)

data class Cloud(
    val x: Float,
    val y: Float,
    val radius: Float,
    val speed: Float
)

@Composable
fun DinoGame(modifier: Modifier = Modifier) {
    var gameActive by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }

    var dino by remember { mutableStateOf(DinoState()) }
    var obstacles by remember { mutableStateOf(listOf<Obstacle>()) }
    var clouds by remember { mutableStateOf(listOf<Cloud>()) }
    var speed by remember { mutableFloatStateOf(7.5f) }
    var frame by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current
    val groundY = with(density) { 200.dp.toPx() } 
    val dinoWidth = with(density) { 44.dp.toPx() }
    val dinoHeight = with(density) { 44.dp.toPx() }

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(gameActive, isGameOver) {
        if (gameActive && !isGameOver) {
            while (isActive) {
                withFrameMillis { _ ->
                    frame++
                    
                    if (frame % 400 == 0L) speed += 0.5f

                    // Dino Physics (Gravity & Jumping)
                    var newY = dino.y + dino.dy
                    var newDy = dino.dy
                    var newJumpCount = dino.jumpCount

                    if (newY < 0f) {
                        newDy += 0.85f // Gravity
                    } else {
                        newY = 0f
                        newDy = 0f
                        newJumpCount = 0 // Reset jump count on ground
                    }
                    dino = dino.copy(y = newY, dy = newDy, jumpCount = newJumpCount)

                    // Clouds
                    if (frame % 150 == 0L) {
                        clouds = clouds + Cloud(
                            x = 1200f,
                            y = (40..140).random().toFloat(),
                            radius = (25..45).random().toFloat(),
                            speed = (0.8f..1.8f).random()
                        )
                    }
                    clouds = clouds.map { it.copy(x = it.x - it.speed) }.filter { it.x > -200f }

                    // Obstacles Spawning
                    if (frame % (Math.max(60, (100 - (speed * 2)).toInt())..120).random().toLong() == 0L) {
                        obstacles = obstacles + Obstacle(
                            x = 1100f,
                            width = 35f,
                            height = (45..75).random().toFloat()
                        )
                    }
                    
                    val currentObstacles = obstacles.map { it.copy(x = it.x - speed) }
                    obstacles = currentObstacles.filter { it.x > -100f }

                    // Collision Detection (Tight Hitbox)
                    val dinoRect = Rect(
                        offset = Offset(60f + 15f, groundY + dino.y - dinoHeight + 10f),
                        size = Size(dinoWidth - 30f, dinoHeight - 15f)
                    )

                    for (obs in currentObstacles) {
                        val obsRect = Rect(
                            offset = Offset(obs.x, groundY - obs.height),
                            size = Size(obs.width, obs.height)
                        )
                        if (dinoRect.overlaps(obsRect)) {
                            isGameOver = true
                            gameActive = false
                        }
                    }

                    // Score increment
                    if (currentObstacles.any { it.x + it.width < 60f && !obstacles.contains(it) }) {
                        score++
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = interactionSource,
                indication = null, 
                enabled = gameActive && !isGameOver
            ) {
                // Double Jump Logic
                if (dino.jumpCount < 2) {
                    dino = dino.copy(dy = -15f, jumpCount = dino.jumpCount + 1)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val scale = width / 1000f

            // Ground line
            drawRect(
                color = GroundColor,
                topLeft = Offset(0f, groundY),
                size = Size(width, 3f)
            )

            // Clouds
            clouds.forEach { cloud ->
                val cx = cloud.x * scale
                drawCircle(color = CloudColor, radius = cloud.radius, center = Offset(cx, cloud.y))
                drawCircle(color = CloudColor, radius = cloud.radius * 0.8f, center = Offset(cx + cloud.radius * 0.6f, cloud.y - cloud.radius * 0.3f))
                drawCircle(color = CloudColor, radius = cloud.radius * 1.2f, center = Offset(cx + cloud.radius * 1.2f, cloud.y))
            }

            // Obstacles (Cactus Shaped)
            obstacles.forEach { obs ->
                drawCactus(
                    x = obs.x * scale,
                    y = groundY - obs.height,
                    w = obs.width,
                    h = obs.height
                )
            }

            // Dino (Classic Grey Pixel Art)
            val dinoX = 60f
            drawPixelDino(
                x = dinoX, 
                y = groundY + dino.y - dinoHeight, 
                w = dinoWidth, 
                frame = frame, 
                isJumping = dino.jumpCount > 0
            )
        }

        // Score display
        Text(
            text = score.toString().padStart(5, '0'),
            color = DinoGrey,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
        )

        // Overlay for Start/GameOver
        if (!gameActive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (isGameOver) {
                    Text(
                        text = "CRASHED !!!",
                        color = Color(0xFFEF4444),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                Button(
                    onClick = {
                        isGameOver = false
                        score = 0
                        obstacles = emptyList()
                        clouds = emptyList()
                        speed = 7.5f
                        frame = 0
                        dino = DinoState()
                        gameActive = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = if (isGameOver) "RETRY DASH" else "START DASH",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCactus(x: Float, y: Float, w: Float, h: Float) {
    val stemW = w * 0.35f
    val armW = w * 0.4f
    val armH = h * 0.25f
    
    // Main stem
    drawRoundRect(
        color = OrangeObstacle,
        topLeft = Offset(x + (w - stemW) / 2, y),
        size = Size(stemW, h),
        cornerRadius = CornerRadius(6f, 6f)
    )
    
    // Left arm
    drawRoundRect(
        color = OrangeObstacle,
        topLeft = Offset(x, y + h * 0.3f),
        size = Size(armW, armH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = OrangeObstacle,
        topLeft = Offset(x, y + h * 0.15f),
        size = Size(stemW * 0.6f, armH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    
    // Right arm
    drawRoundRect(
        color = OrangeObstacle,
        topLeft = Offset(x + w - armW, y + h * 0.45f),
        size = Size(armW, armH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = OrangeObstacle,
        topLeft = Offset(x + w - stemW * 0.6f, y + h * 0.3f),
        size = Size(stemW * 0.6f, armH),
        cornerRadius = CornerRadius(4f, 4f)
    )
}

private fun DrawScope.drawPixelDino(x: Float, y: Float, w: Float, frame: Long, isJumping: Boolean) {
    val p = w / 20f // 20x20 pixel grid roughly
    
    // 1. Head (Right side)
    drawRect(DinoGrey, Offset(x + p * 11, y), Size(p * 9, p * 5))
    drawRect(DinoGrey, Offset(x + p * 11, y + p * 5), Size(p * 10, p * 3))
    // Jaw detail
    drawRect(DinoGrey, Offset(x + p * 15, y + p * 8), Size(p * 5, p * 1))
    // Eye
    drawRect(Color.White, Offset(x + p * 13, y + p), Size(p, p))
    
    // 2. Neck
    drawRect(DinoGrey, Offset(x + p * 11, y + p * 8), Size(p * 4, p * 3))
    
    // 3. Body
    drawRect(DinoGrey, Offset(x + p * 4, y + p * 11), Size(p * 11, p * 5))
    drawRect(DinoGrey, Offset(x + p * 2, y + p * 12), Size(p * 2, p * 3)) // Belly back
    
    // 4. Tail (Left side)
    drawRect(DinoGrey, Offset(x, y + p * 9), Size(p, p * 4))
    drawRect(DinoGrey, Offset(x + p, y + p * 10), Size(p, p * 4))
    
    // 5. Arms
    drawRect(DinoGrey, Offset(x + p * 15, y + p * 11), Size(p * 2, p * 2))
    
    // 6. Legs (Animation)
    val legAnim = (frame / 6 % 2).toInt()
    if (isJumping) {
        drawRect(DinoGrey, Offset(x + p * 5, y + p * 16), Size(p * 2, p * 4))
        drawRect(DinoGrey, Offset(x + p * 9, y + p * 16), Size(p * 2, p * 4))
    } else {
        // Left Leg
        drawRect(DinoGrey, Offset(x + p * 5, y + p * 16), Size(p * 2, if (legAnim == 0) p * 4 else p * 2))
        // Right Leg
        drawRect(DinoGrey, Offset(x + p * 9, y + p * 16), Size(p * 2, if (legAnim == 1) p * 4 else p * 2))
    }
}

private fun ClosedRange<Float>.random() = 
    (Math.random() * (endInclusive - start) + start).toFloat()
