package com.infiniteconquest.data;

@FunctionalInterface
public interface KeywordEffect<C> {
    void apply(C context);
}
