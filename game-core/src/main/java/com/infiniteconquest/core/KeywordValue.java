package com.infiniteconquest.core;

/** Range in board spaces and the strength of a development keyword. */
public record KeywordValue(int range, int amount) {
    public KeywordValue {
        if(range<0 || range>12 || amount<1 || amount>100) throw new IllegalArgumentException("Keyword range must be 0–12 and amount 1–100");
    }
}
