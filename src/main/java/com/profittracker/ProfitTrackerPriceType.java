package com.profittracker;

public enum ProfitTrackerPriceType {
    GE("Grand Exchange"),
    GE_TAXED("Grand Exchange (2% Tax)"), //98% rounded up
    HIGH_ALCH("High Alchemy / Shop 60%"), //60%, wildy shop, rogues den
    SHOP_SPECIAL("Shop 55%"), //55%, west ardy, pollnivneach, legends' guild general stores, moon clan, lighthouse
    LOW_ALCH("Low Alchemy / Shop 40%"), //40%, regular general stores
    SHOP_OVERSTOCK("Shop 10% (Overstocked)"); //10%

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
