package com.profittracker;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;

/**
 * The ProfitTrackerOverlay class is used to display profit values for the user
 */
public class ProfitTrackerOverlay extends Overlay {
    private long profitValue;
    private long startTimeMillies;
    private long activeTicks;
    private long lastTickMillies;
    private boolean inProfitTrackSession;
    private boolean hasBankData;
    private String lastTimeDisplay;
    private long lastProfitValue;
    private int lastWidth;

    private final ProfitTrackerConfig ptConfig;
    private final ProfitTrackerPlugin ptPlugin;
    private final PanelComponent panelComponent = new PanelComponent();

    private static final String RESET_MENU_OPTION = "Reset";
    private static final int MILLISECONDS_PER_TICK = 600;

    public static String FormatIntegerWithCommas(long value) {
        DecimalFormat df = new DecimalFormat("###,###,###");
        return df.format(value);
    }
    @Inject
    private TooltipManager tooltipManager;
    @Inject
    private Client client;
    @Inject
    private ProfitTrackerOverlay(ProfitTrackerConfig config, ProfitTrackerPlugin trackerPlugin)
    {
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        profitValue = 0L;
        ptConfig = config;
        startTimeMillies = 0;
        activeTicks = 0;
        lastTickMillies = 0;
        inProfitTrackSession = false;
        hasBankData = false;
        ptPlugin = trackerPlugin;
        this.addMenuEntry(MenuAction.RUNELITE_OVERLAY, RESET_MENU_OPTION, "Profit Tracker",menuEntry ->
                {
                    ptPlugin.resetSession();
                    profitValue = 0;
                });
    }

    /**
     * Render the item value overlay.
     * @param graphics the 2D graphics
     * @return the value of {@link PanelComponent#render(Graphics2D)} from this panel implementation.
     */
    @Override
    public Dimension render(Graphics2D graphics) {
        String titleText = "Profit Tracker:";
        long millisecondsElapsed;
        long profitRateValue;
        String timeText;

        if (startTimeMillies > 0)
        {
            if (ptConfig.onlineOnlyRate()){
                millisecondsElapsed = (long)(Math.max(0, activeTicks - 1)  * MILLISECONDS_PER_TICK);
                //Add duration since last tick to ensure timer pacing isn't uneven
                if (lastTickMillies != 0 && inProfitTrackSession){
                    millisecondsElapsed += System.currentTimeMillis() - lastTickMillies;
                }
            } else {
                millisecondsElapsed = (System.currentTimeMillis() - startTimeMillies);
            }
        }
        else
        {
            // there was never any session
            millisecondsElapsed = 0;
        }

        timeText = formatTimeIntervalFromMs(millisecondsElapsed, false);
        // Rate limit profit update to avoid extremely high profit being difficult to read
        // Also reduces visual noise
        if (! timeText.equals(lastTimeDisplay) || lastProfitValue == 0) {
            profitRateValue = calculateProfitHourly(millisecondsElapsed, profitValue);
            lastProfitValue = profitRateValue;
        } else {
            profitRateValue = lastProfitValue;
        }
        lastTimeDisplay = timeText;

        // Not sure how this can occur, but it was recommended to do so
        panelComponent.getChildren().clear();

        // Build overlay title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text(titleText)
                .color(hasBankData ? Color.GREEN : Color.YELLOW)
                .build());

        if (!inProfitTrackSession)
        {
            // not in session
            // notify user to reset plugin in order to start
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Reset plugin to start")
                    .color(Color.RED)
                    .build());

        }

        // Show tooltip warning on mouse hover if user hasn't opened bank yet
        Point mousePoint = new Point(client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY());
        if(this.getBounds().contains(mousePoint) && ! hasBankData)
        {
            String tooltipString =
                    "Open bank first to ensure accurate tracking.</br>" +
                    "Otherwise, GE offer interaction or emptying containers from deposit boxes may be incorrect.";

            tooltipManager.add(new Tooltip(tooltipString));
        }

        String formattedProfit = String.format("%,d",profitValue);
        String formattedRate = String.format("%,d",profitRateValue) + "K/H";
        int titleWidth = graphics.getFontMetrics().stringWidth(titleText) + 40;
        int profitWidth = graphics.getFontMetrics().stringWidth("Profit:    " + formattedProfit);
        int rateWidth = graphics.getFontMetrics().stringWidth("Rate:    " + formattedRate);
        // Only allow width to grow, to avoid jitters at high values
        lastWidth = Collections.max(Arrays.asList(lastWidth, titleWidth, profitWidth, rateWidth));

        // Set the size of the overlay (width)
        panelComponent.setPreferredSize(new Dimension(
                lastWidth,
                0));

        // elapsed time
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time:")
                .right(timeText)
                .build());

        // Profit
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit:")
                .right(formattedProfit)
                .build());

        // Profit Rate
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Rate:")
                .right(formattedRate)
                .build());

        return panelComponent.render(graphics);
    }

    /**
     * Updates profit value display
     * @param newValue the value to update the profitValue's {{@link #panelComponent}} with.
     */
    public void updateProfitValue(final long newValue) {
        SwingUtilities.invokeLater(() ->
                {
                    profitValue = newValue;
                    // Value reset to ensure rate is shown immediately, instead of waiting for next time increment
                    lastProfitValue = 0;
                }
        );
    }


    /**
     * Updates startTimeMillies display
     */
    public void updateStartTimeMillies(final long newValue) {
        SwingUtilities.invokeLater(() ->
                {
                    startTimeMillies = newValue;
                    lastTickMillies = System.currentTimeMillis();
                }
        );
    }

    public void updateActiveTicks(final long newValue) {
        SwingUtilities.invokeLater(() ->
                {
                    activeTicks = newValue;
                    lastTickMillies = System.currentTimeMillis();
                }
        );
    }

    public void startSession()
    {
        SwingUtilities.invokeLater(() ->
                {
                    inProfitTrackSession = true;
                    lastWidth = 0;
                }
        );
    }

    public void setBankStatus(boolean bankReady)
    {
        SwingUtilities.invokeLater(() ->
                hasBankData = bankReady
        );
    }

    private static String formatTimeIntervalFromMs(final long totalMsElapsed, boolean showMilliseconds)
    {
        /*
        elapsed seconds to format HH:MM:SS
         */
        final long ms = totalMsElapsed % 1000;
        final long sec = totalMsElapsed / 1000 % 60;
        final long min = (totalMsElapsed / 60000) % 60;
        final long hr = totalMsElapsed / 3600000;

        if (showMilliseconds) {
            return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
        } else {
            return String.format("%02d:%02d:%02d", hr, min, sec);
        }
    }

    static long calculateProfitHourly(long millisecondsElapsed, long profit)
    {
        long averageProfitThousandForHour;
        double averageProfitPerMillisecond;

        if (millisecondsElapsed > 0)
        {
            averageProfitPerMillisecond = (double)profit / millisecondsElapsed;
        }
        else
        {
            // can't divide by zero, not enough time has passed
            averageProfitPerMillisecond = 0;
        }

        averageProfitThousandForHour = (long)(averageProfitPerMillisecond * 3600);

        return averageProfitThousandForHour;
    }
}
