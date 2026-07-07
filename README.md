# 🏃 Endless Runner

A 2D endless runner game developed in **Android Studio using Kotlin**. The game features customizable gameplay with multiple difficulty levels, character selection, and a unique gravity-flip mechanic that allows players to switch between running on the ground and the ceiling.

---

## 🎮 Features

- 🏃 Smooth 2D endless runner gameplay
- 🎨 Three playable characters with sprite animations
- ⚙️ Difficulty selection (Easy, Medium, Hard)
- 🔄 Gravity Flip mode (On/Off)
- 🚧 Random obstacle generation
- ⬆️ Triple jump mechanic
- 📈 Dynamic difficulty scaling
- 🏆 Persistent high score using SharedPreferences
- 🌌 Animated scrolling background and floor
- 💥 Collision detection with game over screen
- 🔁 Retry and Main Menu functionality

---

## 📱 Screenshots

```
screenshots/
├── menu.png
├── setup.png
├── gameplay.png
└── gameover.png
```

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **IDE:** Android Studio
- **Graphics:** Canvas API, Bitmap
- **Game Engine:** Custom SurfaceView Game Loop
- **Storage:** SharedPreferences
- **Platform:** Android

---

## 🎯 Gameplay

1. Launch the game.
2. Tap the screen to continue.
3. Select:
   - Difficulty
   - Character
   - Gravity Flip Mode
4. Press **Start Run**.
5. Tap to jump and avoid obstacles.
6. If Gravity Flip is enabled, collect the Gravity Flip icon to switch gravity.
7. Survive as long as possible and beat your high score.

---

## ✨ Unique Features

### Gravity Flip Mechanic
Unlike traditional endless runners, players can enable Gravity Flip mode. Collecting a Gravity Flip icon reverses gravity, allowing the player to run on the ceiling and avoid obstacles from a different perspective.

### Character Selection
Choose between three unique playable characters before starting the game.

### Difficulty Modes
Select Easy, Medium, or Hard, each with different starting speeds and progression rates.

---

## 🧠 Concepts Used

- SurfaceView Game Loop
- Canvas Rendering
- Sprite Animation
- Bitmap Scaling
- Collision Detection using RectF
- Physics-based Jumping
- Random Obstacle Generation
- Android Activity Lifecycle
- SharedPreferences
- Touch Event Handling

---

## 📂 Project Structure

```
app
├── java
│   ├── MainActivity.kt
│   └── GameView.kt
├── res
│   ├── drawable
│   ├── mipmap
│   ├── values
│   └── xml
└── AndroidManifest.xml
```

---

## 🚀 Installation

1. Clone the repository

```bash
git clone https://github.com/SJK-tech/Endless-runner.git
```

2. Open the project in Android Studio.

3. Let Gradle sync.

4. Connect an Android device or start an emulator.

5. Click **Run ▶️**

---

## 📸 Demo

Gameplay

```
demo.gif
```

---

## 🔮 Future Improvements

- Sound effects and background music
- Power-ups and collectibles
- Boss levels
- Online leaderboard
- Achievements
- Additional playable characters
- More obstacle varieties

---

## 👩‍💻 Author

**Shreeya Jagadish Kumar**

GitHub: https://github.com/SJK-tech

---

## ⭐ If you like this project

Give this repository a ⭐ and feel free to fork it!
