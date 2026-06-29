## Project Overview:Last-Minute Lifesaver

### 1. Problem Statement Selected
**Problem Statement 1 - The Last-Minute Life Saver**
Students, professionals, and entrepreneurs frequently miss deadlines, assignments, meetings, bill payments, interviews, and important commitments. Existing productivity tools often rely on passive reminders that are easy to ignore and do little to help users actually complete their tasks. 
Traditional productivity tools and "to-do lists" exacerbate this issue:
Static Checklists: They present daunting, monolithic tasks (e.g., "Write Research Paper") without showing how to begin, which increases cognitive friction.
Tone-Deaf Scheduling: They set rigid calendar reminders that ignore the user’s fluctuating energy levels, leading to guilt and further avoidance.
Passive Alarm Systems: Standard notifications are easily swiped away, offering no actual support or intervention when procrastination occurs.


---

## 2. Solution Overview

### Lifesaver: The AI-Powered Procrastination Decelerator
**Lifesaver** is an offline-first, Material 3 Android application designed to bridge the gap between psychological intention and actionable momentum. Rather than acting as a passive record of things you haven’t done, Lifesaver is an active partner that helps users dismantle cognitive barriers, manage their executive function, and gamify their path to focus.

Lifesaver's design is centered around **momentum**. It acknowledges that the hardest part of any task is starting. By using server-side Gemini AI models, dynamic scheduling based on real-time energy, and a multi-tiered escalating alert system, Lifesaver gently coaxes users out of procrastination states and guides them through small, bite-sized, low-friction milestones.

---

## 3. Key Features

### 1. AI Task Decelerator & Micro-Steps
- **Unstructured Input Processing:** Users can dictate or type unstructured, chaotic thoughts (e.g., "I need to clean my room but it's a huge mess and I don't know where to start").
- **Gemini Micro-Step Decomposition:** Lifesaver sends this input to Gemini 3.5 Flash, which parses the emotional clutter and returns a highly organized list of ultra-small, sequential **Micro-Steps** (each taking under 5–10 minutes) with metadata. This lowers the psychological barrier to starting to near zero.

### 2. Live Energy-Based AI Scheduler
- **Dynamic Energy States:** The user records their current energy state ("HIGH", "MEDIUM", "LOW").
- **Smart Scheduling:** Lifesaver’s local scheduler matches tasks to the user's current cognitive battery, scheduling demanding brainstorming sessions when energy is high, and reserving low-effort sorting/cleaning tasks for low energy states.

### 3. Real-Time Decision Engine (Next Optimal Action)
- **One-Click Execution:** Instead of forcing the user to choose from a list (which triggers choice paralysis), the user clicks a single prominent "Action" button.
- **Dynamic Prioritization:** The Decision Engine instantly computes and presents the single most critical, energy-appropriate micro-step they should perform *right now*.

### 4. Deadline Rescue Mode
- **Task Emergency Intervention:** For tasks that have been repeatedly procrastinated and are nearing their deadlines, the user can trigger "Rescue Mode".
- **AI Rescue Strategy:** Gemini analyzes the remaining time and the task goal to construct a high-impact, time-boxed "Emergency Rescue Plan" (e.g., "Focus only on Section A for 15 mins, skip formatting, submit raw draft") to secure a passing submission or baseline completion.

### 5. Focus Timer & Escalating Reminders
- **Active Focus Loop:** Built-in Material 3 Pomodoro / Focus timers help maintain focus during task execution.
- **Escalation Protocol:** If the user gets distracted or fails to check off steps, the app triggers escalating reminders—progressing from standard background notifications to continuous vibrations, custom alarm states, and eventually a full-screen "Procrastination Intervention" overlay.

### 6. Bidirectional Google Calendar Sync
- **Commitment Imports:** Imports events directly from Google Calendar as active tasks within Lifesaver.
- **AI Task Mapping:** Automatically estimates duration, priority scores, and generates sub-steps for imported calendar events.

---

## 4. Technologies Used

- **Kotlin:** The modern, expressive, type-safe language used for all Android development.
- **Jetpack Compose:** Declarative UI toolkit utilizing the **Material Design 3 (M3)** design system, supporting smooth transition animations, dark slate colorways, and responsive adaptive layouts.
- **Room Database:** A robust SQLite abstraction layer supporting offline-first, transactional persistence for tasks, user profiles, schedule blocks, and reminder configurations.
- **Coroutines & Flow:** Managed structured concurrency for asynchronous database queries and high-frequency real-time state updates.
- **Retrofit & OkHttp:** Clean network client architecture for fast, reliable, rate-limit-aware REST communication.
- **Jetpack Navigation Compose:** Type-safe, serialization-backed navigation routing and backstack management.

---

## 5. Google Technologies Utilized

### 1. Gemini 3.5 Flash (via Gemini API)
- Leverages Google's state-of-the-art multimodal model for low-latency, high-accuracy unstructured text processing, semantic task decomposition, and emergency rescue plan synthesis.

### 2. Google Workspace & Drive Integration
- Uses **Google Docs API** and **Google Drive API** (via OAuth 2.0 scopes) to let users export their task archives, project journals, and focus analytics directly into collaborative Google Docs.

### 3. Google Calendar API (CalendarContract)
- Leverages the Android system provider to bidirectionally sync calendar events, ensuring user commitments are beautifully synchronized between Google Workspace and the local scheduler.

### 4. Firebase (App Distribution & Analytics)
- Utilizes Google's Firebase platform for rapid, secure, and cost-free beta distribution and app distribution hosting, making test builds accessible instantly via shareable public invite links.
