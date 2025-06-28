package com.profittracker;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;

import java.io.File;
import java.nio.file.Files;

/**
 * Long term data storage for individual account profiles
 */
public class ProfitTrackerRecord {
    public String name;
    public RuneScapeProfileType rsProfileType;
    public long hash;
    public long startTickMillies;
    public long ticksOnline;
    public long profitAccumulated;
    public long depositDeficit;
    public ProfitTrackerPossessions startingPossessions;
    public ProfitTrackerPossessions currentPossessions;
    public Item[] lastPossessionChange;

    public static final File RECORD_DIRECTORY = new File(RuneLite.RUNELITE_DIR, "profit-tracker");

    public ProfitTrackerRecord(Client client){
        hash = client.getAccountHash();
        rsProfileType = RuneScapeProfileType.getCurrent(client);
        startTickMillies = System.currentTimeMillis();
        ticksOnline = 0;
        profitAccumulated = 0;
        depositDeficit = 0;
        startingPossessions = new ProfitTrackerPossessions();
        currentPossessions = new ProfitTrackerPossessions();
    }

    public void reset(ConfigManager configManager) {
        startTickMillies = System.currentTimeMillis();
        ticksOnline = 0;
        profitAccumulated = 0;
        startingPossessions = new ProfitTrackerPossessions();
    }

    public void updateInventoryItems(Item[] items){
        if (startingPossessions.inventoryItems == null) {
            startingPossessions.inventoryItems = items;
        }
        currentPossessions.inventoryItems = items;
    }

    public void updateBankItems(Item[] items){
        if (startingPossessions.bankItems == null) {
            startingPossessions.bankItems = items;
        }
        currentPossessions.bankItems = items;
    }

    public void updateGrandExchangeItems(Item[] items){
        if (startingPossessions.grandExchangeItems == null) {
            startingPossessions.grandExchangeItems = items;
        }
        currentPossessions.grandExchangeItems = items;
    }

    public String getAccountRecordKey(){
        return createAccountRecordKey(this.hash,this.rsProfileType.name());
    }

    private static String createAccountRecordKey(long accountHash, String rsProfileType) {
        if (accountHash == -1) {
            // Not logged in
            return null;
        }
        // Account for special worlds where a player with the same name might have different possessions
        // For example, a speedrunning world has MEMBER and SPEEDRUNNING types
        String accountIdentifier = Long.toString(accountHash) + "_" + rsProfileType;
        return "record_" + accountIdentifier;
    }

    public static String getAccountRecordKey(Client client){
        return createAccountRecordKey(client.getAccountHash(), RuneScapeProfileType.getCurrent(client).name());
    }

    private static File getAccountRecordFile(Client client){
        return new File(RECORD_DIRECTORY, getAccountRecordKey(client) + ".json");
    }

    private File getAccountRecordFile(){
        return new File(RECORD_DIRECTORY, getAccountRecordKey() + ".json");
    }

    /**
     * Loads the current accounts record from its expected file location
     * Returns null if anything fails
     */
    public static ProfitTrackerRecord load(Client client, ConfigManager configManager, Gson gson){
        String json;
        try {
            json = new String(Files.readAllBytes(getAccountRecordFile(client).toPath()));
        } catch(Exception e) {
            return null;
        }
        try {
            return gson.fromJson(json, ProfitTrackerRecord.class);
        } catch(Exception e) {
            // Likely failed to read, maybe the data was corrupted, or manually modified
            return null;
        }
    }

    /**
     * Saves the current account data into a json file by the name of the account hash + rs profile type
     */
    public void save(ConfigManager configManager, Gson gson){
        String json = gson.toJson(this);

        File accountFile = getAccountRecordFile();
        try {
            tryCreateRecordFolder();
            Files.write(accountFile.toPath(), json.getBytes());
        } catch(Exception ignored) {
        }
    }

    /**
     * Removes stored data for this item, for when users don't want to track across sessions
     */
    public void clear(ConfigManager configManager, ProfitTrackerConfig config){
        configManager.unsetConfiguration(ProfitTrackerConfig.GROUP,configManager.getRSProfileKey(),getAccountRecordKey());
    }

    private void tryCreateRecordFolder() {
        if (!RECORD_DIRECTORY.exists())
        {
            RECORD_DIRECTORY.mkdir();
        }
    }
}
