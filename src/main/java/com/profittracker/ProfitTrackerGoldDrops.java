package com.profittracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import java.awt.*;

import static net.runelite.api.ScriptID.XPDROPS_SETDROPSIZE;
import static net.runelite.api.ScriptID.XPDROP_DISABLED;

@Slf4j
public class ProfitTrackerGoldDrops {
    /*
       Implement gold drops.
       We do this by using the XPDrop mechanism, namely the Fake XPDrop script,
       which is intended to generate xp drops for maxed out skills.
       Fake XP Drops are composed of a skill sprite,
        and a text widget with a mod icon (<img=11> in text)
       So to create a gold drop, we create a fake xp drop, and interefere in the middle,
       and change the sprite and text to our liking.

       Flow is:

       1. create xp drop using runScript (see requestGoldDrop)
       2. getting in the middle of the drop, changing icon and text (see handleXpDrop)

       A more correct way to do this is probably by calling Item.GetImage with wanted
       coin quantity, which will give us correct coin icon and correct text,
       and simply drawing that image ourselfs somehow. Instead of using xp drop mechanism.
     */

    /*
    Free sprite id for the gold icons.
     */
    private static final int COINS_SPRITE_ID = -1337;

    // Skill ordinal to send in the fake xp drop script.
    // doesn't matter which skill expect it's better not be attack/defense/magic to avoid collision with
    // XpDropPlugin which looks for those and might change text color
    private static final int XPDROP_SKILL = Skill.FISHING.ordinal();

    // Value to send in the fake xp drop script. Doesn't matter at all
    // since we don't use this value, but we use currentGoldDropValue
    private static final int XPDROP_VALUE = 6;

    /*
    Singletons which will be provided at creation by the plugin
     */
    private final ItemManager itemManager;
    private final Client client;

    /* var currentGoldDropValue will have
    the gold value of the current ongoing gold drop. 2 purposes:
      1. to know the value later when we actually use it,
      2. to know to catch the next fake xpdrop in onScriptPreFired
    */
    private long currentGoldDropValue;

    ProfitTrackerGoldDrops(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;

        prepareCoinSprite();

        currentGoldDropValue = 0L;

    }

    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        /*
        We check for scripts of type XPDROPS_SETDROPSIZE to interfere with the XPdrop
        and write our own values
         */

        // is this current script type?
        if (scriptPreFired.getScriptId() != XPDROPS_SETDROPSIZE)
        {
            return;
        }

        // Get xpdrop widget id using the stack
        // taken from XpDropPlugin!

        // This runs prior to the proc being invoked, so the arguments are still on the stack.
        // Grab the first argument to the script.
        final int[] intStack = client.getIntStack();
        final int intStackSize = client.getIntStackSize();

        final int widgetId = intStack[intStackSize - 4];

        // extract information from currentGoldDropValue
        boolean isThisGoldDrop =   (currentGoldDropValue != 0);
        long     goldDropValue =     currentGoldDropValue;

        // done with this gold drop anyway
        currentGoldDropValue = 0;

        handleXpDrop(widgetId, isThisGoldDrop, goldDropValue);

    }

    private void handleXpDrop(int xpDropWidgetId, boolean isThisGoldDrop, long goldDropValue)
    {
        final Widget xpDropWidget;
        final Widget dropTextWidget;

        Widget[] xpDropWidgetChildren;

        // get widget from ID
        xpDropWidget = client.getWidget(InterfaceID.EXPERIENCE_TRACKER, xpDropWidgetId & 0xFFFF);

        if (xpDropWidget == null)
        {
            log.error("xpDropWidget was null");
            return;
        }

        xpDropWidgetChildren = xpDropWidget.getChildren();

        if (xpDropWidgetChildren.length < 1)
        {
            log.error(String.format("Unexpected xpDropWidgets length! %d", xpDropWidgetChildren.length));
            return;
        }

        dropTextWidget = xpDropWidgetChildren[0];

        if (isThisGoldDrop)
        {
            final Widget dropSpriteWidget;

            if (xpDropWidgetChildren.length < 2)
            {
                log.error(String.format(
                        "Unexpected xpDropWidgetChildren length for a gold drop! length! %d",
                        xpDropWidgetChildren.length));
                return;
            }

            dropSpriteWidget = xpDropWidgetChildren[1];

            xpDropToGoldDrop(dropTextWidget, dropSpriteWidget, goldDropValue);
        }
        else
        {
            // reset text color for all regular xpdrops
            resetXpDropTextColor(dropTextWidget);
        }


    }
    private void xpDropToGoldDrop(Widget dropTextWidget, Widget dropSpriteWidget, long goldDropValue)
    {
        /*
        Change xpdrop icon and text, to make a gold drop
         */


        dropTextWidget.setText(formatGoldDropText(goldDropValue));

        if (goldDropValue > 0)
        {
            // green text for profit
            dropTextWidget.setTextColor(Color.GREEN.getRGB());
        }
        else
        {
            // red for loss
            dropTextWidget.setTextColor(Color.RED.getRGB());
        }

        // change skill sprite to coin sprite
        dropSpriteWidget.setSpriteId(COINS_SPRITE_ID);

    }

    private void prepareCoinSprite()
    {
        /*
        Prepare coin sprites for use in the gold drops.
        It seems item icons are not available as sprites with id,
        so we convert in this function.

        */

        AsyncBufferedImage coin_image_raw;

        // get image object by coin item id
        coin_image_raw = itemManager.getImage(ItemID.COINS_995, 10000, false);

        // since getImage returns an AsyncBufferedImage, which is not loaded initially,
        // we schedule sprite conversion and sprite override for when the image is actually loaded
        coin_image_raw.onLoaded(() -> {
            final SpritePixels coin_sprite;

            // convert image to sprite
            coin_sprite = ImageUtil.getImageSpritePixels(coin_image_raw, client);

            // register new coin sprite by overriding a free sprite id
            client.getSpriteOverrides().put(COINS_SPRITE_ID, coin_sprite);
        });

    }

    public void requestGoldDrop(long amount)
    {
        /*
        We create gold drops by faking a fake xp drop :)
         */

        log.debug(String.format("goldDrop: %d", amount));

        // save the value and mark an ongoing gold drop
        currentGoldDropValue = amount;

        // Create a fake xp drop. the 2 last arguments don't matter:
        // 1. skill ordinal - we will replace the icon anyway
        // 2. value - since we want to be able to pass negative numbers, we pass the value using
        // currentGoldDropValue instead of this argument

        client.runScript(XPDROP_DISABLED, XPDROP_SKILL, XPDROP_VALUE);

    }

    private void resetXpDropTextColor(Widget xpDropTextWidget)
    {
        // taken from XpDropPlugin
        EnumComposition colorEnum = client.getEnum(EnumID.XPDROP_COLORS);
        int defaultColorId = client.getVarbitValue(Varbits.EXPERIENCE_DROP_COLOR);
        int color = colorEnum.getIntValue(defaultColorId);
        xpDropTextWidget.setTextColor(color);
    }

    private String formatGoldDropText(long goldDropValue)
    {
        // Format gold value to fit in xp drop to avoid being cutoff by gold sprite
        // 999
        // 1.0K
        // 20K
        // 300K
        // 1.0M

        float goldValueRep = goldDropValue;
        String suffix = "";
        boolean useDecimal = false;
        if (Math.abs(goldDropValue) < 1000L) { // 1-999
            return Long.toString(goldDropValue);
        }
        else if (Math.abs(goldDropValue) < 10000L) // 1,000-9,999
        {
            goldValueRep = (goldDropValue / 1000.0F);
            suffix = "K";
            useDecimal = true;
        }
        else if (Math.abs(goldDropValue) < 1000000L) // 10,000-999,999
        {
            goldValueRep = (goldDropValue / 1000.0F);
            suffix = "K";
        }
        else if (Math.abs(goldDropValue) < 10000000L) // 1,000,000-9,999,999
        {
            goldValueRep = (goldDropValue / 1000000.0F);
            suffix = "M";
            useDecimal = true;
        }
        else if (Math.abs(goldDropValue) < 1000000000L) // 10,000,000-999,999,999
        {
            goldValueRep = (goldDropValue / 1000000.0F);
            suffix = "M";
        }
        else if (Math.abs(goldDropValue) < 1000000000000L) // 1,000,000,000+
        {
            goldValueRep = (goldDropValue / 1000000000.0F);
            suffix = "B";
            useDecimal = true;
        }
        else
        {
            return "ALOT";
        }
        if(useDecimal)
        {
            return String.format("%.1f%s", Math.floor(goldValueRep * 10) / 10, suffix);
        }else{
            return String.format("%.0f%s", Math.floor(goldValueRep * 10) / 10, suffix);
        }
    }
}
