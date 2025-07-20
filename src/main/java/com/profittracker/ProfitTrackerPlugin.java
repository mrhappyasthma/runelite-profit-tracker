package com.profittracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import net.runelite.api.events.*;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.VarbitChanged;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
        name = "Profit Tracker",
        description = "Tracks profit according to the value of your items.",
        tags = {"overlay"}
)
public class ProfitTrackerPlugin extends Plugin
{
    ProfitTrackerGoldDrops goldDropsObject;
    ProfitTrackerInventoryValue inventoryValueObject;

    // the profit will be calculated against this value
    private long prevInventoryValue;
    // Holds session data for the current account
    private ProfitTrackerRecord accountRecord;
    private String previousAccount;
    // Collection of items that was last used to calculate value, includes inventory and equipment
    private long totalProfit;

    private long startTickMillis;
    private long activeTicks;

    private boolean skipTickForProfitCalculation;
    private boolean inventoryValueChanged;
    private boolean bankValueChanged;
    private boolean grandExchangeValueChanged;
    private boolean inProfitTrackSession;
    private boolean runePouchContentsChanged;
    // State boolean for when a widget we do not fully track is currently opened, such as the leprechaun tool store
    private boolean untrackedStorageOpened;
    // Remembers the state of bank interface
    private boolean bankOpened;
    // Remembers the state of grand exchange
    private boolean grandExchangeOpened;
    // Set when using a deposit menu option. Used to create a depositing deficit for the next time you open bank
    // This ensures using a deposit box doesn't spam coin drops, but also doesn't get out of sync when the race
    // condition with menu options and container changes causes some anyway
    private boolean depositingItem;
    // State of a deposit box being open, used to avoid tracking profit changes when just sending to the bank
    private boolean depositBoxOpened;
    // Tracks the last widget we care about that closed, like bank, GE, other storage
    private int closingWidgetId;
    // Tracks when an event causes an item to be stored to an untracked location, like using an item on a tackle box
    private boolean depositingUntrackedItem;

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

    @Inject
    private ConfigManager configManager;

    @Inject
    private Gson gson;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ChatboxPanelManager chatboxPanelManager;

    @Override
    protected void startUp() throws Exception
    {
        // Add the inventory overlay
        overlayManager.add(overlay);

        goldDropsObject = new ProfitTrackerGoldDrops(client, itemManager, config);

        inventoryValueObject = new ProfitTrackerInventoryValue(client, itemManager, config);

        initializeVariables();
    }

    private void initializeVariables()
    {
        inventoryValueObject.setOffers(null);

        // profit begins at 0 of course
        totalProfit = 0;

        // this will be filled with actual information in startProfitTrackingSession
        startTickMillis = 0;
        activeTicks = 0;

        // skip profit calculation for first tick, to initialize first inventory value
        skipTickForProfitCalculation = true;

        inventoryValueChanged = false;

        bankValueChanged = false;

        grandExchangeValueChanged = false;

        inProfitTrackSession = false;

        runePouchContentsChanged = false;

        depositingItem = false;
    }

    private void startProfitTrackingSession()
    {
        /*
        Start tracking profit from now on
         */

        initializeVariables();

        // initialize timer
        startTickMillis = System.currentTimeMillis();

        activeTicks = 0;

        overlay.updateStartTimeMillies(startTickMillis);

        overlay.updateActiveTicks(activeTicks);

        overlay.updateProfitValue(totalProfit);

        overlay.startSession();

        inProfitTrackSession = true;
    }

    public void resetSession(boolean hardReset){
        initializeVariables();
        startProfitTrackingSession();
        inventoryValueChanged = true;
        if (accountRecord != null) {
            accountRecord.reset(configManager, hardReset);
            accountRecord.save(gson);
            overlay.updateBankStatus(accountRecord);
        }
    }

    /**
     * Creates an entry for the current login if needed.
     * Hooks up the possessions collection for repeat use for the current login.
     */
    private void checkAccount()
    {
        String accountIdentifier = ProfitTrackerRecord.getAccountRecordKey(client);
        if (accountIdentifier == null) {
            return;
        }
        boolean changedAccounts = previousAccount != null && ! previousAccount.contentEquals(accountIdentifier);

        if (previousAccount != null && changedAccounts) {
            // Changed account, save the items we know about
            accountRecord.save(gson);
            accountRecord = null;
        }

        //configManager.getRSProfileConfigurationKeys(ProfitTrackerConfig.GROUP,configManager.getRSProfileKey(),"record_");
        if (accountRecord == null) {
            // Check for existing record
            ProfitTrackerRecord record = ProfitTrackerRecord.load(client, configManager, gson);
            if (! config.rememberProfit() && record != null) {
                record.reset(configManager, true);
            }

            if (record == null) {
                // Create a new one if not found
                accountRecord = new ProfitTrackerRecord(client);
            } else {
                accountRecord = record;
            }
        }

        totalProfit = accountRecord.profitAccumulated;
        activeTicks = accountRecord.ticksOnline;
        startTickMillis = accountRecord.startTickMillies;

        overlay.updateProfitValue(totalProfit);
        overlay.updateStartTimeMillies(startTickMillis);
        overlay.updateActiveTicks(activeTicks);

        overlay.updateBankStatus(accountRecord);

        previousAccount = accountIdentifier;
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
    {
        checkAccount();
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
            if (accountRecord != null){
                overlay.startSession();
                inProfitTrackSession = true;
                inventoryValueChanged = true;
                // Active ticks will only be 0 if toggling the plugin
                if (activeTicks == 0) {
                    resetSession(true);
                }
            } else {
                return;
            }
        }

        if (accountRecord.name == null && client.getGameState() == GameState.LOGGED_IN) {
            accountRecord.name = client.getLocalPlayer().getName();
        }

        activeTicks += 1;
        accountRecord.ticksOnline = activeTicks;
        overlay.updateActiveTicks(activeTicks);

        if (inventoryValueChanged || runePouchContentsChanged || bankValueChanged || grandExchangeValueChanged)
        {
            tickProfit = calculateProfit();

            // Generate gold drop only based on instantaneous profit, to avoid scaring users during GE adjustment ticks
            if (config.goldDrops() && tickProfit != 0)
            {
                goldDropsObject.requestGoldDrop(tickProfit);
            }

            totalProfit += tickProfit;

            if (tickProfit != 0) {
                // This causes total profit to only update when we profit off something.
                // While this may cause temporary inaccuracy when GE prices change, it prevents excessive calculations
                // every time an item is moved, equipped, deposited, etc.
                totalProfit = inventoryValueObject.calculateItemValue(accountRecord.itemDifferenceAccumulated);
            }

            accountRecord.profitAccumulated = totalProfit;
            overlay.updateProfitValue(totalProfit);

            inventoryValueChanged = false;
            bankValueChanged = false;
            runePouchContentsChanged = false;
            grandExchangeValueChanged = false;
            depositingItem = false;
        }
        resetWidgetClosedVariables();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        boolean isStorage = true;
        switch (event.getGroupId()) {
            case InterfaceID.BANKMAIN:
                // Bank contents will be null if the bank has no items when first logging in
                if (inventoryValueObject.getBankContents() == null && accountRecord.currentPossessions.bankItems == null) {
                    accountRecord.updateBankItems(new Item[0]);
                    overlay.updateBankStatus(accountRecord);
                }
                bankOpened = true;
                break;
            case InterfaceID.GE_COLLECT:
            case InterfaceID.GE_OFFERS:
                inventoryValueObject.setOffers(client.getGrandExchangeOffers());
                grandExchangeOpened = true;
                break;
            case InterfaceID.BANK_DEPOSIT_IMP:
            case InterfaceID.BANK_DEPOSITBOX:
                depositBoxOpened = true;
                break;
            case InterfaceID.HUNTSMANS_KIT:
            case InterfaceID.SEED_VAULT:
            case InterfaceID.TACKLE_BOX_MAIN:
            case InterfaceID.FARMING_TOOLS:
                untrackedStorageOpened = true;
                break;
            default:
                isStorage = false;
                break;
        }
        if (isStorage) {
            // If a user is flipping through multiple storages tick after tick, and moving items in/out
            // tracking can get complicated.
            // So we reset immediately to avoid longer term desyncs, like jumping between GE and bank
            resetWidgetClosedVariables();
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        // Widget closes immediately, but items can still transfer between containers the next tick
        // So actually flagging them is done at the end of the next tick, we just set a variable here to look for later
        switch (event.getGroupId()) {
            case InterfaceID.BANKMAIN:
            case InterfaceID.HUNTSMANS_KIT:
            case InterfaceID.SEED_VAULT:
            case InterfaceID.TACKLE_BOX_MAIN:
            case InterfaceID.FARMING_TOOLS:
            case InterfaceID.GE_COLLECT:
            case InterfaceID.GE_OFFERS:
            case InterfaceID.BANK_DEPOSIT_IMP:
            case InterfaceID.BANK_DEPOSITBOX:
                closingWidgetId = event.getGroupId();
                break;
        }
    }

    /**
     * Clears the associated variable for the last opened storage widget
     * Should be called with closingWidgetId
     */
    private void resetWidgetClosedVariables(){
        if (closingWidgetId != 0) {
            switch (closingWidgetId) {
                case InterfaceID.BANKMAIN:
                    bankOpened = false;
                    break;
                case InterfaceID.HUNTSMANS_KIT:
                case InterfaceID.SEED_VAULT:
                case InterfaceID.TACKLE_BOX_MAIN:
                case InterfaceID.FARMING_TOOLS:
                    untrackedStorageOpened = false;
                    break;
                case InterfaceID.GE_COLLECT:
                case InterfaceID.GE_OFFERS:
                    grandExchangeOpened = false;
                    break;
                case InterfaceID.BANK_DEPOSIT_IMP:
                case InterfaceID.BANK_DEPOSITBOX:
                    depositBoxOpened = false;
                    break;
            }
            closingWidgetId = 0;
        }
    }

    private long calculateProfit()
    {
        /*
        Calculate and return the profit for this tick
        if skipTickForProfitCalculation is set, meaning this tick was bank / deposit
        so return 0
         */
        ProfitTrackerPossessions newPossessions = new ProfitTrackerPossessions();
        newPossessions.grandExchangeItems = null;
        long newProfit = 0;
        Item[] possessionDifference = null;

        // calculate current inventory value
        newPossessions.inventoryItems = inventoryValueObject.getInventoryAndEquipmentContents();
        newPossessions.bankItems = inventoryValueObject.getBankContents();
        if (grandExchangeValueChanged) {
            newPossessions.grandExchangeItems = inventoryValueObject.getGrandExchangeContents();
        }
        accountRecord.currentPossessions.fillNullItems(newPossessions);
        newPossessions.fillNullItems(accountRecord.currentPossessions);
        Item[] newItems = newPossessions.getItems();

        if (!skipTickForProfitCalculation && accountRecord.currentPossessions.inventoryItems != null && newItems != null)
        {
            // calculate new profit
            possessionDifference = inventoryValueObject.getItemCollectionDifference(accountRecord.currentPossessions.getItems(), newItems, config.estimateUntradeables());
            newProfit = inventoryValueObject.calculateItemValue(possessionDifference);

            log.debug("Calculated " + newProfit + " profit for " + (possessionDifference.length) + " item changes.");
        }
        else
        {
            /* first time calculation / banking / equipping */
            log.debug("Skipping profit calculation!");

            skipTickForProfitCalculation = false;
        }

        Item[] rawPossessionDifference = new Item[0];
        if (accountRecord.currentPossessions.getItems() != null) {
            rawPossessionDifference = ProfitTrackerInventoryValue.getItemCollectionDifference(accountRecord.currentPossessions.getItems(), newItems);
        }
        if (rawPossessionDifference.length > 0) {
            // This block generally checks for possessions changing when they shouldn't be, often when closing storage the same tick as withdraw/depositing
            // Otherwise, just records the last change seen
            boolean bankingItemsWithoutWidget = (bankOpened || depositingItem || depositBoxOpened) && inventoryValueObject.getBankContents() == null && !untrackedStorageOpened;
            // If bank/deposit box/depositing flag, any lost items are in bank, any gained items came from bank
            if (bankingItemsWithoutWidget) {
                depositingItem = false;
                Item[] bankChange = ProfitTrackerInventoryValue.getItemCollectionDifference(rawPossessionDifference, new Item[0]);
                if (accountRecord.currentPossessions.bankItems != null) {
                    newPossessions.bankItems = ProfitTrackerInventoryValue.getItemCollectionSum(accountRecord.currentPossessions.bankItems, bankChange);
                } else {
                    // Bank yet to be opened, can't deposit anything, as then it would be like it originally had almost nothing
                    accountRecord.updateItems(newPossessions, overlay);
                    return 0;
                }
            }
            // If ge opened, gained items pull from ge, items banked will cause temporary desync
            if (grandExchangeOpened && !grandExchangeValueChanged) {
                Item[] grandExchangeChange = ProfitTrackerInventoryValue.getItemCollectionDifference(rawPossessionDifference, new Item[0]);
                newPossessions.grandExchangeItems = ProfitTrackerInventoryValue.getItemCollectionSum(accountRecord.currentPossessions.grandExchangeItems, grandExchangeChange);
            }
            // If untracked storage, move lost items to untracked storage, add gained items to old record
            if (untrackedStorageOpened || depositingUntrackedItem) {
                depositingUntrackedItem = false;
                Item[] untrackedStorageChange = ProfitTrackerInventoryValue.getItemCollectionDifference(rawPossessionDifference, new Item[0]);
                newPossessions.untrackedStorageItems = ProfitTrackerInventoryValue.getItemCollectionSum(newPossessions.untrackedStorageItems, untrackedStorageChange);
                // If we go into the negatives, that means untrackedStorage originally had more items in it
                Item[] missingItems = ProfitTrackerInventoryValue.getItemCollectionGain(ProfitTrackerInventoryValue.getItemCollectionDifference(newPossessions.untrackedStorageItems, new Item[0]));
                // Ensure starting possessions has at least as many as were withdrawn
                accountRecord.startingPossessions.untrackedStorageItems = ProfitTrackerInventoryValue.getItemCollectionSum(accountRecord.startingPossessions.untrackedStorageItems, missingItems);
                accountRecord.currentPossessions.untrackedStorageItems = ProfitTrackerInventoryValue.getItemCollectionSum(accountRecord.currentPossessions.untrackedStorageItems, missingItems);
                newPossessions.untrackedStorageItems = ProfitTrackerInventoryValue.getItemCollectionSum(newPossessions.untrackedStorageItems, missingItems);
            }

            newItems = newPossessions.getItems();
            // This should always be empty in the event of a storage being opened
            rawPossessionDifference = ProfitTrackerInventoryValue.getItemCollectionDifference(accountRecord.currentPossessions.getItems(), newItems);
            if (rawPossessionDifference.length > 0) {
                accountRecord.lastPossessionChange = rawPossessionDifference;
                accountRecord.itemDifferenceAccumulated = ProfitTrackerInventoryValue.getItemCollectionSum(accountRecord.itemDifferenceAccumulated, rawPossessionDifference);
            } else {
                newProfit = 0;
            }
        }

        // update prevInventoryValue for future calculations anyway!
        //prevInventoryValue = newInventoryValue;
        accountRecord.updateItems(newPossessions, overlay);

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
            // Inventory has changed - need calculate profit in onGameTick
            inventoryValueChanged = true;
        }

        if (containerId == InventoryID.BANK) {
            bankValueChanged = true;
        }

        // No container event occurs for the GE collection item containers, but inventory does
        if (grandExchangeOpened && closingWidgetId != InterfaceID.GE_OFFERS && closingWidgetId != InterfaceID.GE_COLLECT) {
            inventoryValueObject.setOffers(client.getGrandExchangeOffers());
            grandExchangeValueChanged = true;
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        if (grandExchangeOpened && closingWidgetId != InterfaceID.GE_OFFERS && closingWidgetId != InterfaceID.GE_COLLECT){
            inventoryValueObject.setOffers(client.getGrandExchangeOffers());
            grandExchangeValueChanged = true;
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

        String[] collectionMenuOptions = {"Collect to bank", "Bank"};
        for (String collectionMenuOption : collectionMenuOptions) {
            if (menuOption.startsWith(collectionMenuOption) && grandExchangeOpened) {
                depositingItem = true;
                break;
            }
        }

        // Imp in a box acts like a deposit box, both via interface, and use interaction
        if (menuOption.startsWith("Use") && event.getMenuTarget().contains("Imp-in-a-box(")){
            depositingItem = true;
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
                        depositingUntrackedItem = true;
                }
        }

        // Half-volatile storage
        // Items that can change from pure storage to volatile storage by opening
        switch (event.getItemId()) {
            case ItemID.FISH_SACK_BARREL_CLOSED:
            case ItemID.FISH_BARREL_CLOSED: //Fill, open | empty
            case ItemID.GEM_BAG: //Fill, empty, open | empty
            case ItemID.SLAYER_HERB_SACK: // Fill, empty, open | empty

            case ItemID.SEED_BOX: // Fill, Empty, Check, Open

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
            case ItemID.SEED_BOX_OPEN: //Fill, Empty, Check, Close

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

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        goldDropsObject.onScriptPostFired(event);
    }

    @Subscribe
    public void onClientShutdown(ClientShutdown event)
    {
        accountRecord.save(gson);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged)
    {
        // Allows hot swapping between price calculation methods non-destructively
        if (configChanged.getGroup().equals(ProfitTrackerConfig.GROUP)) {
            clientThread.invoke(this::updateProfitUI);
            clientThread.invoke(() -> {
                if (config.goldDrops()) {
                    String[] goldDropVisuals = {"color", "style", "drop"};
                    for (String containerMenuOption : goldDropVisuals) {
                        if (configChanged.getKey().toLowerCase().contains(containerMenuOption)) {
                            // Visual indicator to user when they mess with settings, so they can see
                            goldDropsObject.requestGoldDrop(0);
                            break;
                        }
                    }
                }
            });
        }
    }

    private void updateProfitUI(){
        if (accountRecord != null) {
            totalProfit = inventoryValueObject.calculateItemValue(accountRecord.itemDifferenceAccumulated);
            accountRecord.profitAccumulated = totalProfit;
            overlay.updateProfitValue(totalProfit);
        }
    }

    public void adjustProfit()
    {
        String lastChange = accountRecord.lastPossessionChange != null ? Long.toString(-inventoryValueObject.calculateItemValue(accountRecord.lastPossessionChange)) : "";
        ChatboxTextInput chatInput = chatboxPanelManager.openTextInput("Adjust profit")
                .value(lastChange);
        chatInput
                .onChanged((input) -> {
                    // Ensure numeric while user is typing
                    Pattern pattern = Pattern.compile("-?\\d*[kmb]?", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(input);
                    matcher.find();
                    chatInput.value(matcher.toMatchResult().group(0));
                })
                .onDone((input) -> {
                    // Support values like 10k
                    input = input.toLowerCase().replace("b", "000000000");
                    input = input.replace("m", "000000");
                    input = input.replace("k", "000");
                    int adjustment = Integer.parseInt(input);
                    Item[] coinsAdjustment = new Item[] {new Item(ItemID.COINS, adjustment)};
                    accountRecord.itemDifferenceAccumulated = ProfitTrackerInventoryValue.getItemCollectionSum(accountRecord.itemDifferenceAccumulated, coinsAdjustment);
                    accountRecord.profitAccumulated += adjustment;
                    accountRecord.lastPossessionChange = coinsAdjustment;
                    updateProfitUI();
                    clientThread.invoke(() -> {
                            goldDropsObject.requestGoldDrop(adjustment);
                    });
                })
                .build();
    }
}