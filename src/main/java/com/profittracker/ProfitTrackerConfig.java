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
    String VISUAL_SETTINGS = "Visual";

    @ConfigSection(
            name = "Behavior",
            description = "Settings for calculation behavior.",
            position =  1,
            closedByDefault = false
    )
    String BEHAVIOR_SETTINGS = "Behavior";

    @ConfigSection(
            name = "Calculation",
            description = "Settings for price calculation.",
            position =  2,
            closedByDefault = false
    )
    String CALCULATION_SETTINGS = "Calculation";

    @ConfigItem(
            keyName = "goldDrops",
            name = "Show value changes (gold drops)",
            description = "Show each profit increase or decrease.",
            section = VISUAL_SETTINGS
    )
    default boolean goldDrops()
    {
        return true;
    }

    @ConfigItem(
            keyName = "unhideGoldDrops",
            name = "Unhide value changes",
            description = "Prevents other plugins from hiding value changes if they are enabled.",
            section = VISUAL_SETTINGS
    )
    default boolean unhideGoldDrops()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoStart",
            name = "Automatically start tracking",
            description = "Automatically begin tracking profit on session start.",
            section = BEHAVIOR_SETTINGS
    )
    default boolean autoStart()
    {
        return true;
    }

    @ConfigItem(
            keyName = "rememberProfit",
            name = "Remember profit",
            description = "Profit will be remembered between application closes.",
            section = BEHAVIOR_SETTINGS
    )
    default boolean rememberProfit()
    {
        return true;
    }

    @ConfigItem(
            keyName = "shortDrops",
            name = "Shorten drop numbers",
            description = "Shorten drop numbers like 1.2K instead of 1,223, or 10M instead of 10,000,000.",
            section = VISUAL_SETTINGS
    )
    default boolean shortDrops()
    {
        return true;
    }

    @ConfigItem(
            keyName = "iconStyle",
            name = "Icon style",
            description = "Dynamically adjust the coin icon based on the drop value, or select a specific icon.",
            section = VISUAL_SETTINGS
    )
    default ProfitTrackerIconType iconStyle()
    {
        return ProfitTrackerIconType.DYNAMIC;
    }

    @ConfigItem(
            keyName = "estimateUntradeables",
            name = "Estimate untradeable item values",
            description = "Some untradeable items will utilize equivalent values of the best items they can convert into.",
            section = CALCULATION_SETTINGS
    )
    default boolean estimateUntradeables()
    {
        return true;
    }

    @ConfigItem(
            keyName = "onlineOnlyRate",
            name = "Online only rate",
            description = "Show profit rate only for time spent logged in.",
            position =  0,
            section = CALCULATION_SETTINGS
    )
    default boolean onlineOnlyRate()
    {
        return false;
    }

    @ConfigItem(
            keyName = "valueMode",
            name = "Value",
            description = "Method used to estimate the value of items.",
            section = CALCULATION_SETTINGS
    )
    default ProfitTrackerPriceType valueMode()
    {
        return ProfitTrackerPriceType.GE;
    }
}

