package com.profittracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

import java.util.Arrays;
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
            log.info("Bad item id!" + itemId);
            return 0;

        }

        if (itemId == EMPTY_SLOT_ITEMID)
        {
            return 0;
        }

        if (Arrays.stream(RUNE_POUCH_ITEM_IDS).anyMatch(pouchID -> itemId == pouchID))
        {
            log.info(String.format("calculateItemValue itemId = %d (Rune pouch variant)", itemId));
            return calculateRunePouchValue();
        }

        log.info(String.format("calculateItemValue itemId = %d", itemId));

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

        newInventoryValue = Arrays.stream(items).flatMapToLong(item ->
                LongStream.of(calculateItemValue(item))
        ).sum();

        return newInventoryValue;
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
        log.info(String.format("calculateRuneValue runeId = %d", runeId));
        return itemManager.getItemPrice(runePouchEnum.getIntValue(runeId)) * runeQuantity;
    }

    public long calculateInventoryAndEquipmentValue()
    {
        /*
        calculate total inventory + equipment value
         */

        return calculateInventoryValue() + calculateEquipmentValue();
    }


}
