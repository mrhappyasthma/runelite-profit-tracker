 <img src="https://oldschool.runescape.wiki/images/Coins_detail.png?404bc" width="200" title="Coins 10000+">
 
 
# Profit Tracker Plugin
NOTE: I've stopped working on this, I will probably not fix much of the issues. People are welcome to fork or use the code.


This runelite plugin tracks the profit you are generating, according to GE, while money-making.
![image](https://user-images.githubusercontent.com/8212109/94357201-5d4c1780-009f-11eb-9c73-17c279edd613.png)

For example, if you are filling vials, the plugin will accumlate profit each time you fill vial, accounting for empty vials price in GE.
Depositing or withdrawing items will not affect profit value.


# Gold drops
Every change in your inventory and bank is monitored and a corresponding profit animation will be shown.
![image](https://user-images.githubusercontent.com/8212109/94357070-393c0680-009e-11eb-96a1-8fa7469ee6e1.png)

For example, if you buy in a general shop, an item for 20 coins, which is worth in GE 220 coins,
ProfitTracker will generate a gold drop animation of 200 coins.

# How to use
The plugin will begin tracking when entering the game. Be sure to reload the plugin when you are starting a new money routine!

Certain actions will not be recorded as profit unless the bank has been opened at least once to create a baseline, and again to see changes. Things like using a deposit box to empty storage pouches, or banking items directly from reward chests. So make sure to open up the bank once for better accuracy before doing those things.

# Running the plugin from repo
Clone the repo, and run ProfitTrackerTest java class from Intellij.

# Missing features
I've developed this while being F2P.
There is no tracking of member stuff like tridents, dwarf cannon.

# Credits
Credit to wikiworm (Brandon Ripley) for his runelite plugin
https://github.com/wikiworm/InventoryValue
which helped for the creation of this plugin!