package com.matthewOSRS;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * RandomTimerConfig.java
 *
 * Defines settings shown in the RuneLite plugin config panel (wrench icon).
 * Min/Max time is intentionally excluded here — those are set in the sidebar panel.
 * Only persistent preferences that aren't part of the timer workflow live here.
 */
@ConfigGroup("randomtimer")
public interface RandomTimerConfig extends Config
{

    // -------------------------------------------------------------------------
    // Custom Sound Path
    // -------------------------------------------------------------------------

    @ConfigItem(
            keyName = "customSoundPath",
            name = "Custom Sound File Path",
            description = "Absolute path to a .wav file to play when the alarm fires. Leave blank to use the built-in beep. Example: C:/sounds/alarm.wav",
            position = 1
    )
    default String customSoundPath()
    {
        return ""; // Default: no sound
    }

    // -------------------------------------------------------------------------
    // Custom Chat Message
    // -------------------------------------------------------------------------

    @ConfigItem(
            keyName = "customChatMessage",
            name = "Custom Chat Message",
            description = "The message sent to your chatbox when the alarm fires.",
            position = 2
    )
    default String customChatMessage()
    {
        return "Random Timer: Your timer has triggered!"; // Default message
    }

    // -------------------------------------------------------------------------
    // InfoBox Toggle
    // -------------------------------------------------------------------------

    @ConfigItem(
            keyName = "showInfoBox",
            name = "Show HUD InfoBox",
            description = "Show a compact infobox on the game screen displaying elapsed time while the timer is active. Disable to hide it entirely.",
            position = 3
    )
    default boolean showInfoBox()
    {
        return true; // Default: infobox is visible
    }
}