package com.profittracker;

public enum ProfitTrackerPriceType {
    GE("Grand Exchange"),
    GE_TAXED("Grand Exchange (2% Tax)"), //98% rounded up
    HIGH_ALCH("High Alchemy / Shop++"), //60%, wildy shop, rogues den
    SHOP_SPECIAL("Shop+"), //55%, west ardy, pollnivneach, legends' guild general stores
    LOW_ALCH("Low Alchemy / Shop"), //40%, regular general stores
    SHOP_OVERSTOCK("Shop (Overstocked)"); //10%

    private final String name;

    ProfitTrackerPriceType(String s) {
        name = s;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
