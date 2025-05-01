package com.profittracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.ArrayUtils;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.InventoryID;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Slf4j
public class ProfitTrackerInventoryValue {
    /*
    Provide functional methods for calculating inventory value
     */
    /*
    Singletons which will be provided at creation by the plugin
     */

    static final int EMPTY_SLOT_ITEMID = -1;

    private final int[] RUNE_POUCH_ITEM_IDS = {
            ItemID.BH_RUNE_POUCH,
            ItemID.BH_RUNE_POUCH_TROUVER,
            ItemID.DIVINE_RUNE_POUCH,
            ItemID.DIVINE_RUNE_POUCH_TROUVER
    };

    private final int[] RUNE_POUCH_AMOUNT_VARBITS = {
            VarbitID.RUNE_POUCH_QUANTITY_1,
            VarbitID.RUNE_POUCH_QUANTITY_2,
            VarbitID.RUNE_POUCH_QUANTITY_3,
            VarbitID.RUNE_POUCH_QUANTITY_4
    };

    private final int[] RUNE_POUCH_RUNE_VARBITS = {
            VarbitID.RUNE_POUCH_TYPE_1,
            VarbitID.RUNE_POUCH_TYPE_2,
            VarbitID.RUNE_POUCH_TYPE_3,
            VarbitID.RUNE_POUCH_TYPE_4
    };

    private final ItemManager itemManager;
    private final Client client;
    @Inject
    private ProfitTrackerConfig config;
    private GrandExchangeOfferData[] offers = new GrandExchangeOfferData[8];


    public ProfitTrackerInventoryValue( Client client, ItemManager itemManager, ProfitTrackerConfig config) {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
    }

    private long calculateItemValue(Item item) {
        /*
        Calculate GE value of single item
         */

        int itemId = item.getId();

        if (itemId < -1)
        {
            // unexpected
            log.debug("Bad item id!" + itemId);
            return 0;

        }

        if (itemId == EMPTY_SLOT_ITEMID)
        {
            return 0;
        }

        if (Arrays.stream(RUNE_POUCH_ITEM_IDS).anyMatch(pouchID -> itemId == pouchID))
        {
            log.debug(String.format("calculateItemValue itemId = %d (Rune pouch variant)", itemId));
            return item.getQuantity() * calculateRunePouchValue();
        }

        log.debug(String.format("calculateItemValue itemId = %d", itemId));

        // multiply quantity  by GE value
        return (long) item.getQuantity() * (itemManager.getItemPrice(itemId));
    }

    public long calculateContainerValue(int containerID)
    {
        /*
        calculate total inventory value
         */

        ItemContainer container = client.getItemContainer(containerID);

        if (container == null)
        {
            return 0;
        }

        Item[] items = container.getItems();

        return calculateItemValue(items);
    }

    /**
     * Calculates the value of an array of items
     * @param items
     * @return
     */
    public long calculateItemValue(Item[] items) {
        if (config.estimateUntradeables()){
            items = replaceUntradeables(items);
        }
        return Arrays.stream(items).flatMapToLong(item ->
                LongStream.of(calculateItemValue(item))
        ).sum();
    }

    public long calculateInventoryValue()
    {
        /*
        calculate total inventory value
         */

        return calculateContainerValue(InventoryID.INV);

    }

    public long calculateEquipmentValue()
    {
        /*
        calculate total equipment value
         */
        return calculateContainerValue(InventoryID.WORN);
    }

    public long calculateRunePouchValue()
    {
        long runePouchValue = 0;
        EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);

        for (int i = 0; i < RUNE_POUCH_AMOUNT_VARBITS.length; i++)
        {
            runePouchValue += calculateRuneValue(
                    client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]),
                    client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]),
                    runePouchEnum
            );
        }

        return runePouchValue;
    }

    public long calculateRuneValue(int runeId, int runeQuantity, EnumComposition runePouchEnum)
    {
        if (runeQuantity == 0){
            return 0;
        }
        log.debug(String.format("calculateRuneValue runeId = %d", runeId));
        return itemManager.getItemPrice(runePouchEnum.getIntValue(runeId)) * runeQuantity;
    }

    public long calculateInventoryAndEquipmentValue()
    {
        /*
        calculate total inventory + equipment value
         */

        return calculateInventoryValue() + calculateEquipmentValue();
    }

    /**
     * Gets all items on the player, or null if inventory or equipment is null
     * @return Array of items from inventory and equipment containers
     */
    public Item[] getInventoryAndEquipmentContents(){
        ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INV);
        ItemContainer equipmentContainer = client.getItemContainer(InventoryID.WORN);

        if (inventoryContainer == null || equipmentContainer == null)
        {
            return null;
        }

        Item[] inventoryItems = inventoryContainer.getItems();
        Item[] equipmentItems = equipmentContainer.getItems();
        Item[] personItems = ArrayUtils.addAll(inventoryItems,equipmentItems);
        // Expand to have runes from pouch as individual items
        return expandContainers(personItems);
    }

    public Item[] getBankContents(){
        ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);

        if (bankContainer == null)
        {
            return null;
        }
        return expandContainers(bankContainer.getItems());
    }

    public Item[] getGrandExchangeContents(){
        ArrayList<Item> items = new ArrayList<> ();
        //Unclear why, but without an intermediate storage for this variable, just doing items.add(new ...) caused improper quantities
        Item coins;
        for (GrandExchangeOffer offer : offers) {
            if (offer == null) {
                items.add(new Item(-1, 0));
                continue;
            }
            switch (offer.getState()) {
                case BOUGHT:
                case BUYING:
                case CANCELLED_BUY:
                    items.add(new Item(offer.getItemId(), offer.getQuantitySold()));
                    coins = new Item(net.runelite.api.gameval.ItemID.COINS, offer.getPrice() * (offer.getTotalQuantity() - offer.getQuantitySold()));
                    items.add(coins);
                    break;
                case SOLD:
                case SELLING:
                case CANCELLED_SELL:
                    items.add(new Item(offer.getItemId(), offer.getTotalQuantity() - offer.getQuantitySold()));
                    coins = new Item(net.runelite.api.gameval.ItemID.COINS, offer.getSpent());
                    items.add(coins);
                    break;
                case EMPTY:
                default:
                    items.add(new Item(-1, 0));
                    break;
            }
        }
        return items.toArray(new Item[0]);
    }

    private Item[] expandContainers(Item[] items){
        Item[] extraItems = new Item[0];
        for (int i = 0; i < items.length; i++){
            final int lambdaId = items[i].getId(); // Compiler complains without this
            if (Arrays.stream(RUNE_POUCH_ITEM_IDS).anyMatch(pouchID -> lambdaId == pouchID)){
                extraItems = ArrayUtils.addAll(extraItems,getRunePouchItems());
                items[i] = new Item(-1,0); // Get rid of pouch
                break; //TODO Other containers
            }
        }
        return ArrayUtils.addAll(items,extraItems);
    }

    /**
     * Replaces various untradeable items with items they can be converted into, or coin values of those items
     */
    private Item[] replaceUntradeables(Item[] items){
        Item[] extraItems = new Item[0];
        Item[] resultItems = items.clone();
        for (int i = 0; i < resultItems.length; i++){
            switch (resultItems[i].getId()){
                case ItemID.MINNOW:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.RAW_SHARK,resultItems[i].getQuantity() / 40));
                    resultItems[i] = new Item(-1,0);
                    break;
                //Mark of grace for amylase crystals seems to be covered already by the GE value checker
                case ItemID.VARLAMORE_WYRM_AGILITY_TERMITE:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.AMYLASE,resultItems[i].getQuantity()));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.AGILITYARENA_TICKET: //Old agility arena ticker for pirate's hook
                case ItemID.AGILITYARENA_VOUCHER: //Brimhaven voucher for pirate's hook
                    int hookValue = (int) calculateItemValue(new Item(ItemID.PIRATEHOOK, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * hookValue / 800));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.STAR_DUST:
                case ItemID.STAR_DUST_25:
                case ItemID.STAR_DUST_75:
                case ItemID.STAR_DUST_125:
                case ItemID.STAR_DUST_175:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.SOFTCLAY,resultItems[i].getQuantity() * 2 / 3));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.MOTHERLODE_NUGGET:
                case ItemID.MGUILD_MINERALS:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.SOFTCLAY,resultItems[i].getQuantity() * 10));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.FORESTRY_CURRENCY: //Anima bark for felling axe handle
                    int handleValue = (int)(calculateItemValue(new Item(ItemID.FORESTRY_2H_AXE_HANDLE, 1)) - calculateItemValue(new Item(ItemID.OAK_LOGS, 500)));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * handleValue / 10000));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.PRIF_CRYSTAL_SHARD: //Crystal shard high alch
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * 6000));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.PRIF_CRYSTAL_SHARD_CRUSHED:
                    // Profit from making divine super combat, used for crystal shards/dust
                    int potionProfit = (int)(calculateItemValue(new Item(ItemID._4DOSEDIVINECOMBAT, 1)) - calculateItemValue(new Item(ItemID._4DOSE2COMBAT,1)));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * potionProfit / 4));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.TZHAAR_TOKEN: //Tokkul for onyx
                    int onyxValue = (int) calculateItemValue(new Item(ItemID.ONYX, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * onyxValue / 300000));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.ABYSSAL_PEARL: //Abyssal pearls for ring of the elements
                    int roteValue = (int) calculateItemValue(new Item(ItemID.RING_OF_ELEMENTS, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * roteValue / 400));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.VILLAGE_TRADE_STICKS: //Trading sticks for gout tubers
                    int tuberValue = (int) calculateItemValue(new Item(ItemID.VILLAGE_RARE_TUBER, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * tuberValue / 120));
                    resultItems[i] = new Item(-1,0);
                    break;
                case ItemID.FOSSIL_MERMAID_TEAR: //Mermaid tears for merfolk trident
                    int tridentValue = (int) calculateItemValue(new Item(ItemID.MERFOLK_TRIDENT, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * tridentValue / 400));
                    resultItems[i] = new Item(-1,0);
                    break;
            }
        }
        return ArrayUtils.addAll(resultItems,extraItems);
    }

    private Item[] getRunePouchItems(){
        List<Item> runes = new ArrayList<>();
        EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);

        for (int i = 0; i < RUNE_POUCH_AMOUNT_VARBITS.length; i++)
        {
            int itemID = runePouchEnum.getIntValue(client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]));
            runes.add(new Item(itemID,client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i])));
        }

        return runes.toArray(new Item[0]);
    }

    /**
     * Converts the given item array into a map
     * @param items
     * @return Map of item ID -> QTY
     */
    private Map<Integer, Integer> mapItemArray(Item[] items){
        return Arrays.stream(items)
                .filter((item) -> item.getQuantity() > 0)
                .collect(Collectors.toMap(Item::getId, Item::getQuantity, Integer::sum));
    }

    /**
     * Compares the two arrays, returning an array of item differences
     * For example, dropping a shark would be an array of 1 shark item, with quantity -1
     * @param originalItems
     * @param newItems
     * @return
     */
    public Item[] getItemCollectionDifference(Item[] originalItems, Item[] newItems){
        Map<Integer, Integer> originalItemList = mapItemArray(originalItems);
        Map<Integer, Integer> newItemList = mapItemArray(newItems);
        //Subtract old quantities from new to get difference
        originalItemList.forEach((id, quantity) -> newItemList.merge(id, -quantity,(a,b)-> {
            int sum = a + b;
            //Returning null for merge removes the entry
            return sum != 0 ? sum : null;
        }));

        //Convert back to item array
        List<Item> itemDifference = new ArrayList<>();
        newItemList.forEach((id, quantity) -> itemDifference.add(new Item(id,quantity)));

        return itemDifference.toArray(new Item[0]);
    }
}
