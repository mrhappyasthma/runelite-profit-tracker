package com.profittracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import net.runelite.api.events.*;

import net.runelite.api.widgets.WidgetID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.VarbitChanged;

import java.util.Arrays;

@Slf4j
@PluginDescriptor(
        name = "Profit Tracker"
)
public class ProfitTrackerPlugin extends Plugin
{
    ProfitTrackerGoldDrops goldDropsObject;
    ProfitTrackerInventoryValue inventoryValueObject;

    // the profit will be calculated against this value
    private long prevInventoryValue;
    private long totalProfit;

    private long startTickMillis;

    private boolean skipTickForProfitCalculation;
    private boolean inventoryValueChanged;
    private boolean inProfitTrackSession;
    private boolean runePouchContentsChanged;
    //Remembers if the bank was open last tick, because tick perfect bank close reports changes late
    private boolean bankJustClosed;
    private int[] RUNE_POUCH_VARBITS = {
            Varbits.RUNE_POUCH_AMOUNT1,
            Varbits.RUNE_POUCH_AMOUNT2,
            Varbits.RUNE_POUCH_AMOUNT3,
            Varbits.RUNE_POUCH_AMOUNT4,
            Varbits.RUNE_POUCH_RUNE1,
            Varbits.RUNE_POUCH_RUNE2,
            Varbits.RUNE_POUCH_RUNE3,
            Varbits.RUNE_POUCH_RUNE4
    };

    @Inject
    private Client client;

    @Inject
    private ProfitTrackerConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ProfitTrackerOverlay overlay;

    @Override
    protected void startUp() throws Exception
    {
        // Add the inventory overlay
        overlayManager.add(overlay);

        goldDropsObject = new ProfitTrackerGoldDrops(client, itemManager);

        inventoryValueObject = new ProfitTrackerInventoryValue(client, itemManager);

        initializeVariables();

        // start tracking only if plugin was re-started mid game
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            startProfitTrackingSession();
        }

    }

    private void initializeVariables()
    {
        // value here doesn't matter, will be overwritten
        prevInventoryValue = -1;

        // profit begins at 0 of course
        totalProfit = 0;

        // this will be filled with actual information in startProfitTrackingSession
        startTickMillis = 0;

        // skip profit calculation for first tick, to initialize first inventory value
        skipTickForProfitCalculation = true;

        inventoryValueChanged = false;

        inProfitTrackSession = false;

        runePouchContentsChanged = false;

    }

    private void startProfitTrackingSession()
    {
        /*
        Start tracking profit from now on
         */

        initializeVariables();

        // initialize timer
        startTickMillis = System.currentTimeMillis();

        overlay.updateStartTimeMillies(startTickMillis);

        overlay.startSession();

        inProfitTrackSession = true;
    }

    @Override
    protected void shutDown() throws Exception
    {
        // Remove the inventory overlay
        overlayManager.remove(overlay);

    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        /*
        Main plugin logic here

        1. If inventory changed,
            - calculate profit (inventory value difference)
            - generate gold drop (nice animation for showing gold earn or loss)

        2. Calculate profit rate and update in overlay

        */

        long tickProfit;

        if (!inProfitTrackSession)
        {
            return;
        }

        boolean skipOnce = false;
        if (bankJustClosed) {
            // Interacting with bank
            // itemContainerChanged does not report bank change if closed on same tick
            skipOnce = true;
        }
        bankJustClosed = false;

        if (inventoryValueChanged || runePouchContentsChanged)
        {
            if (skipOnce) {
                skipTickForProfitCalculation = true;
            }
            tickProfit = calculateTickProfit();

            // accumulate profit
            totalProfit += tickProfit;

            overlay.updateProfitValue(totalProfit);

            // generate gold drop
            if (config.goldDrops() && tickProfit != 0)
            {
                goldDropsObject.requestGoldDrop(tickProfit);
            }

            inventoryValueChanged = false;
            runePouchContentsChanged = false;
        }

    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        //Catch bank closing, as tick perfect close can cause onItemContainerChanged to not think it is in the bank
        if (event.getGroupId() == WidgetID.BANK_GROUP_ID || event.getGroupId() == WidgetID.BANK_INVENTORY_GROUP_ID) {
            bankJustClosed = true;
        }
    }


    private long calculateTickProfit()
    {
        /*
        Calculate and return the profit for this tick
        if skipTickForProfitCalculation is set, meaning this tick was bank / deposit
        so return 0

         */
        long newInventoryValue;
        long newProfit;

        // calculate current inventory value
        newInventoryValue = inventoryValueObject.calculateInventoryAndEquipmentValue();

        if (!skipTickForProfitCalculation)
        {
            // calculate new profit
            newProfit = newInventoryValue - prevInventoryValue;

        }
        else
        {
            /* first time calculation / banking / equipping */
            log.debug("Skipping profit calculation!");

            skipTickForProfitCalculation = false;

            // no profit this tick
            newProfit = 0;
        }

        // update prevInventoryValue for future calculations anyway!
        prevInventoryValue = newInventoryValue;

        return newProfit;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        /*
        this event tells us when inventory has changed
        and when banking/equipment event occured this tick
         */
        log.debug("onItemContainerChanged container id: " + event.getContainerId());

        int containerId = event.getContainerId();

        if( containerId == InventoryID.INVENTORY.getId() ||
            containerId == InventoryID.EQUIPMENT.getId()) {
            // inventory has changed - need calculate profit in onGameTick
            inventoryValueChanged = true;

        }

        // in these events, inventory WILL be changed but we DON'T want to calculate profit!
        if(     containerId == InventoryID.BANK.getId()) {
            // this is a bank interaction.
            // Don't take this into account
            skipTickForProfitCalculation = true;

        }

    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        runePouchContentsChanged = Arrays.stream(RUNE_POUCH_VARBITS).anyMatch(vb -> event.getVarbitId() == vb);
        if (Arrays.stream(RUNE_POUCH_VARBITS).anyMatch(vb -> event.getVarbitId() == vb)){
            runePouchContentsChanged = true;
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        /* for ignoring deposit in deposit box */
        log.debug(String.format("Click! ID: %d ,menuOption: %s, menuTarget: %s",
                  event.getId(), event.getMenuOption(), event.getMenuTarget()));
        String menuOption = event.getMenuOption();

        String containerMenuOptions[] = {"Deposit","Empty"};
        for (int i = 0; i < containerMenuOptions.length; i++){
            if (menuOption.startsWith(containerMenuOptions[i])){
                // Backup catch for various bank interfaces to deposit or empty sacks
                // Event object does not seem to provide information that would otherwise tell us it's a bank
                skipTickForProfitCalculation = true;
                break;
            }
        }

        // Ignore losses incurred by filling container items that are only empty-able to the bank
        switch (event.getItemId()) {
            case ItemID.PLANK_SACK: // Fill, empty | use (dumps into inventory)
                // Items can be used directly from sack
                // Filling sack requires inventory as intermediate
            case ItemID.FISH_SACK_BARREL:
            case ItemID.FISH_BARREL: //Fill, open | empty

            case ItemID.BRONZE_COFFIN: // Fill, configure, open
            case ItemID.BLACK_COFFIN:
            case ItemID.STEEL_COFFIN:
            case ItemID.SILVER_COFFIN:
            case ItemID.GOLD_COFFIN:

            case ItemID.GEM_BAG: //Fill, empty, open | empty
            case ItemID.COAL_BAG: // Fill, empty, open | fill, empty
                //Coal can be directly used from the sack, and be filled directly from bank

            case ItemID.COLOSSAL_POUCH: // Fill, empty | fill, empty
            case ItemID.GIANT_POUCH:
            case ItemID.LARGE_POUCH:
            case ItemID.MEDIUM_POUCH:
            case ItemID.SMALL_POUCH:

            // case ItemID.BASKET: //Fill, remove-one, empty | fill | basket turns into different name like "Bananas(#)"
                //Empty sack
            case ItemID.BOLT_POUCH: //Open(remove interface) | | bolts can be worn via armor interface extra ammo slot
            case ItemID.HERB_SACK: // Fill, empty, open | empty
                //Looting bag
                //Rune pouch // Covered by withdraw interface
            case ItemID.SEED_BOX: // Open
            case ItemID.STEEL_KEY_RING: //Add keys via use on ring | remove via remove interface
                //Flamtaer bag // Fill, empty | empty (dumps into inventory) | items can be used directly from sack
                //Tackle box
            case ItemID.MASTER_SCROLL_BOOK: // Interface with remove option | items can be used from book via activate and teleport
                //Gnomish firelighter // Check, uncharge

            case ItemID.LOG_BASKET: // Fill, Check(dialog based withdraw), Close/Open | Empty
            case ItemID.FORESTRY_KIT: // View(kit has withdraw interface), Fill | Use(dumps to bank)
            case ItemID.FORESTRY_BASKET: // Fill, view(kit has withdraw interface/basket has none) | use (dumps to bank)

            case ItemID.SMALL_MEAT_POUCH: // Fill, Empty
            case ItemID.LARGE_MEAT_POUCH: // Fill, Empty
            case ItemID.SMALL_FUR_POUCH: // Fill, Empty
            case ItemID.MEDIUM_FUR_POUCH: // Fill, Empty
            case ItemID.LARGE_FUR_POUCH: // Fill, Empty

            case ItemID.REAGENT_POUCH: // ??? | Use (dumps to bank)
                switch (menuOption.toLowerCase()) {
                    case "empty":
                    case "fill":
                    case "use":
                        log.debug("Ignoring storage item interaction.");
                        // Ignore manual changes to container items as the items have not been lost
                        skipTickForProfitCalculation = true;
                }
                break;
            // Don't ignore open containers, as items added directly to them don't get recorded
            case ItemID.OPEN_FISH_SACK_BARREL:
            case ItemID.OPEN_FISH_BARREL:
            case ItemID.OPEN_BRONZE_COFFIN: // Fill, configure, open
            case ItemID.OPEN_BLACK_COFFIN:
            case ItemID.OPEN_STEEL_COFFIN:
            case ItemID.OPEN_SILVER_COFFIN:
            case ItemID.OPEN_GOLD_COFFIN:

            case ItemID.OPEN_GEM_BAG:
            case ItemID.OPEN_COAL_BAG:

            case ItemID.OPEN_HERB_SACK:
            case ItemID.OPEN_LOG_BASKET:
            case ItemID.OPEN_FORESTRY_BASKET:

            case ItemID.SMALL_MEAT_POUCH_OPEN: // Fill, Empty
            case ItemID.LARGE_MEAT_POUCH_OPEN: // Fill, Empty
            case ItemID.SMALL_FUR_POUCH_OPEN: // Fill, Empty
            case ItemID.MEDIUM_FUR_POUCH_OPEN: // Fill, Empty
            case ItemID.LARGE_FUR_POUCH_OPEN: // Fill, Empty

            case ItemID.OPEN_REAGENT_POUCH:
                switch (menuOption.toLowerCase()) {
                    // Empty is not ignored, as we want an option to calculate profit for unrecorded items that
                    // were sucked into the container directly
                    case "fill":
                    case "use":
                        log.debug("Ignoring open storage item interaction.");
                        // Ignore manual changes to container items as the items have not been lost
                        skipTickForProfitCalculation = true;
                }
                break;
        }
    }

    @Provides
    ProfitTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ProfitTrackerConfig.class);
    }


    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        goldDropsObject.onScriptPreFired(scriptPreFired);
    }
}
