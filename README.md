# Mobile Fight Game 🎮

Welcome to **Mobile Fight Game**! This is a fast-paced, 2D arcade-style fighting game built natively for Android using Kotlin. It features custom physics, complex state-machine based combat, and an intelligent AI opponent designed to challenge your skills.

## 🚀 Game Features

- **Custom Physics Engine**: Built completely from scratch without external physics libraries. Fighters have distinct velocities, gravity, jump curves, and boundary collisions, giving the game a classic arcade feel.
- **Dynamic Combat System**:
  - **Attack Types**: Light Attacks, Heavy Attacks, and multi-hit Combo Attacks.
  - **Hitboxes & Hurtboxes**: Precise collision detection mapping to the characters' exact visual animations.
  - **Defensive Mechanics**: Rolling (dashing), Blocking (which mitigates damage and prevents hit-stun), and Crouch-attacks.
- **Intelligent AI Opponent (v4)**:
  - **Situational Awareness**: The AI dynamically chooses its strategy based on distance and its current HP. High HP leads to aggressive play, while low HP triggers defensive and counter-attack behaviors.
  - **Hit-stun Exploitation**: If you miss an attack and fall into hit-stun, the AI instantly capitalizes on the free-hit window.
  - **Corner Detection**: If you are trapped near the edge of the screen, the AI will dash in and unleash devastating heavy or combo attacks.
  - **Unpredictability**: Includes randomized jump-in attacks, combo continuations, and defensive rolls when pressured.
- **Smooth Animations**: A robust `SpriteLoader` manages complex sprite sheet parsing, seamlessly connecting frame-by-frame animations for every state (Idle, Run, Attack, Hit Stun, Death, etc.).

## 🏗️ Technical Architecture & Code Highlights

The codebase is structured in Kotlin and leverages standard Android Canvas rendering for high-performance 2D drawing. 

### Core Components

* **`Fighter.kt`**: The heart of the player and enemy entities. It manages the state machine (`FighterState`), exact spatial coordinates (`x`, `y`), and dynamic hitboxes. It includes:
  - **Animation State Sync**: Attack hitboxes are only active during specific frames of the attack animation (e.g., frames 30% to 70% of a Heavy Attack duration).
  - **Collision Resolution**: Stops fighters from clipping through each other, ensuring that attacking fighters do not slide the defending fighter backwards unfairly.
* **`AIController.kt`**: A completely custom, logic-driven AI brain. Instead of just randomly mashing buttons, the AI calculates distance (`dist()`), checks player states (e.g., `isAttacking`), tracks cooldowns, and reacts dynamically to swings.
* **`CombatSystem.kt` & `GameState.kt`**: These files manage the overarching flow of the match, checking round timers, resolving who hit whom (by mathematically intersecting `hitbox` vs `hurtbox` `RectF` objects), and determining the winner.
* **`GameView.kt`**: The main rendering surface. Runs a dedicated background `GameThread` to ensure smooth 60FPS updates, independent of the Android UI thread.

## 🛠️ Getting Started

1. Clone this repository:
   ```bash
   git clone https://github.com/naveenkumarr24cs-a11y/Mobile-Fight-Game-.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync the dependencies.
4. Hit **Run** (`Shift + F10`) to deploy to your Android Emulator or physical Android device.

*Note: Large binary assets (like `app-release.apk` and `gradle.zip`) are intentionally omitted from this repository via `.gitignore` to keep the repository lightweight and within GitHub's file size limits.*

## 🎨 Assets and Audio

- Includes distinct character sprite sheets for `Character Colour1` and `Character Colour2`.
- High-quality parallax backgrounds and stage environments.
- Immersive background music (`music.ogg`) handled via Android's `MediaPlayer`.

---
*Created and maintained by Naveen.*
