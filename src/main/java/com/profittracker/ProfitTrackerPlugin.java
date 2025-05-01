package com.profittracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import net.runelite.api.events.*;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.VarbitChanged;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
    private Map<String, ProfitTrackerPossessions> accountPossessions;
    private ProfitTrackerPossessions previousPossessions;
    private String previousAccount;
    // Collection of items that was last used to calculate value, includes inventory and equipment
    private long totalProfit;

    private long startTickMillis;

    private boolean skipTickForProfitCalculation;
    private boolean inventoryValueChanged;
    private boolean bankValueChanged;
    private boolean grandExchangeValueChanged;
    private boolean inProfitTrackSession;
    private boolean runePouchContentsChanged;
    // Remembers if the bank was open last tick, because tick perfect bank close reports changes late
    private boolean bankJustClosed;
    // Set when using a deposit menu option. Used to create a depositing deficit for the next time you open bank
    // This ensures using a deposit box doesn't spam coin drops, but also doesn't get out of sync when the race
    // condition with menu options and container changes causes some anyway
    private boolean depositingItem;
    private long depositDeficit;
    private final int[] RUNE_POUCH_VARBITS = {
            VarbitID.RUNE_POUCH_QUANTITY_1,
            VarbitID.RUNE_POUCH_QUANTITY_2,
            VarbitID.RUNE_POUCH_QUANTITY_3,
            VarbitID.RUNE_POUCH_QUANTITY_4,
            VarbitID.RUNE_POUCH_TYPE_1,
            VarbitID.RUNE_POUCH_TYPE_2,
            VarbitID.RUNE_POUCH_TYPE_3,
            VarbitID.RUNE_POUCH_TYPE_4
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

        goldDropsObject = new ProfitTrackerGoldDrops(client, itemManager, config);

        inventoryValueObject = new ProfitTrackerInventoryValue(client, itemManager, config);

        initializeVariables();

        // start tracking only if plugin was re-started mid game
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            startProfitTrackingSession();
        }

    }

    private void initializeVariables()
    {
        accountPossessions = new HashMap<>();
        previousPossessions = null;
        previousAccount = null;

        // profit begins at 0 of course
        totalProfit = 0;

        // this will be filled with actual information in startProfitTrackingSession
        startTickMillis = 0;

        // skip profit calculation for first tick, to initialize first inventory value
        skipTickForProfitCalculation = true;

        inventoryValueChanged = false;

        bankValueChanged = false;

        grandExchangeValueChanged = false;

        inProfitTrackSession = false;

        runePouchContentsChanged = false;

        bankJustClosed = false;

        depositingItem = false;

        depositDeficit = 0;
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

    /**
     * Creates an entry for the current login if needed.
     * Hooks up the possessions collection for repeat use for the current login.
     */
    private void checkAccount()
    {
        //TODO Account for special worlds where a player with the same name might have different possessions
        String accountIdentifier = client.getLocalPlayer().getName();
        accountPossessions.putIfAbsent(client.getLocalPlayer().getName(),new ProfitTrackerPossessions());
        previousPossessions = accountPossessions.get(accountIdentifier);

        if (previousAccount == null || ! previousAccount.equals(accountIdentifier)) {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                inventoryValueObject.offers = client.getGrandExchangeOffers();
            }
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        // Remove the inventory overlay
        overlayManager.remove(overlay);

    }

    @Subscribe
    public void onGameTick(GameTick gameTick) throws Exception
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
            if (config.autoStart()){
                startUp();
                inventoryValueChanged = true;
            } else {
                return;
            }
        }

        checkAccount();

        boolean skipOnce = false;
        if (bankJustClosed) {
            // Interacting with bank
            // itemContainerChanged does not report bank change if closed on same tick
            skipOnce = true;
        }
        bankJustClosed = false;

        if (inventoryValueChanged || runePouchContentsChanged || bankValueChanged || grandExchangeValueChanged)
        {
            if (skipOnce) {
                skipTickForProfitCalculation = true;
            }
            tickProfit = calculateTickProfit();

            // accumulate profit
            if (depositingItem){
                // Track a deficit for deposits because of deposit box problems
                depositDeficit += tickProfit;
                depositingItem = false;
                tickProfit = 0;
            }

            // Resync with untracked changes from using deposit box
            if (bankValueChanged) {
                tickProfit += depositDeficit;
                depositDeficit = 0;
            }

            totalProfit += tickProfit;
            overlay.updateProfitValue(totalProfit);

            // generate gold drop
            if (config.goldDrops() && tickProfit != 0)
            {
                goldDropsObject.requestGoldDrop(tickProfit);
            }

            inventoryValueChanged = false;
            bankValueChanged = false;
            runePouchContentsChanged = false;
            grandExchangeValueChanged = false;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        //Catch untracked storage closing, as tick perfect close can cause onItemContainerChanged to not see the change
        if (event.getGroupId() == InterfaceID.HUNTSMANS_KIT || //Huntsman's kit
            event.getGroupId() == InterfaceID.SEED_VAULT) { // Seed vault
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
        Item[] newInventoryItems;
        Item[] newBankItems;
        Item[] newGrandExchangeItems;
        long newProfit;
        Item[] possessionDifference;
        Item[] bankDifference;
        Item[] grandExchangeDifference;

        // calculate current inventory value
        //newInventoryValue = inventoryValueObject.calculateInventoryAndEquipmentValue();
        newInventoryItems = inventoryValueObject.getInventoryAndEquipmentContents();
        newBankItems = inventoryValueObject.getBankContents();
        newGrandExchangeItems = inventoryValueObject.getGrandExchangeContents();

        if (!skipTickForProfitCalculation && previousPossessions.inventoryItems != null)
        {
            // calculate new profit
            // newProfit = newInventoryValue - prevInventoryValue;
            possessionDifference = inventoryValueObject.getItemCollectionDifference(previousPossessions.inventoryItems,newInventoryItems);
            newProfit = inventoryValueObject.calculateItemValue(possessionDifference);
            if (previousPossessions.bankItems != null && newBankItems != null) {
                bankDifference = inventoryValueObject.getItemCollectionDifference(previousPossessions.bankItems,newBankItems);
                // Profit is recalculated on all items instead of summed just in case item values could change between calculations
                possessionDifference = ArrayUtils.addAll(possessionDifference,bankDifference);
                newProfit = inventoryValueObject.calculateItemValue(possessionDifference);
            }
            if (previousPossessions.grandExchangeItems != null && newGrandExchangeItems != null) {
                grandExchangeDifference = inventoryValueObject.getItemCollectionDifference(previousPossessions.grandExchangeItems,newGrandExchangeItems);
                possessionDifference = ArrayUtils.addAll(possessionDifference,grandExchangeDifference);
                newProfit = inventoryValueObject.calculateItemValue(possessionDifference);
            }

            log.debug("Calculated " + newProfit + " profit for " + (possessionDifference.length) + " item changes.");
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
        //prevInventoryValue = newInventoryValue;
        previousPossessions.inventoryItems = newInventoryItems;
        if (newBankItems != null) {
            if (previousPossessions.bankItems == null) {
                // If user hasn't opened bank yet, the deficit doesn't help us resync
                depositDeficit = 0;
            }
            previousPossessions.bankItems = newBankItems;
        }
        previousPossessions.grandExchangeItems = newGrandExchangeItems;

        return newProfit;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        /*
        this event tells us when inventory has changed
        and when banking/equipment event occurred this tick
         */
        log.debug("onItemContainerChanged container id: " + event.getContainerId());

        int containerId = event.getContainerId();

        if (containerId == InventoryID.INV ||
            containerId == InventoryID.WORN) {
            // inventory has changed - need calculate profit in onGameTick
            inventoryValueChanged = true;
        }

        if (containerId == InventoryID.BANK) {
            bankValueChanged = true;
        }

        // In these events, inventory WILL be changed, but we DON'T want to calculate profit!
        if (containerId == 855 || // Huntsman's kit
            containerId == InventoryID.SEED_VAULT) { // Seed vault
            skipTickForProfitCalculation = true;
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        //Profit for GE offers is only calculated when we have seen a different non-empty state of the offer
        int slot = event.getSlot();
        GrandExchangeOffer offer = event.getOffer();

        if (offer.getState() == GrandExchangeOfferState.EMPTY && client.getGameState() != GameState.LOGGED_IN) {
            grandExchangeValueChanged = false;
        }else {
            if (inventoryValueObject.offers[slot] == null){
                grandExchangeValueChanged = true;
            }
            inventoryValueObject.offers[slot] = offer;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
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

        String[] containerMenuOptions = {"Deposit-"};
        for (String containerMenuOption : containerMenuOptions) {
            if (menuOption.startsWith(containerMenuOption)) {
                // Backup catch for various bank interfaces to deposit items
                // Event object does not seem to provide information that would otherwise tell us it's a bank
                // Still, it is possible to have game tick happen before a container changes to reflect menu option,
                // which can cause unexpected profit/loss, particularly when clicking a lot in a deposit box.
                depositingItem = true;
                break;
            }
        }

        // Container items
        // Ignore profit changes for items that act as storage only
        switch (event.getItemId()) {
            case ItemID.RCU_POUCH_COLOSSAL: // Fill, empty | fill, empty | essence must be taken out to use
            case ItemID.RCU_POUCH_GIANT:
            case ItemID.RCU_POUCH_LARGE:
            case ItemID.RCU_POUCH_MEDIUM:
            case ItemID.RCU_POUCH_SMALL:

            case ItemID.HUNTSMANS_KIT: // Fill, Empty, view(custom storage interface)
            case ItemID.TACKLE_BOX: // View, ??
                switch (menuOption.toLowerCase()) {
                    // These items act as long term storage, and are more like banks
                    // Items are not used directly from them either
                    case "empty":
                    case "fill":
                    case "use":
                        log.debug("Ignoring storage item interaction.");
                        // Ignore manual changes to container items as the items have not been lost
                        skipTickForProfitCalculation = true;
                }
        }

        // Half-volatile storage
        // Items that can change from pure storage to volatile storage by opening
        switch (event.getItemId()) {
            case ItemID.FISH_SACK_BARREL_CLOSED:
            case ItemID.FISH_BARREL_CLOSED: //Fill, open | empty
            case ItemID.GEM_BAG: //Fill, empty, open | empty
            case ItemID.SLAYER_HERB_SACK: // Fill, empty, open | empty

            case ItemID.SEED_BOX: // Open

            case ItemID.HG_MEATPOUCH_SMALL: // Fill, Empty
            case ItemID.HG_MEATPOUCH_LARGE: // Fill, Empty
            case ItemID.HG_FURPOUCH_SMALL: // Fill, Empty
            case ItemID.HG_FURPOUCH_MED: // Fill, Empty
            case ItemID.HG_FURPOUCH_LARGE: // Fill, Empty
                switch (menuOption.toLowerCase()) {
                    // These items act as long term storage, and are more like banks
                    // Items are not used directly from them either
                    case "empty":
                    case "fill":
                    case "use":
                        // Ignore manual changes to container items as the items have not been lost
                        skipTickForProfitCalculation = false;
                }
        }

        // If items can be used directly from the storage container, count the profit as otherwise we might not see
        // them get used, including interface only withdraws
        switch (event.getItemId()) {
            case ItemID.PLANK_SACK: // Fill, empty | use (dumps into inventory)
                // Items can be used directly from sack
                // Filling sack requires inventory as intermediate

            case ItemID.COAL_BAG: // Fill, empty, open | fill, empty
                //Coal can be directly used from the sack, and be filled directly from bank

                // case ItemID.BASKET: //Fill, remove-one, empty | fill | basket turns into different name like "Bananas(#)"
                //Empty sack
            case ItemID.XBOWS_BOLT_POUCH: //Open(remove interface) | | bolts can be worn via armor interface extra ammo slot
                //Rune pouch // Covered by withdraw interface

            case ItemID.FLAMTAER_BAG: // Fill, empty | empty (dumps into inventory) | items can be used directly from sack
            case ItemID.BOOKOFSCROLLS_CHARGED: // Interface with remove option | items can be used from book via activate and teleport
            case ItemID.BOOKOFSCROLLS_EMPTY: // Interface with remove option | items can be used from book via activate and teleport
            case ItemID.GNOMISH_FIRELIGHTER: // Check, uncharge | Firelighter charges used directly from box when burning logs
            case ItemID.FAVOUR_KEY_RING: //Add keys via use on ring | remove via remove interface

            case ItemID.LOG_BASKET_CLOSED: // Fill, Check(dialog based withdraw), Close/Open | Empty
            case ItemID.FORESTRY_KIT: // View(kit has withdraw interface), Fill | Use(dumps to bank) | rations used directly from kit
            case ItemID.FORESTRY_BASKET_CLOSED: // Fill, view(kit has withdraw interface/basket has none) | use (dumps to bank) | rations used directly from kit

            case ItemID.MM_SECONDARY_POUCH: // Fill, open, empty, use, check | Use (dumps to bank)
                switch (menuOption.toLowerCase()) {
                    // Interacting with these things pulls items from volatile unrecorded space
                    // Coal bag could be filled from bank to use in smithing, or filled from mining
                    // Price needs to be recorded while banking or otherwise
                    case "empty":
                    case "fill":
                    case "use":
                        // Ensure item containers
                        skipTickForProfitCalculation = false;
                }
        }

        // If items can be placed directly into the container, skipping inventory, track profit to not miss emptying
        switch (event.getItemId()) {
            case ItemID.FISH_SACK_BARREL_OPEN:
            case ItemID.FISH_BARREL_OPEN:

            //Coffins can only be emptied via configure, which doesn't report an ItemId we can see for emptying
            case ItemID.SHADES_COFFIN_BRONZE: // Fill, configure, open
            case ItemID.SHADES_COFFIN_BLACK:
            case ItemID.SHADES_COFFIN_STEEL:
            case ItemID.SHADES_COFFIN_SILVER:
            case ItemID.SHADES_COFFIN_GOLD:

            case ItemID.SHADES_COFFIN_BRONZE_OPEN:
            case ItemID.SHADES_COFFIN_BLACK_OPEN:
            case ItemID.SHADES_COFFIN_STEEL_OPEN:
            case ItemID.SHADES_COFFIN_SILVER_OPEN:
            case ItemID.SHADES_COFFIN_GOLD_OPEN: // Fill, configure, close

            case ItemID.GEM_BAG_OPEN: // Fill, Empty
            case ItemID.COAL_BAG_OPEN: // Fill, Empty

            case ItemID.SLAYER_HERB_SACK_OPEN:
            case ItemID.SEED_BOX_OPEN:

            case ItemID.LOG_BASKET_OPEN:
            case ItemID.FORESTRY_BASKET_OPEN:

            case ItemID.HG_MEATPOUCH_SMALL_OPEN: // Fill, Empty
            case ItemID.HG_MEATPOUCH_LARGE_OPEN: // Fill, Empty
            case ItemID.HG_FURPOUCH_SMALL_OPEN: // Fill, Empty
            case ItemID.HG_FURPOUCH_MED_OPEN: // Fill, Empty
            case ItemID.HG_FURPOUCH_LARGE_OPEN: // Fill, Empty

            case ItemID.LOOTING_BAG: // Open, Deposit(store interface) | view (deposit interface) | Commonly opened and closed

            case ItemID.MM_SECONDARY_POUCH_OPEN: // Reagent pouch
                switch (menuOption.toLowerCase()) {
                    // Interacting with these things pulls items from volatile unrecorded space
                    // Coal bag could be filled from bank to use in smithing, or filled from mining
                    // Price needs to be recorded while banking or otherwise
                    case "empty":
                    case "fill":
                    case "use":
                        // Ensure item containers
                        skipTickForProfitCalculation = false;
                }
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
