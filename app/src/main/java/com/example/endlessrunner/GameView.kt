package com.example.endlessrunner

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.SurfaceView
import android.view.MotionEvent
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), Runnable {

    private enum class GameState {
        START, PLAYING, GAME_OVER
    }

    private var thread: Thread? = null
    private var isPlaying = false
    private var gameState = GameState.START
    
    private val textPaint = Paint().apply {
        textSize = 60f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.BLACK
        isAntiAlias = true
    }
    
    private val buttonPaint = Paint().apply {
        color = Color.parseColor("#8E44AD") // Nice purple
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val buttonStrokePaint = Paint().apply {
        color = Color.parseColor("#C084FC")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false // Faster for pre-scaled bitmaps
        isDither = false
    }

    private val overlayPaint = Paint().apply {
        color = Color.argb(150, 0, 0, 0)
    }

    private val playerRect = RectF()
    private val obstacleRect = RectF()
    private val tryAgainRect = RectF()
    private val menuRect = RectF()
    private val textBounds = Rect()

    private val gravityZonePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private var run1: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.run1)
    private var run2: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.run2)
    private var jumpImg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.jump)
    private var bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.background)
    private var floor: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.floor)
    private var logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.logo)
    private var obstacleBitmaps: Array<Bitmap> = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.obstacle1),
        BitmapFactory.decodeResource(resources, R.drawable.obstacle2),
        BitmapFactory.decodeResource(resources, R.drawable.obstacle3)
    )

    private var frame = 0
    private var frameTimer = 0L

    private var bgX = 0f
    private var floorX = 0f

    private val playerSize = 160f
    private val obstacleSize = 150f
    private var groundLevel = 0f 
    private var floorY = 0f

    private var playerX = 200f
    private var playerY = 0f
    private var velocity = 0f
    private val gravity = 1.8f
    private var gravityDirection = 1 // 1 = normal, -1 = upside down
    private var jumpCount = 0
    private var score = 0
    private var highScore = 0
    private var gameSpeed = 15f
    private var isInitialized = false

    private class Obstacle(var x: Float, var y: Float, val bitmap: Bitmap, val isGravityZone: Boolean = false, val isCeiling: Boolean = false)
    private val activeObstacles = mutableListOf<Obstacle>()
    private var nextSpawnDistance = 0f

    private val sharedPreferences = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    init {
        highScore = sharedPreferences.getInt("highScore", 0)
        
        run1 = Bitmap.createScaledBitmap(run1, playerSize.toInt(), playerSize.toInt(), false)
        run2 = Bitmap.createScaledBitmap(run2, playerSize.toInt(), playerSize.toInt(), false)
        jumpImg = Bitmap.createScaledBitmap(jumpImg, playerSize.toInt(), playerSize.toInt(), false)

        obstacleBitmaps = obstacleBitmaps.map {
            Bitmap.createScaledBitmap(it, obstacleSize.toInt(), obstacleSize.toInt(), false)
        }.toTypedArray()
    }

    private fun initGameDimensions() {
        if (width > 0 && height > 0) {
            floorY = height * 0.65f
            val targetFloorHeight = (height * 0.15f).toInt()
            floor = Bitmap.createScaledBitmap(floor, width, targetFloorHeight, false)

            val surfaceOffset = targetFloorHeight * 0.1f
            groundLevel = (floorY + surfaceOffset) - playerSize
            playerY = groundLevel
            
            // Scale background to screen height and ensure it covers at least the width
            val bgAspectRatio = bg.width.toFloat() / bg.height.toFloat()
            var bgWidth = (height.toFloat() * bgAspectRatio).toInt()
            if (bgWidth < width) bgWidth = width // Stretch if too narrow
            bg = Bitmap.createScaledBitmap(bg, bgWidth, height, false)

            // Scale logo - use 60% of width as requested
            val logoScale = (width * 0.6f) / logo.width
            val logoWidth = (logo.width * logoScale).toInt()
            val logoHeight = (logo.height * logoScale).toInt()
            logo = Bitmap.createScaledBitmap(logo, logoWidth, logoHeight, false)

            // Setup Game Over Buttons
            val btnWidth = 400f
            val btnHeight = 120f
            val centerX = width / 2f
            val centerY = height / 2f
            
            tryAgainRect.set(centerX - btnWidth - 20f, centerY + 100f, centerX - 20f, centerY + 100f + btnHeight)
            menuRect.set(centerX + 20f, centerY + 100f, centerX + btnWidth + 20f, centerY + 100f + btnHeight)

            resetGame()
            isInitialized = true
        }
    }

    private fun resetGame() {
        playerY = groundLevel
        velocity = 0f
        gravityDirection = 1
        jumpCount = 0
        score = 0
        gameSpeed = 18f
        activeObstacles.clear()
        nextSpawnDistance = 0f
        spawnObstacle()
    }

    private fun spawnObstacle() {
        val bitmap = obstacleBitmaps.random()
        val surfaceOffset = (height * 0.15f) * 0.1f
        
        // Randomly choose obstacle type: 0=Ground, 1=Ceiling, 2=GravityZone
        val spawnType = Random.nextInt(10)
        val y: Float
        var isGravity = false
        var isCeiling = false

        when {
            spawnType < 2 -> { // 20% chance Gravity Zone
                isGravity = true
                y = (floorY / 2f)
            }
            spawnType < 6 -> { // 40% chance Ceiling
                isCeiling = true
                y = 0f
            }
            else -> { // 40% chance Ground
                y = (floorY + surfaceOffset) - obstacleSize
            }
        }
        
        activeObstacles.add(Obstacle(width.toFloat(), y, bitmap, isGravity, isCeiling))
        // Reduced distance for more frequent spawning
        nextSpawnDistance = Random.nextFloat() * 400f + 400f
    }

    override fun run() {
        val targetTimeNanos = 1_000_000_000L / 60 
        while (isPlaying) {
            val startTime = System.nanoTime()

            if (!isInitialized) {
                initGameDimensions()
            }

            update()
            renderFrame()

            val timeElapsed = System.nanoTime() - startTime
            val waitTimeNanos = targetTimeNanos - timeElapsed
            
            if (waitTimeNanos > 0) {
                val millis = waitTimeNanos / 1_000_000
                val nanos = (waitTimeNanos % 1_000_000).toInt()
                try { Thread.sleep(millis, nanos) } catch (e: Exception) {}
            }
        }
    }

    private fun renderFrame() {
        if (!holder.surface.isValid) return

        val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            holder.lockHardwareCanvas()
        } else {
            holder.lockCanvas()
        } ?: return

        try {
            // Draw Background
            canvas.drawBitmap(bg, bgX, 0f, bitmapPaint)
            if (bgX + bg.width < width) {
                canvas.drawBitmap(bg, bgX + bg.width, 0f, bitmapPaint)
            }

            if (!isInitialized) return

            // Draw Floor
            canvas.drawBitmap(floor, floorX, floorY, bitmapPaint)
            if (floorX + floor.width < width) {
                canvas.drawBitmap(floor, floorX + floor.width, floorY, bitmapPaint)
            }

            // Draw Player
            canvas.save()
            if (gravityDirection == -1) {
                // Flip player vertically
                canvas.scale(1f, -1f, playerX + playerSize / 2f, playerY + playerSize / 2f)
            }
            val isGrounded = if (gravityDirection == 1) playerY >= groundLevel else playerY <= 0f
            val currentBitmap = if (!isGrounded) jumpImg else (if (frame == 0) run1 else run2)
            canvas.drawBitmap(currentBitmap, playerX, playerY, bitmapPaint)
            canvas.restore()

            // Draw Obstacles
            for (obs in activeObstacles) {
                canvas.save()
                if (obs.isCeiling) {
                    // Flip ceiling obstacles vertically
                    canvas.scale(1f, -1f, obs.x + obstacleSize / 2f, obs.y + obstacleSize / 2f)
                }
                
                if (obs.isGravityZone) {
                    // Draw glow effect for gravity zones
                    gravityZonePaint.setShadowLayer(20f, 0f, 0f, Color.CYAN)
                    canvas.drawRect(obs.x, obs.y, obs.x + obstacleSize, obs.y + obstacleSize, gravityZonePaint)
                    gravityZonePaint.clearShadowLayer()
                }
                canvas.drawBitmap(obs.bitmap, obs.x, obs.y, bitmapPaint)
                canvas.restore()
            }

            drawUI(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun update() {
        if (!isInitialized) return

        // Background scrolling (Always scroll for visual effect)
        bgX -= (gameSpeed / 6f)
        if (bgX <= -bg.width) bgX = 0f

        if (gameState != GameState.PLAYING) return

        // Difficulty scaling
        gameSpeed = 18f + (score / 5f)

        // Physics
        velocity += gravity * gravityDirection
        playerY += velocity
        
        if (gravityDirection == 1) {
            if (playerY >= groundLevel) {
                playerY = groundLevel
                velocity = 0f
                jumpCount = 0
            }
        } else {
            if (playerY <= 0f) {
                playerY = 0f
                velocity = 0f
                jumpCount = 0
            }
        }

        floorX -= gameSpeed
        if (floorX <= -floor.width) floorX = 0f

        var furthestObstacleX = 0f
        var i = 0
        while (i < activeObstacles.size) {
            val obs = activeObstacles[i]
            obs.x -= gameSpeed
            if (obs.x > furthestObstacleX) furthestObstacleX = obs.x

            playerRect.set(playerX + 45, playerY + 40, playerX + playerSize - 45, playerY + playerSize - 10)
            obstacleRect.set(obs.x + 35, obs.y + 35, obs.x + obstacleSize - 35, obs.y + obstacleSize - 10)

            if (RectF.intersects(playerRect, obstacleRect)) {
                if (obs.isGravityZone) {
                    gravityDirection *= -1
                    activeObstacles.removeAt(i)
                    // No score increment for hitting gravity zones, they are utility
                } else {
                    endGame()
                    return
                }
            } else if (obs.x < -obstacleSize) {
                activeObstacles.removeAt(i)
                score++
            } else {
                i++
            }
        }

        if (width - furthestObstacleX >= nextSpawnDistance) {
            spawnObstacle()
        }

        frameTimer += 16
        val targetFrameTime = (150 - (gameSpeed * 2)).toLong().coerceAtLeast(50L)
        if (frameTimer > targetFrameTime) {
            frame = (frame + 1) % 2
            frameTimer = 0
        }
    }

    private fun endGame() {
        gameState = GameState.GAME_OVER
        if (score > highScore) {
            highScore = score
            sharedPreferences.edit().putInt("highScore", highScore).apply()
        }
    }

    private fun drawUI(canvas: Canvas) {
        textPaint.color = Color.BLACK
        textPaint.textSize = 60f
        canvas.drawText("Score: $score", 50f, 100f, textPaint)
        canvas.drawText("Best: $highScore", 50f, 180f, textPaint)

        if (gameState == GameState.START) {
            drawMenu(canvas)
        } else if (gameState == GameState.GAME_OVER) {
            drawGameOver(canvas)
        }
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        
        textPaint.color = Color.WHITE
        textPaint.textSize = 100f
        val msg = "GAME OVER"
        textPaint.getTextBounds(msg, 0, msg.length, textBounds)
        canvas.drawText(msg, width / 2f - textBounds.width() / 2f, height / 2f - 100f, textPaint)

        textPaint.textSize = 50f
        val finalScoreMsg = "Final Score: $score"
        textPaint.getTextBounds(finalScoreMsg, 0, finalScoreMsg.length, textBounds)
        canvas.drawText(finalScoreMsg, width / 2f - textBounds.width() / 2f, height / 2f, textPaint)

        // Draw Buttons
        drawButton(canvas, tryAgainRect, "RETRY")
        drawButton(canvas, menuRect, "MENU")
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String) {

        val gradient = LinearGradient(
            rect.left, rect.top,
            rect.left, rect.bottom,
            Color.parseColor("#6A0DAD"),
            Color.parseColor("#2E003E"),
            Shader.TileMode.CLAMP
        )

        buttonPaint.shader = gradient
        canvas.drawRoundRect(rect, 25f, 25f, buttonPaint)
        buttonPaint.shader = null

        canvas.drawRoundRect(rect, 25f, 25f, buttonStrokePaint)

        // glow text
        textPaint.setShadowLayer(10f, 0f, 0f, Color.MAGENTA)
        textPaint.color = Color.WHITE
        textPaint.textSize = 45f

        textPaint.getTextBounds(text, 0, text.length, textBounds)

        canvas.drawText(
            text,
            rect.centerX() - textBounds.width() / 2f,
            rect.centerY() + textBounds.height() / 2f,
            textPaint
        )
    }

    private fun drawMenu(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        
        // Draw Logo - position at 15% from top
        val logoY = height * 0.15f
        canvas.drawBitmap(logo, width / 2f - logo.width / 2f, logoY, bitmapPaint)

        // Draw "TAP ANYWHERE TO START" hint
        textPaint.color = Color.WHITE
        textPaint.textSize = 25f
        val startText = "Tap anywhere to start"
        textPaint.getTextBounds(startText, 0, startText.length, textBounds)
        canvas.drawText(
            startText, 
            width / 2f - textBounds.width() / 2f,
            logoY + logo.height + 150f, 
            textPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            when (gameState) {
                GameState.START -> {
                    gameState = GameState.PLAYING
                }
                GameState.PLAYING -> {
                    if (jumpCount < 2) {
                        velocity = -35f * gravityDirection
                        jumpCount++
                    }
                }
                GameState.GAME_OVER -> {
                    if (tryAgainRect.contains(event.x, event.y)) {
                        resetGame()
                        gameState = GameState.PLAYING
                    } else if (menuRect.contains(event.x, event.y)) {
                        resetGame()
                        gameState = GameState.START
                    }
                }
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun resume() {
        isPlaying = true
        thread = Thread(this)
        thread!!.start()
    }

    fun pause() {
        isPlaying = false
        try {
            thread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
