package com.profittracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * The ProfitTrackerConfig class is used to provide user preferences to the ProfitTrackerPlugin.
 */
@ConfigGroup(ProfitTrackerConfig.GROUP)
public interface ProfitTrackerConfig extends Config
{
    String GROUP = "ptconfig";

    @ConfigSection(
            name = "Visual",
            description = "Settings for what the plugin features look like.",
            position =  0,
            closedByDefault = false
    )
    String visualSettings = "Visual";

    @ConfigSection(
            name = "Behavior",
            description = "Settings for calculation behavior.",
            position =  1,
            closedByDefault = false
    )
    String behaviorSettings = "Behavior";

    @ConfigItem(
            keyName = "goldDrops",
            name = "Show value changes (gold drops)",
            description = "Show each profit increase or decrease.",
            section = visualSettings
    )
    default boolean goldDrops()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoStart",
            name = "Automatically start tracking",
            description = "Automatically begin tracking profit on session start.",
            section = behaviorSettings
    )
    default boolean autoStart()
    {
        return true;
    }

    @ConfigItem(
            keyName = "rememberProfit",
            name = "Remember profit",
            description = "Profit will be remembered between application closes.",
            section = behaviorSettings
    )
    default boolean rememberProfit()
    {
        return true;
    }

    @ConfigItem(
            keyName = "shortDrops",
            name = "Shorten drop numbers",
            description = "Shorten drop numbers like 1.2K instead of 1,223, or 10M instead of 10,000,000.",
            section = visualSettings
    )
    default boolean shortDrops()
    {
        return true;
    }

    @ConfigItem(
            keyName = "iconStyle",
            name = "Icon style",
            description = "Dynamically adjust the coin icon based on the drop value, or select a specific icon.",
            section = visualSettings
    )
    default ProfitTrackerIconType iconStyle()
    {
        return ProfitTrackerIconType.DYNAMIC;
    }

    @ConfigItem(
            keyName = "estimateUntradeables",
            name = "Estimate untradeable item values",
            description = "Some untradeable items will utilize equivalent values of the best items they can convert into.",
            section = behaviorSettings
    )
    default boolean estimateUntradeables()
    {
        return true;
    }

    @ConfigItem(
            keyName = "onlineOnlyRate",
            name = "Online only rate",
            description = "Show profit rate only for time spent logged in.",
            section = visualSettings
    )
    default boolean onlineOnlyRate()
    {
        return false;
    }
}

