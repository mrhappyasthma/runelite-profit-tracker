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

    private final int[] COLLECTION_BOX_INVENTORY_IDS = {
            InventoryID.TRADINGPOST_SELL_0,
            InventoryID.TRADINGPOST_SELL_1,
            InventoryID.TRADINGPOST_SELL_2,
            InventoryID.TRADINGPOST_SELL_3,
            InventoryID.TRADINGPOST_SELL_4,
            InventoryID.TRADINGPOST_SELL_5,
            InventoryID.GE_COLLECT_6,
            InventoryID.GE_COLLECT_7
    };

    private final ItemManager itemManager;
    private final Client client;
    @Inject
    private ProfitTrackerConfig config;
    private GrandExchangeOfferData[] offers = new GrandExchangeOfferData[8];

    /**
     * Data storage for GE offers as the normal object always maintains a reference,
     * and we need to be able to compare changes that have occurred
     */
    private static class GrandExchangeOfferData{
        int quantitySold;
        int id;
        int totalQuantity;
        int price;
        int spent;
        GrandExchangeOfferState state;

        public GrandExchangeOfferData(GrandExchangeOffer offer){
            this.quantitySold = offer.getQuantitySold();
            this.id = offer.getItemId();
            this.totalQuantity = offer.getTotalQuantity();
            this.price = offer.getPrice();
            this.spent = offer.getSpent();
            this.state = offer.getState();
        }
    }

    public ProfitTrackerInventoryValue( Client client, ItemManager itemManager, ProfitTrackerConfig config) {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
    }

    public void setOffers(GrandExchangeOffer[] offers){
        if (offers == null){
            this.offers = new GrandExchangeOfferData[8];
            return;
        }
        for (int index = 0; index < offers.length; index++){
            this.offers[index] = new GrandExchangeOfferData(offers[index]);
        }
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
        return (long)(itemManager.getItemPrice(runePouchEnum.getIntValue(runeId))) * runeQuantity;
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
        Item[] inventoryItems = new Item[0];
        Item[] equipmentItems = new Item[0];

        if (inventoryContainer != null){
            inventoryItems = inventoryContainer.getItems();
        }
        if (equipmentContainer != null){
            equipmentItems = equipmentContainer.getItems();
        }

        Item[] personItems = ArrayUtils.addAll(inventoryItems, equipmentItems);
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
        for (GrandExchangeOfferData offer : offers) {
            if (offer == null) {
                items.add(new Item(-1, 0));
                continue;
            }
            switch (offer.state) {
                case BOUGHT:
                case BUYING:
                    coins = new Item(ItemID.COINS, offer.price * (offer.totalQuantity - offer.quantitySold)); //Gold left to spend
                    items.add(coins);
                    break;
                case SOLD:
                case SELLING:
                    items.add(new Item(offer.id, offer.totalQuantity - offer.quantitySold)); //Items left to sell
                    break;
                case CANCELLED_BUY:
                case CANCELLED_SELL:
                    //All pending value in the offer will be moved to the collection item container
                    break;
                case EMPTY:
                default:
                    items.add(new Item(-1, 0));
                    break;
            }
        }
        return ArrayUtils.addAll(items.toArray(new Item[0]), getCollectionBoxContents());
    }

    /**
     * Gets all items sitting in the collection box for GE offers.
     * Can only access this data when the interface is open, avoid calling from something like onGrandExchangeOfferChanged
     * which can happen at any time.
     */
    private Item[] getCollectionBoxContents(){
        Item[] items = new Item[0];
        for (int collectionBoxInventoryId : COLLECTION_BOX_INVENTORY_IDS) {
            ItemContainer collectionBox = client.getItemContainer(collectionBoxInventoryId);
            if (collectionBox != null) {
                items = ArrayUtils.addAll(items, collectionBox.getItems());
            }
        }
        return items;
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
            boolean replaceItem = true;
            switch (resultItems[i].getId()){
                case ItemID.MINNOW:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.RAW_SHARK,resultItems[i].getQuantity() / 40));
                    break;
                //Mark of grace for amylase crystals seems to be covered already by the GE value checker
                case ItemID.VARLAMORE_WYRM_AGILITY_TERMITE:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.AMYLASE,resultItems[i].getQuantity()));
                    break;
                case ItemID.AGILITYARENA_TICKET: //Old agility arena ticket for pirate's hook
                case ItemID.AGILITYARENA_VOUCHER: //Brimhaven voucher for pirate's hook
                    long hookValue = calculateItemValue(new Item(ItemID.PIRATEHOOK, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * hookValue / 800)));
                    break;
                case ItemID.STAR_DUST:
                case ItemID.STAR_DUST_25:
                case ItemID.STAR_DUST_75:
                case ItemID.STAR_DUST_125:
                case ItemID.STAR_DUST_175:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.SOFTCLAY,resultItems[i].getQuantity() * 2 / 3));
                    break;
                case ItemID.MOTHERLODE_NUGGET:
                case ItemID.MGUILD_MINERALS:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.SOFTCLAY,resultItems[i].getQuantity() * 10));
                    break;
                case ItemID.FORESTRY_CURRENCY: //Anima bark for felling axe handle
                    long handleValue = calculateItemValue(new Item(ItemID.FORESTRY_2H_AXE_HANDLE, 1)) - calculateItemValue(new Item(ItemID.OAK_LOGS, 500));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * handleValue / 10000)));
                    break;
                case ItemID.PRIF_CRYSTAL_SHARD: //Crystal shard high alch
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,resultItems[i].getQuantity() * 6000));
                    break;
                case ItemID.PRIF_CRYSTAL_SHARD_CRUSHED:
                    // Profit from making divine super combat, used for crystal shards/dust
                    long potionProfit = calculateItemValue(new Item(ItemID._4DOSEDIVINECOMBAT, 1)) - calculateItemValue(new Item(ItemID._4DOSE2COMBAT,1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * potionProfit / 4)));
                    break;
                case ItemID.TZHAAR_TOKEN: //Tokkul for onyx
                    long onyxValue = calculateItemValue(new Item(ItemID.ONYX, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * onyxValue / 300000)));
                    break;
                case ItemID.ABYSSAL_PEARL: //Abyssal pearls for ring of the elements
                    long roteValue = calculateItemValue(new Item(ItemID.RING_OF_ELEMENTS, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * roteValue / 400)));
                    break;
                case ItemID.VILLAGE_TRADE_STICKS: //Trading sticks for gout tubers
                    long tuberValue = calculateItemValue(new Item(ItemID.VILLAGE_RARE_TUBER, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * tuberValue / 120)));
                    break;
                case ItemID.FOSSIL_MERMAID_TEAR: //Mermaid tears for merfolk trident
                    long tridentValue = calculateItemValue(new Item(ItemID.MERFOLK_TRIDENT, 1));
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * tridentValue / 400)));
                    break;
                case ItemID.KONAR_KEY: //Brimstone key high alch
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.COINS,(int)(resultItems[i].getQuantity() * 48000)));
                    break;
                case ItemID.BIRD_EGG_BLUE: //Bird eggs can be traded in for seed nests
                case ItemID.BIRD_EGG_RED:
                case ItemID.BIRD_EGG_GREEN:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.BIRD_NEST_EMPTY,resultItems[i].getQuantity()));
                    break;
                case ItemID.BIRD_NEST_EGG_BLUE:
                case ItemID.BIRD_NEST_EGG_RED:
                case ItemID.BIRD_NEST_EGG_GREEN:
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.BIRD_NEST_EMPTY,resultItems[i].getQuantity() * 2));
                    break;
                case ItemID.MAGIC_IMP_BOX_FULL:
                case ItemID.MAGIC_IMP_BOX_HALF:
                    //Replace un-tradeable magic imp boxes with regular ones for value check
                    //Otherwise using them and opening the bank would cause confusing small profits
                    extraItems = ArrayUtils.add(extraItems,new Item(ItemID.MAGIC_IMP_BOX,resultItems[i].getQuantity()));
                    break;
                //TODO Seedlings: Have unwatered seedlings turn into the seed + pot, and the watered versions into saplings
                default:
                    replaceItem = false;
                    break;
            }
            if (replaceItem) {
                resultItems[i] = new Item(-1,0);
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
     * @return Map of item ID -> QTY
     */
    private static Map<Integer, Integer> mapItemArray(Item[] items){
        return Arrays.stream(items)
                .filter((item) -> item.getQuantity() > 0)
                .collect(Collectors.toMap(Item::getId, Item::getQuantity, Integer::sum));
    }

    public Item[] getItemCollectionDifference(Item[] originalItems, Item[] newItems, boolean replaceUntradeables){
        if (replaceUntradeables){
            //Replace untradeables with their equivalent items.
            //The replaceUntradeables function is inaccurate for very small amounts, so we need to perform it over the source
            //with larger quantities instead of over the result difference between collections which generally is just 1 item.
            //For example, a single stardust = 2/3rds of a soft clay, which is smaller than 1, and so its quantity is truncated by the Item object.
            originalItems = replaceUntradeables(originalItems);
            newItems = replaceUntradeables(newItems);
        }
        return getItemCollectionDifference(originalItems, newItems);
    }

    /**
     * Compares the two arrays, returning an array of item differences
     * For example, dropping a shark would be an array of 1 shark item, with quantity -1
     * @return Array of items with quantity set to the difference
     */
    public static Item[] getItemCollectionDifference(Item[] originalItems, Item[] newItems){
        Map<Integer, Integer> originalItemList = mapItemArray(originalItems);
        Map<Integer, Integer> newItemList = mapItemArray(newItems);
        //Subtract old quantities from new to get difference
        originalItemList.forEach((id, quantity) -> newItemList.merge(id, -quantity,(a,b)-> {
            int sum = a + b;
            //Returning null if merge removes the entry
            return sum != 0 ? sum : null;
        }));

        //Convert back to item array
        List<Item> itemDifference = new ArrayList<>();
        newItemList.forEach((id, quantity) -> itemDifference.add(new Item(id,quantity)));

        return itemDifference.toArray(new Item[0]);
    }

    /**
     * Returns an item array which is the sum of two other item arrays, adjusting quantities as needed
     * Null collections are not added
     * If both collections are null, returns null
     */
    public static Item[] getItemCollectionSum(Item[] items1, Item[] items2){
        if (items1 == null || items2 == null){
            if (items1 != null) {
                return items1;
            }
            return items2;
        }
        Map<Integer, Integer> firstItems = mapItemArray(items1);
        Map<Integer, Integer> secondItems = mapItemArray(items2);
        firstItems.forEach((id, quantity) -> secondItems.merge(id, quantity,(a,b)-> {
            int sum = a + b;
            //Returning null if merge removes the entry
            return sum != 0 ? sum : null;
        }));

        //Convert back to item array
        List<Item> itemSum = new ArrayList<>();
        secondItems.forEach((id, quantity) -> itemSum.add(new Item(id,quantity)));

        return itemSum.toArray(new Item[0]);
    }
}
