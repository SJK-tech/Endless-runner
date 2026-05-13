package com.example.endlessrunner

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.SurfaceView
import android.view.MotionEvent
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), Runnable {

    private enum class GameState {
        START, SETUP, PLAYING, GAME_OVER
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

    private val selectedButtonPaint = Paint().apply {
        color = Color.parseColor("#C084FC") // Lighter purple for selection
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false // Faster for pre-scaled bitmaps
        isDither = false
    }

    private val overlayPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
    }

    private val playerRect = RectF()
    private val obstacleRect = RectF()
    private val tryAgainRect = RectF()
    private val menuRect = RectF()
    private val textBounds = Rect()

    // Setup screen button rects
    private val difficultyEasyRect = RectF()
    private val difficultyMedRect = RectF()
    private val difficultyHardRect = RectF()
    private val gravityToggleRect = RectF()
    private val startGameRect = RectF()
    private val charSelectRects = Array(3) { RectF() }

    private val gravityZonePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    // Bitmaps
    private var run1: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.run1)
    private var run2: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.run2)
    private var jumpImg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.jump)
    private var bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.background)
    private var floor: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.floor)
    private var logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.logo)
    private var flipImg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.flip)
    
    // Character bitmaps storage
    private class CharacterSprites(val run1: Bitmap, val run2: Bitmap, val jump: Bitmap)
    private lateinit var characters: Array<CharacterSprites>

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
    private val gravity = 2.2f // Increased from 1.8f
    private var gravityDirection = 1 // 1 = normal, -1 = upside down
    private var jumpCount = 0
    private var score = 0
    private var highScore = 0
    private var gameSpeed = 15f
    private var isInitialized = false

    // Setup Variables
    private var selectedDifficulty = 1 // 0=Easy, 1=Medium, 2=Hard
    private var selectedCharacter = 0 // 0 to 2
    private var gravityEnabled = true

    private class Obstacle(var x: Float, var y: Float, val bitmap: Bitmap, val isGravityZone: Boolean = false, val isCeiling: Boolean = false)
    private val activeObstacles = mutableListOf<Obstacle>()
    private var nextSpawnDistance = 0f

    private val sharedPreferences = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    init {
        highScore = sharedPreferences.getInt("highScore", 0)
        
        loadAndScaleCharacters()

        obstacleBitmaps = obstacleBitmaps.map {
            Bitmap.createScaledBitmap(it, obstacleSize.toInt(), obstacleSize.toInt(), false)
        }.toTypedArray()

        flipImg = Bitmap.createScaledBitmap(flipImg, obstacleSize.toInt(), obstacleSize.toInt(), false)
    }

    private fun loadAndScaleCharacters() {
        val size = playerSize.toInt()
        
        // Character 1 (Default)
        val char1 = CharacterSprites(
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.run1), size, size, false),
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.run2), size, size, false),
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.jump), size, size, false)
        )

        // Character 2
        val char2 = CharacterSprites(
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.character2_left), size, size, false),
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.character2_right), size, size, false),
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.character2_jump), size, size, false)
        )

        // Character 3
        val char3 = CharacterSprites(
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.character3_left), size, size, false),
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.character3_right), size, size, false),
            Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.character3_jump), size, size, false)
        )

        characters = arrayOf(char1, char2, char3)
        
        // Initialize current sprites
        updatePlayerSprites()
    }

    private fun updatePlayerSprites() {
        val sprites = characters[selectedCharacter]
        run1 = sprites.run1
        run2 = sprites.run2
        jumpImg = sprites.jump
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

            // Scale logo
            val logoScale = (width * 0.6f) / logo.width
            val logoWidth = (logo.width * logoScale).toInt()
            val logoHeight = (logo.height * logoScale).toInt()
            logo = Bitmap.createScaledBitmap(logo, logoWidth, logoHeight, false)

            // Setup UI Rects
            val centerX = width / 2f
            val centerY = height / 2f

            // Game Over Buttons
            val btnW = 400f
            val btnH = 120f
            tryAgainRect.set(centerX - btnW - 20f, centerY + 100f, centerX - 20f, centerY + 100f + btnH)
            menuRect.set(centerX + 20f, centerY + 100f, centerX + btnW + 20f, centerY + 100f + btnH)

            // Setup Screen Buttons
            val setupBtnW = 280f
            val setupBtnH = 100f
            
            // Difficulty row
            val diffY = centerY - 150f
            difficultyEasyRect.set(centerX - setupBtnW * 1.6f, diffY, centerX - setupBtnW * 0.6f, diffY + setupBtnH)
            difficultyMedRect.set(centerX - setupBtnW * 0.5f, diffY, centerX + setupBtnW * 0.5f, diffY + setupBtnH)
            difficultyHardRect.set(centerX + setupBtnW * 0.6f, diffY, centerX + setupBtnW * 1.6f, diffY + setupBtnH)

            // Character row
            val charY = centerY + 50f
            val charIconSize = 140f
            for (i in 0 until 3) {
                val xOffset = (i - 1.0f) * (charIconSize + 60f)
                charSelectRects[i].set(centerX + xOffset - charIconSize/2, charY, centerX + xOffset + charIconSize/2, charY + charIconSize)
            }

            // Gravity & Start
            gravityToggleRect.set(centerX - 200f, centerY + 280f, centerX + 200f, centerY + 280f + 100f)
            startGameRect.set(centerX - 250f, height - 180f, centerX + 250f, height - 180f + 120f)

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
        
        // Apply selected difficulty (Starting speeds reduced)
        gameSpeed = when(selectedDifficulty) {
            0 -> 10f // Easy (Slower start)
            1 -> 15f // Medium
            else -> 20f // Hard
        }
        
        updatePlayerSprites()
        activeObstacles.clear()
        nextSpawnDistance = 0f
        spawnObstacle()
    }

    private fun spawnObstacle() {
        val surfaceOffset = (height * 0.15f) * 0.1f
        
        val spawnType = Random.nextInt(10)
        val y: Float
        val bitmap: Bitmap
        var isGravity = false
        var isCeiling = false

        when {
            spawnType < 1 && gravityEnabled -> { 
                isGravity = true
                bitmap = flipImg
                y = (floorY / 2f)
            }
            spawnType < 5 -> { 
                isCeiling = true
                bitmap = obstacleBitmaps.random()
                y = 0f
            }
            else -> { 
                bitmap = obstacleBitmaps.random()
                y = (floorY + surfaceOffset) - obstacleSize
            }
        }
        
        activeObstacles.add(Obstacle(width.toFloat(), y, bitmap, isGravity, isCeiling))
        // Increased distance between obstacles to reduce spawn rate
        nextSpawnDistance = Random.nextFloat() * 600f + 800f
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

            if (gameState == GameState.PLAYING) {
                // Draw Player
                canvas.save()
                if (gravityDirection == -1) {
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
                        canvas.scale(1f, -1f, obs.x + obstacleSize / 2f, obs.y + obstacleSize / 2f)
                    }
                    
                    if (obs.isGravityZone && gravityEnabled) {
                        gravityZonePaint.setShadowLayer(20f, 0f, 0f, Color.CYAN)
                        canvas.drawRect(obs.x, obs.y, obs.x + obstacleSize, obs.y + obstacleSize, gravityZonePaint)
                        gravityZonePaint.clearShadowLayer()
                    }
                    canvas.drawBitmap(obs.bitmap, obs.x, obs.y, bitmapPaint)
                    canvas.restore()
                }
            }

            drawUI(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun update() {
        if (!isInitialized) return

        // Background scrolling
        bgX -= (gameSpeed / 6f)
        if (bgX <= -bg.width) bgX = 0f

        if (gameState != GameState.PLAYING) return

        // Difficulty scaling - Much more gradual speed increase
        val speedIncrement = when(selectedDifficulty) {
            0 -> 0.0005f // Easy: Very slow growth
            1 -> 0.0015f // Medium: Moderate growth
            else -> 0.003f // Hard: Faster growth
        }
        gameSpeed += speedIncrement

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
                if (obs.isGravityZone && gravityEnabled) {
                    gravityDirection *= -1
                    activeObstacles.removeAt(i)
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
        if (gameState == GameState.PLAYING || gameState == GameState.GAME_OVER) {
            textPaint.color = Color.BLACK
            textPaint.textSize = 60f
            canvas.drawText("Score: $score", 50f, 100f, textPaint)
            canvas.drawText("Best: $highScore", 50f, 180f, textPaint)
        }

        when (gameState) {
            GameState.START -> drawMenu(canvas)
            GameState.SETUP -> drawSetup(canvas)
            GameState.GAME_OVER -> drawGameOver(canvas)
            else -> {}
        }
    }

    private fun drawSetup(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        
        textPaint.color = Color.WHITE
        textPaint.textSize = 80f
        val title = "SETUP YOUR RUN"
        textPaint.getTextBounds(title, 0, title.length, textBounds)
        canvas.drawText(title, width/2f - textBounds.width()/2f, height * 0.15f, textPaint)

        // Difficulty Label
        textPaint.textSize = 45f
        canvas.drawText("DIFFICULTY", width/2f - 120f, difficultyMedRect.top - 40f, textPaint)
        
        drawSetupButton(canvas, difficultyEasyRect, "EASY", selectedDifficulty == 0)
        drawSetupButton(canvas, difficultyMedRect, "MED", selectedDifficulty == 1)
        drawSetupButton(canvas, difficultyHardRect, "HARD", selectedDifficulty == 2)

        // Character Label
        canvas.drawText("CHARACTER", width/2f - 120f, charSelectRects[0].top - 40f, textPaint)
        for (i in 0 until 3) {
            val rect = charSelectRects[i]
            val isSelected = selectedCharacter == i
            canvas.drawRoundRect(rect, 15f, 15f, if(isSelected) selectedButtonPaint else buttonPaint)
            canvas.drawRoundRect(rect, 15f, 15f, buttonStrokePaint)
            
            // Draw character preview
            val sprite = characters[i].run1
            canvas.drawBitmap(sprite, rect.centerX() - sprite.width/2f, rect.centerY() - sprite.height/2f, bitmapPaint)
        }

        // Gravity Toggle
        val gravText = if(gravityEnabled) "GRAVITY: ON" else "GRAVITY: OFF"
        drawSetupButton(canvas, gravityToggleRect, gravText, gravityEnabled)

        // Start Game Button
        drawButton(canvas, startGameRect, "START RUN!")
    }

    private fun drawSetupButton(canvas: Canvas, rect: RectF, text: String, isSelected: Boolean) {
        canvas.drawRoundRect(rect, 20f, 20f, if(isSelected) selectedButtonPaint else buttonPaint)
        canvas.drawRoundRect(rect, 20f, 20f, buttonStrokePaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 40f
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, rect.centerX() - textBounds.width()/2f, rect.centerY() + textBounds.height()/2f, textPaint)
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

        drawButton(canvas, tryAgainRect, "RETRY")
        drawButton(canvas, menuRect, "MENU")
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String) {
        val gradient = LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
            Color.parseColor("#6A0DAD"), Color.parseColor("#2E003E"), Shader.TileMode.CLAMP)
        buttonPaint.shader = gradient
        canvas.drawRoundRect(rect, 25f, 25f, buttonPaint)
        buttonPaint.shader = null
        canvas.drawRoundRect(rect, 25f, 25f, buttonStrokePaint)

        textPaint.setShadowLayer(10f, 0f, 0f, Color.MAGENTA)
        textPaint.color = Color.WHITE
        textPaint.textSize = 45f
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, rect.centerX() - textBounds.width() / 2f, rect.centerY() + textBounds.height() / 2f, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawMenu(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val logoY = height * 0.15f
        canvas.drawBitmap(logo, width / 2f - logo.width / 2f, logoY, bitmapPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 25f
        val startText = "Tap anywhere to start"
        textPaint.getTextBounds(startText, 0, startText.length, textBounds)
        canvas.drawText(startText, width / 2f - textBounds.width() / 2f, logoY + logo.height + 150f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            when (gameState) {
                GameState.START -> gameState = GameState.SETUP
                GameState.SETUP -> {
                    if (difficultyEasyRect.contains(x, y)) selectedDifficulty = 0
                    else if (difficultyMedRect.contains(x, y)) selectedDifficulty = 1
                    else if (difficultyHardRect.contains(x, y)) selectedDifficulty = 2
                    else if (gravityToggleRect.contains(x, y)) gravityEnabled = !gravityEnabled
                    else if (startGameRect.contains(x, y)) {
                        resetGame()
                        gameState = GameState.PLAYING
                    } else {
                        for (i in 0 until 3) {
                            if (charSelectRects[i].contains(x, y)) {
                                selectedCharacter = i
                                break
                            }
                        }
                    }
                }
                GameState.PLAYING -> {
                    if (jumpCount < 3) {
                        velocity = -35f * gravityDirection
                        jumpCount++
                    }
                }
                GameState.GAME_OVER -> {
                    if (tryAgainRect.contains(x, y)) {
                        resetGame()
                        gameState = GameState.PLAYING
                    } else if (menuRect.contains(x, y)) {
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
