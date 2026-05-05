# Random Timer — RuneLite Plugin

A RuneLite plugin that fires a hidden alarm at a random time between two intervals you set. You'll know the range — but never exactly when it will go off. Useful as an awareness check, an anti-AFK prompt, or a general activity reminder while playing Old School RuneScape.

---

## What It Does

When you start the timer, the plugin secretly picks a random moment between your **minimum** and **maximum** time settings. When that moment arrives:

- A **sound plays** — either your own custom `.wav` file or the built-in alarm beep
- A **message appears in your chatbox**
- The timer resets and waits for you to start it again

The sidebar panel shows how long the timer has been running, and a small HUD infobox displays the elapsed time on screen — but the target time is always hidden. That's the point.

---

## Getting Started

1. Enable **Random Timer** from your RuneLite plugin list
2. Click the **clock icon** in the right sidebar to open the plugin panel
3. Set your **Min** and **Max** times using the input fields (in seconds)
4. Click **Test Sound** to preview your alarm audio
5. Click **Start Timer** — the countdown begins silently

Go play. The alarm will fire when it fires.

---

## The Sidebar Panel

Open it by clicking the clock icon in the RuneLite right sidebar. This is where you control the timer and configure your time range.

| Element | Description |
|---|---|
| **Status** | Shows `ACTIVE` (green) or `INACTIVE` (red) |
| **Running** | Elapsed time since the timer started (H:MM:SS) |
| **Min (sec)** | Earliest the alarm can fire, in seconds from start |
| **Max (sec)** | Latest the alarm can fire, in seconds from start |
| **Test Sound** | Previews whichever sound is currently configured |
| **Start Timer** | Begins the countdown with a secretly chosen target |
| **Stop Timer** | Cancels the current countdown without firing the alarm |

> **Note:** Min and Max lock while the timer is active. Stop the timer first to change the range.

**Default range:** Min `120` (2 min) / Max `600` (10 min)

---

## The HUD InfoBox

While the timer is running, a compact infobox appears on your game screen showing elapsed time in green. It disappears automatically when the timer stops or fires.

- The time counts **up** (elapsed), not down — the target is never shown
- You can drag it anywhere on screen by holding `Alt` and dragging in RuneLite
- You can disable it entirely in the plugin settings if you prefer no HUD element

---

## Plugin Settings

Access these by clicking the **wrench icon** next to Random Timer in your plugin list. These are persistent preferences that don't change between timer runs.

| Setting | Default | Description |
|---|---|---|
| **Custom Sound File Path** | *(blank)* | Absolute path to a `.wav` file. Leave blank to use the built-in beep. |
| **Custom Chat Message** | `Random Timer: Your timer has triggered!` | Message posted to your chatbox when the alarm fires. |
| **Show HUD InfoBox** | Enabled | Toggle the in-game elapsed time display on or off. |

> **Min/Max time is set in the sidebar panel**, not here — so you can adjust it quickly without opening settings.

---

## Adding Your Own Sound

You can replace the built-in alarm beep with any `.wav` file on your computer.

### Step 1 — Get a `.wav` file

Only `.wav` format is supported (Java's built-in audio limitation). To convert from another format:

- **Free online:** [cloudconvert.com/mp3-to-wav](https://cloudconvert.com/mp3-to-wav)
- **Free desktop app:** [Audacity](https://www.audacityteam.org/) → File → Export → Export as WAV

### Step 2 — Find the full file path

You need the **absolute path** — the full location from the root of your drive.

| OS | Example |
|---|---|
| Windows | `C:\Users\Matthew\sounds\alarm.wav` |
| Mac | `/Users/matthew/sounds/alarm.wav` |

> **Windows tip:** Hold `Shift` and right-click the file → **"Copy as path"** to grab it instantly.

### Step 3 — Paste it into settings

1. Open RuneLite settings → find **Random Timer**
2. Paste your path into **Custom Sound File Path**
3. Go to the sidebar and click **Test Sound** to confirm it works

To go back to the built-in beep, just clear the path field.

---

## Tips

**Choosing a good time range**
- Anti-AFK / quick check: `120` min / `300` max (2–5 minutes)
- Longer awareness reminder: `600` min / `3600` max (10 min – 1 hour)
- All values are in **seconds**

**The alarm fired while I was AFK — did it reset?**
Yes. Once it fires the timer returns to inactive. Click Start Timer again when you're back.

**The sound played but I couldn't hear it**
Check your system volume. The built-in beep plays at 60% volume. If using a custom `.wav`, make sure the file isn't recorded quietly — you can boost it in Audacity.

**Can I use the plugin without the Jagex launcher?**
For development and testing yes — see the Developer Setup section below. For normal play you'll use it like any other RuneLite plugin.

---

## Troubleshooting

**Sound doesn't play**
- Confirm the file path is correct with no typos
- Confirm the file is `.wav` format (not `.mp3`, `.ogg`, etc.)
- Confirm the file exists at that exact path

**Timer doesn't fire**
- Make sure you're logged into the game — the plugin only checks while `GameState` is `LOGGED_IN`

**InfoBox doesn't appear**
- Check that **Show HUD InfoBox** is enabled in plugin settings
- Make sure the timer is actually active (Status shows ACTIVE in the panel)
