package com.profittracker;
import net.runelite.api.Item;
import static com.profittracker.ProfitTrackerInventoryValue.getItemCollectionSum;

/**
 * Data structure for holding information about a players possessions
 * Should be associated with a particular player, or account type like Leagues/Standard/Beta
 */
public class ProfitTrackerPossessions {
    public Item[] inventoryItems;
    public Item[] bankItems;
    public Item[] grandExchangeItems;

    public Item[] getItems(){
        Item[] storedItems = getItemCollectionSum(bankItems, grandExchangeItems);
        return getItemCollectionSum(inventoryItems, storedItems);
    }

    /**
     * If any collection is null, it will instead use the items from the given possessions
     */
    public void fillNullItems(ProfitTrackerPossessions knownPossessions){
        if (inventoryItems == null){
            inventoryItems = knownPossessions.inventoryItems;
        }
        if (bankItems == null){
            bankItems = knownPossessions.bankItems;
        }
        if (grandExchangeItems == null){
            grandExchangeItems = knownPossessions.grandExchangeItems;
        }
    }
}