package com.profittracker;

import lombok.Value;

import java.awt.image.BufferedImage;

@Value
public class ProfitTrackerPanelItem
{
    int itemId;
    String name;
    int quantity;
    long profitChange;
    BufferedImage image;
}
