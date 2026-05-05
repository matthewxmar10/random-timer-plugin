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

import net.runelite.client.audio.AudioPlayer;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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
	private AudioPlayer audioPlayer; // RuneLite's approved audio playback API

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
	 * Plays a .wav file from an absolute path using RuneLite's AudioPlayer.
	 *
	 * @param filePath Absolute path to the .wav file (e.g. C:/sounds/alarm.wav)
	 */
	private void playSound(String filePath)
	{
		File soundFile = new File(filePath);

		// Security: verify the file exists and is a .wav before attempting playback
		if (!soundFile.exists() || !filePath.toLowerCase().endsWith(".wav"))
		{
			log.warn("Sound file not found or not a .wav: {}", filePath);
			return;
		}

		try
		{
			// AudioPlayer is RuneLite's approved audio API.
			// It handles threading and resource cleanup internally —
			// no need to manage Clips, Lines, or AudioStreams manually.
			audioPlayer.play(soundFile, 1.0f);
		}
		catch (Exception e)
		{
			log.error("Failed to play sound file: {}", filePath, e);
		}
	}

	/**
	 * Generates a classic 3-beep alarm tone and plays it via RuneLite's AudioPlayer.
	 *
	 * Since AudioPlayer only accepts File inputs, we:
	 *   1. Build raw PCM audio data as a byte array (sine wave math)
	 *   2. Wrap it in a valid .wav file structure
	 *   3. Write it to a temp file
	 *   4. Play that temp file via AudioPlayer
	 *   5. Delete the temp file after playback
	 *
	 * PCM (Pulse Code Modulation) is the raw sample format inside a .wav file —
	 * think of each sample as one snapshot of the audio waveform at a point in time.
	 */
	private void playGeneratedAlarm()
	{
		new Thread(() ->
		{
			Path tempFile = null;
			try
			{
				// --- Audio parameters ---
				int sampleRate     = 44100; // CD-quality: 44100 samples per second
				int bitsPerSample  = 16;    // Each sample is a 16-bit signed integer
				int channels       = 1;     // Mono

				// Alarm pattern: 3 beeps at 880 Hz (A5 — sharp, attention-grabbing)
				double beepHz      = 880.0;
				int beepSamples    = (int)(sampleRate * 0.20); // 200ms on
				int silenceSamples = (int)(sampleRate * 0.15); // 150ms off
				int beepCount      = 3;
				int totalSamples   = beepCount * (beepSamples + silenceSamples);

				// PCM data buffer — 2 bytes per sample (16-bit)
				byte[] pcm = new byte[totalSamples * 2];
				int pos = 0;

				for (int beep = 0; beep < beepCount; beep++)
				{
					// Sine wave samples for the beep tone
					for (int i = 0; i < beepSamples; i++)
					{
						double angle  = 2.0 * Math.PI * beepHz * i / sampleRate;
						short  sample = (short)(Short.MAX_VALUE * 0.6 * Math.sin(angle));
						// Big-endian: high byte first
						pcm[pos++] = (byte)(sample >> 8);
						pcm[pos++] = (byte)(sample & 0xFF);
					}
					// Silence between beeps (zero samples)
					for (int i = 0; i < silenceSamples; i++)
					{
						pcm[pos++] = 0;
						pcm[pos++] = 0;
					}
				}

				// --- Build a valid .wav file in memory ---
				// A .wav file = a RIFF header + a fmt chunk + a data chunk + the PCM bytes.
				// We write this manually so we don't need javax.sound to create it.
				int dataSize   = pcm.length;
				int byteRate   = sampleRate * channels * (bitsPerSample / 8);
				int blockAlign = channels * (bitsPerSample / 8);

				ByteArrayInputStream wavStream = new ByteArrayInputStream(buildWavBytes(
						sampleRate, channels, bitsPerSample, byteRate, blockAlign, pcm
				));

				// Write to a temporary file — AudioPlayer needs a File, not a stream
				tempFile = Files.createTempFile("randomtimer_beep", ".wav");
				Files.write(tempFile, wavStream.readAllBytes());

				// Play via RuneLite's approved AudioPlayer API
				audioPlayer.play(tempFile.toFile(), 1.0f);

				// Give the audio time to finish before deleting the temp file
				Thread.sleep(2500);
			}
			catch (Exception e)
			{
				log.error("Failed to play generated alarm", e);
			}
			finally
			{
				// Always clean up the temp file — even if playback failed
				if (tempFile != null)
				{
					try { Files.deleteIfExists(tempFile); }
					catch (Exception ignored) {}
				}
			}
		}).start();
	}

	/**
	 * Builds a valid WAV file byte array from raw PCM data.
	 * A WAV file is a RIFF container with a fixed header structure.
	 * We write all multi-byte integers in little-endian order (least significant byte first),
	 * which is what the WAV spec requires.
	 *
	 * @return Complete .wav file as a byte array, ready to write to disk
	 */
	private byte[] buildWavBytes(int sampleRate, int channels, int bitsPerSample,
	                             int byteRate, int blockAlign, byte[] pcm)
	{
		int dataSize   = pcm.length;
		int chunkSize  = 36 + dataSize; // Total file size minus the 8-byte RIFF header

		byte[] wav = new byte[44 + dataSize]; // 44-byte header + PCM data
		int i = 0;

		// RIFF chunk descriptor
		wav[i++] = 'R'; wav[i++] = 'I'; wav[i++] = 'F'; wav[i++] = 'F';
		wav[i++] = (byte)(chunkSize);        wav[i++] = (byte)(chunkSize >> 8);
		wav[i++] = (byte)(chunkSize >> 16);  wav[i++] = (byte)(chunkSize >> 24);
		wav[i++] = 'W'; wav[i++] = 'A'; wav[i++] = 'V'; wav[i++] = 'E';

		// fmt sub-chunk
		wav[i++] = 'f'; wav[i++] = 'm'; wav[i++] = 't'; wav[i++] = ' ';
		wav[i++] = 16; wav[i++] = 0; wav[i++] = 0; wav[i++] = 0; // Sub-chunk size (16 for PCM)
		wav[i++] = 1;  wav[i++] = 0;                              // Audio format (1 = PCM)
		wav[i++] = (byte)(channels);        wav[i++] = 0;
		wav[i++] = (byte)(sampleRate);      wav[i++] = (byte)(sampleRate >> 8);
		wav[i++] = (byte)(sampleRate >> 16); wav[i++] = (byte)(sampleRate >> 24);
		wav[i++] = (byte)(byteRate);        wav[i++] = (byte)(byteRate >> 8);
		wav[i++] = (byte)(byteRate >> 16);  wav[i++] = (byte)(byteRate >> 24);
		wav[i++] = (byte)(blockAlign);      wav[i++] = 0;
		wav[i++] = (byte)(bitsPerSample);   wav[i++] = 0;

		// data sub-chunk
		wav[i++] = 'd'; wav[i++] = 'a'; wav[i++] = 't'; wav[i++] = 'a';
		wav[i++] = (byte)(dataSize);        wav[i++] = (byte)(dataSize >> 8);
		wav[i++] = (byte)(dataSize >> 16);  wav[i++] = (byte)(dataSize >> 24);

		// Copy PCM data into the wav array after the header
		System.arraycopy(pcm, 0, wav, 44, dataSize);

		return wav;
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