package com.profittracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
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
            ItemID.RUNE_POUCH,
            ItemID.RUNE_POUCH_L,
            ItemID.DIVINE_RUNE_POUCH,
            ItemID.DIVINE_RUNE_POUCH_L
    };

    private final int[] RUNE_POUCH_AMOUNT_VARBITS = {
            Varbits.RUNE_POUCH_AMOUNT1,
            Varbits.RUNE_POUCH_AMOUNT2,
            Varbits.RUNE_POUCH_AMOUNT3,
            Varbits.RUNE_POUCH_AMOUNT4
    };

    private final int[] RUNE_POUCH_RUNE_VARBITS = {
            Varbits.RUNE_POUCH_RUNE1,
            Varbits.RUNE_POUCH_RUNE2,
            Varbits.RUNE_POUCH_RUNE3,
            Varbits.RUNE_POUCH_RUNE4
    };

    private final ItemManager itemManager;
    private final Client client;

    public ProfitTrackerInventoryValue( Client client, ItemManager itemManager) {
        this.client = client;
        this.itemManager = itemManager;
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
        return item.getQuantity() * (itemManager.getItemPrice(itemId));
    }

    public long calculateContainerValue(InventoryID ContainerID)
    {
        /*
        calculate total inventory value
         */

        long newInventoryValue;

        ItemContainer container = client.getItemContainer(ContainerID);

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
        return Arrays.stream(items).flatMapToLong(item ->
                LongStream.of(calculateItemValue(item))
        ).sum();
    }

    public long calculateInventoryValue()
    {
        /*
        calculate total inventory value
         */

        return calculateContainerValue(InventoryID.INVENTORY);

    }

    public long calculateEquipmentValue()
    {
        /*
        calculate total equipment value
         */
        return calculateContainerValue(InventoryID.EQUIPMENT);
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
        ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INVENTORY);
        ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);


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
     * Compares the two arrays, returning an array of item differences
     * For example, dropping a shark would be an array of 1 shark item, with quantity -1
     * @param originalItems
     * @param newItems
     * @return
     */
    public Item[] getItemCollectionDif(Item[] originalItems, Item[] newItems){
        //Iterate over each item, finding any instances of its existence from before
        Item[] negativeItems = originalItems.clone();
        for (int i = 0; i < originalItems.length; i++){
            negativeItems[i] = new Item(originalItems[i].getId(),-originalItems[i].getQuantity());
        }
        Item[] itemIntermediateDif = ArrayUtils.addAll(negativeItems,newItems);

        //Create a nicer looking item list with only the actual changes
        HashMap<Integer, Integer> itemDifHash = new HashMap<>();

        for (int i = 0; i < itemIntermediateDif.length; i++){
            int itemID = itemIntermediateDif[i].getId();
            itemDifHash.putIfAbsent(itemID, 0);
            itemDifHash.put(itemID, itemDifHash.get(itemID) + itemIntermediateDif[i].getQuantity());
        }

        Iterator mapIt = itemDifHash.entrySet().iterator();
        while (mapIt.hasNext()){
            Map.Entry pair = (Map.Entry)mapIt.next();
            if ((Integer)(pair.getValue()) == 0){
                mapIt.remove();
            }
        }
        List<Item> itemDif = new ArrayList<>();
        mapIt = itemDifHash.entrySet().iterator();
        while (mapIt.hasNext()){
            Map.Entry pair = (Map.Entry)mapIt.next();
            itemDif.add(new Item((Integer)pair.getKey(),(Integer)pair.getValue()));
        }

        return itemDif.toArray(new Item[0]);
    }
}
