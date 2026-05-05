package com.matthewOSRS;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

/**
 * RandomTimerInfoBox.java
 *
 * A compact HUD tile using RuneLite's InfoBox system — the same style as
 * slayer task counters, death indicators, and other small overlays.
 *
 * InfoBox REQUIRES two things passed to super():
 *   1. A BufferedImage — the icon drawn inside the tile
 *   2. The owning Plugin instance — RuneLite uses this to group/manage boxes
 *
 * We override getText() and getTextColor() which RuneLite calls every frame.
 */
public class RandomTimerInfoBox extends InfoBox
{
    // Reference to the main plugin so we can read elapsed time each frame
    private final RandomTimerPlugin plugin;

    /**
     * @param plugin The main plugin instance — provides isTimerActive() and getElapsedMillis()
     * @param icon   The image shown inside the infobox tile (the generated clock icon)
     * @param owner  The Plugin that owns this box — required by InfoBox's parent constructor
     */
    public RandomTimerInfoBox(RandomTimerPlugin plugin, BufferedImage icon, Plugin owner)
    {
        // InfoBox's constructor signature: InfoBox(BufferedImage image, Plugin plugin)
        // "owner" here is the RandomTimerPlugin itself, passed in as the Plugin type.
        // This is how RuneLite knows which plugin to associate the box with.
        super(icon, owner);

        this.plugin = plugin;

        // Tooltip shown when the user hovers over the infobox on screen
        setTooltip("Random Timer — elapsed time (target is hidden)");
    }

    /**
     * Called every frame by RuneLite to get the text drawn on the tile.
     * Returning null hides the text — but we also remove the box entirely
     * via InfoBoxManager when the timer stops, so this is just a safety net.
     */
    @Override
    public String getText()
    {
        if (!plugin.isTimerActive())
        {
            return null;
        }

        // Format elapsed time compactly — infobox tiles are small
        // Show M:SS normally, switch to H:MM:SS only if over one hour
        long millis  = plugin.getElapsedMillis();
        long hours   = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0)
        {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        else
        {
            // e.g. "4:32" — short and clean for the tile format
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    /**
     * Called every frame to set the text color on the tile.
     * Green = actively counting up.
     */
    @Override
    public Color getTextColor()
    {
        return Color.GREEN;
    }
}