package com.matthewOSRS;

// --- RuneLite & Minecraft-style plugin imports ---
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;

// @Slf4j gives us a `log` object for debug/info logging (like console.log in JS)
@Slf4j
// This annotation registers your plugin with RuneLite
@PluginDescriptor(
		name = "Random Timer",
		description = "Fires a random alarm between two configurable time intervals",
		tags = {"timer", "alarm", "random", "alert"}
)
public class RandomTimerPlugin extends Plugin
{
	// --- Injected dependencies (RuneLite handles wiring these up automatically) ---

	@Inject
	private Client client; // The game client — lets us send chat messages, read game state, etc.

	@Inject
	private RandomTimerConfig config; // Our config panel values (min/max time, sound path, message)

	@Inject
	private InfoBoxManager infoBoxManager; // Manages compact infobox tiles on the HUD

	@Inject
	private ClientToolbar clientToolbar; // The left sidebar toolbar

	// --- Internal state ---

	private final Random random = new Random(); // Used to pick a random fire time

	private boolean timerActive = false;        // Is the timer currently running?
	private long timerStartTime = -1;           // When the timer was started (ms since epoch)
	private long targetFireTime = -1;           // The randomly chosen time to fire (ms since epoch)

	private NavigationButton navButton;         // The sidebar button
	private RandomTimerPanel sidePanel;         // The sidebar panel UI
	private RandomTimerInfoBox infoBox;         // The compact HUD infobox (null when inactive)
	private BufferedImage pluginIcon;           // Cached icon — reused for the infobox each run

	// --- Plugin lifecycle ---

	@Override
	protected void startUp() throws Exception
	{
		// Generate the clock icon once and cache it — used for both the sidebar
		// nav button and the infobox tile so they share the same image
		pluginIcon = generateIcon();

		// Build the sidebar panel and nav button
		sidePanel = new RandomTimerPanel(this);

		navButton = NavigationButton.builder()
				.tooltip("Random Timer")
				.icon(pluginIcon)
				.priority(10) // Lower = higher up in sidebar
				.panel(sidePanel)
				.build();

		clientToolbar.addNavigation(navButton);

		log.info("Random Timer plugin started.");
	}

	@Override
	protected void shutDown() throws Exception
	{
		// Clean up: remove infobox and sidebar when plugin is turned off
		removeInfoBox();
		clientToolbar.removeNavigation(navButton);
		stopTimer();

		log.info("Random Timer plugin stopped.");
	}

	// --- Timer control (called by the sidebar panel buttons) ---

	/**
	 * Starts the random timer using values passed in from the sidebar panel.
	 * The panel validates min < max before calling this, so we trust those values here.
	 *
	 * @param minSeconds Minimum seconds before alarm can fire (from panel spinner)
	 * @param maxSeconds Maximum seconds before alarm fires (from panel spinner)
	 */
	public void startTimer(int minSeconds, int maxSeconds)
	{
		// Pick a random duration between min and max (inclusive)
		int randomDuration = minSeconds + random.nextInt(maxSeconds - minSeconds + 1);

		timerStartTime = System.currentTimeMillis();

		// Calculate the exact future timestamp when the alarm fires
		targetFireTime = timerStartTime + TimeUnit.SECONDS.toMillis(randomDuration);

		timerActive = true;

		// Show the infobox on the HUD (if the user hasn't disabled it in config)
		addInfoBox();

		// Update the sidebar panel to reflect "active" state
		sidePanel.setStatus(true);

		log.info("Random Timer started. Will fire in {} seconds.", randomDuration);
		// NOTE: We intentionally don't tell the user how long — that's the point!
	}

	/**
	 * Stops and resets the timer without firing the alarm.
	 */
	public void stopTimer()
	{
		timerActive = false;
		timerStartTime = -1;
		targetFireTime = -1;

		// Remove the infobox from the HUD — it should only show while active
		removeInfoBox();

		// Update the sidebar
		sidePanel.setStatus(false);

		log.info("Random Timer stopped.");
	}

	// --- InfoBox management ---

	/**
	 * Adds the infobox to the HUD if:
	 *   (a) the user hasn't disabled it in config, and
	 *   (b) one isn't already showing
	 */
	private void addInfoBox()
	{
		// Check the config toggle — respect the user's preference
		if (!config.showInfoBox())
		{
			return;
		}

		// Avoid adding duplicates if startTimer is called while already active
		if (infoBox != null)
		{
			return;
		}

		// Create a new infobox instance using the cached clock icon
		infoBox = new RandomTimerInfoBox(this, pluginIcon, this);
		infoBoxManager.addInfoBox(infoBox);
	}

	/**
	 * Removes the infobox from the HUD and clears the reference.
	 * Safe to call even if no infobox is currently showing.
	 */
	private void removeInfoBox()
	{
		if (infoBox != null)
		{
			infoBoxManager.removeInfoBox(infoBox);
			infoBox = null;
		}
	}

	// --- Game tick listener ---
	// RuneLite fires this event ~every 600ms (one game tick)

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Only check if the timer is active and the player is logged in
		if (!timerActive || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long now = System.currentTimeMillis();

		// Check if we've hit or passed the target fire time
		if (now >= targetFireTime)
		{
			fireAlarm();
		}
	}

	/**
	 * Fires the alarm: plays sound + sends chat message, then stops the timer.
	 */
	private void fireAlarm()
	{
		// --- Play sound ---
		// If the user configured a custom .wav path, use that.
		// Otherwise fall back to the built-in generated alarm beep.
		String soundPath = config.customSoundPath().trim();
		if (!soundPath.isEmpty())
		{
			playSound(soundPath);
		}
		else
		{
			playGeneratedAlarm();
		}

		// --- Send chat message ---
		// The config default is already "Random Timer: Your timer has triggered!"
		// so this fallback only fires if the user explicitly blanked the field.
		String message = config.customChatMessage().trim();
		if (message.isEmpty())
		{
			message = "Random Timer: Your timer has triggered!";
		}

		// Send as a GAME_MESSAGE so it appears in the chatbox
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);

		log.info("Random Timer alarm fired!");

		// Stop and reset the timer after firing
		stopTimer();
	}

	// --- Sound playback ---

	/**
	 * Plays a .wav file from an absolute path on the user's filesystem.
	 * Runs on a background thread so it doesn't freeze the game.
	 *
	 * @param filePath Absolute path to the .wav file (e.g. C:/sounds/alarm.wav)
	 */
	private void playSound(String filePath)
	{
		// Run audio in a separate thread — audio loading can be slow
		new Thread(() ->
		{
			try
			{
				File soundFile = new File(filePath);

				// Security: only allow .wav files and ensure the file actually exists
				if (!soundFile.exists() || !filePath.toLowerCase().endsWith(".wav"))
				{
					log.warn("Sound file not found or not a .wav: {}", filePath);
					return;
				}

				// Java's AudioInputStream reads audio data from the file
				AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
				Clip clip = AudioSystem.getClip();
				clip.open(audioStream);
				clip.start();

				// Wait for the clip to finish before closing it
				clip.addLineListener(e ->
				{
					if (e.getType() == LineEvent.Type.STOP)
					{
						clip.close();
					}
				});
			}
			catch (Exception e)
			{
				log.error("Failed to play sound file: {}", filePath, e);
			}
		}).start();
	}

	/**
	 * Generates and plays a classic alarm clock beep pattern entirely in code.
	 * No external sound file required — uses Java's synthesized audio API.
	 *
	 * How it works: we build raw PCM audio samples (the same format as a .wav file)
	 * by calculating a sine wave for each beep, then feed that directly to Java's
	 * audio Clip. Think of PCM as the raw pixel data of audio — just numbers.
	 */
	private void playGeneratedAlarm()
	{
		new Thread(() ->
		{
			try
			{
				// AudioFormat defines the "shape" of our audio data:
				//   44100 Hz  = CD-quality sample rate (samples per second)
				//   16-bit    = each sample is a 16-bit integer (-32768 to 32767)
				//   1 channel = mono (no stereo needed for an alarm beep)
				//   signed, big-endian = standard byte ordering for Java audio
				float sampleRate   = 44100f;
				int   bitsPerSample = 16;
				int   channels      = 1;
				AudioFormat format  = new AudioFormat(sampleRate, bitsPerSample, channels, true, true);

				// Alarm pattern: 3 short beeps (like a classic alarm clock)
				// Each beep is 200ms on, 150ms off
				int beepCount      = 3;
				double beepHz      = 880.0;  // Frequency in Hz — 880 = A5, a sharp alarm tone
				int beepSamples    = (int)(sampleRate * 0.20); // 200ms worth of samples
				int silenceSamples = (int)(sampleRate * 0.15); // 150ms silence between beeps
				int totalSamples   = beepCount * (beepSamples + silenceSamples);

				// Each sample is 2 bytes (16-bit), so the buffer is twice the sample count
				byte[] buffer = new byte[totalSamples * 2];

				int bufferPos = 0;
				for (int beep = 0; beep < beepCount; beep++)
				{
					// --- Write beep samples (sine wave) ---
					for (int i = 0; i < beepSamples; i++)
					{
						// Sine wave formula: amplitude * sin(2π * frequency * time)
						// We scale down volume slightly (0.6) so it's not ear-splitting
						double angle     = 2.0 * Math.PI * beepHz * i / sampleRate;
						short  sample    = (short)(Short.MAX_VALUE * 0.6 * Math.sin(angle));

						// Split 16-bit short into 2 bytes (big-endian: high byte first)
						buffer[bufferPos++] = (byte)(sample >> 8);
						buffer[bufferPos++] = (byte)(sample & 0xFF);
					}

					// --- Write silence samples (all zeros = silence) ---
					for (int i = 0; i < silenceSamples; i++)
					{
						buffer[bufferPos++] = 0;
						buffer[bufferPos++] = 0;
					}
				}

				// Wrap our raw byte array in an AudioInputStream so Java can play it
				DataLine.Info info = new DataLine.Info(Clip.class, format);
				Clip clip = (Clip) AudioSystem.getLine(info);
				clip.open(format, buffer, 0, buffer.length);
				clip.start();

				clip.addLineListener(e ->
				{
					if (e.getType() == LineEvent.Type.STOP)
					{
						clip.close();
					}
				});
			}
			catch (Exception e)
			{
				log.error("Failed to play generated alarm sound", e);
			}
		}).start();
	}

	// --- Getters used by the Overlay and Panel ---

	/** Returns true if the timer is currently counting down */
	public boolean isTimerActive()
	{
		return timerActive;
	}

	/**
	 * Returns how many milliseconds have passed since the timer was started.
	 * Returns 0 if the timer isn't active.
	 */
	public long getElapsedMillis()
	{
		if (!timerActive || timerStartTime < 0)
		{
			return 0;
		}
		return System.currentTimeMillis() - timerStartTime;
	}

	// --- Icon generation ---

	/**
	 * Draws a simple clock/timer icon at 16x16 pixels entirely in code.
	 * This avoids needing a PNG resource file bundled with the plugin.
	 *
	 * Graphics2D is Java's 2D drawing API — think of it like an HTML <canvas>
	 * where you can draw shapes, text, and colors programmatically.
	 */
	private BufferedImage generateIcon()
	{
		// 16x16 is the standard RuneLite sidebar icon size
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		// Get the drawing context — all draw calls go through this object
		Graphics2D g = image.createGraphics();

		// Smooth out the edges of drawn shapes (anti-aliasing)
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Draw the clock face (filled orange circle)
		g.setColor(new Color(255, 153, 0)); // Orange
		g.fillOval(1, 1, 14, 14);

		// Draw a dark border around it
		g.setColor(new Color(60, 40, 0));
		g.setStroke(new BasicStroke(1.2f));
		g.drawOval(1, 1, 14, 14);

		// Draw clock hands pointing to roughly "12 and 3" (like a timer just started)
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine(8, 8, 8, 3);  // Hour hand pointing up (12)
		g.drawLine(8, 8, 12, 8); // Minute hand pointing right (3)

		// Center dot
		g.setColor(new Color(60, 40, 0));
		g.fillOval(7, 7, 2, 2);

		// Always release Graphics resources when done — prevents memory leaks
		g.dispose();

		return image;
	}

	/**
	 * Plays whichever sound would fire if the alarm triggered right now.
	 * Called by the panel's "Test Sound" button so the user can verify their audio.
	 * Uses the same logic as fireAlarm() — custom .wav if set, generated beep otherwise.
	 */
	public void testSound()
	{
		String soundPath = config.customSoundPath().trim();
		if (!soundPath.isEmpty())
		{
			playSound(soundPath);
		}
		else
		{
			playGeneratedAlarm();
		}
	}

	// Required by RuneLite to wire up the config automatically
	@Provides
	RandomTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RandomTimerConfig.class);
	}
}