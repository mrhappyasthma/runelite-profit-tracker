package com.profittracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * The ProfitTrackerConfig class is used to provide user preferences to the ProfitTrackerPlugin.
 */
@ConfigGroup("ptconfig")
public interface ProfitTrackerConfig extends Config
{

    @ConfigItem(
            keyName = "goldDrops",
            name = "Show value changes (gold drops) ",
            description = "Show each profit increase or decrease"
    )
    default boolean goldDrops()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoStart",
            name = "Automatically start tracking",
            description = "Automatically begin tracking profit on session start."
    )
    default boolean autoStart()
    {
        return true;
    }

    @ConfigItem(
            keyName = "shortDrops",
            name = "Shorten drop numbers",
            description = "Shorten drop numbers like 1.2K instead of 1,223, or 10M instead of 10,000,000"
    )
    default boolean shortDrops()
    {
        return true;
    }

    @ConfigItem(
            keyName = "iconStyle",
            name = "Icon style",
            description = "Dynamically adjust the coin icon based on the drop value, or select a specific icon."
    )
    default ProfitTrackerIconType iconStyle()
    {
        return ProfitTrackerIconType.DYNAMIC;
    }

    @ConfigItem(
            keyName = "estimateUntradeables",
            name = "Estimate untradeable item values",
            description = "Some untradeable items will utilize equivalent values of the best items they can convert into."
    )
    default boolean estimateUntradeables()
    {
        return true;
    }
}

