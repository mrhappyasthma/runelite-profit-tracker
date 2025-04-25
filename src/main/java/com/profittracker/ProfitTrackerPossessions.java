package com.profittracker;
import net.runelite.api.Item;

/**
 * Data structure for holding information about a players possessions
 * Should be associated with a particular player, or account type like Leagues/Standard/Beta
 */
public class ProfitTrackerPossessions {
    public Item[] inventoryItems;
    public Item[] bankItems;
    public Item[] grandExchangeItems;
}