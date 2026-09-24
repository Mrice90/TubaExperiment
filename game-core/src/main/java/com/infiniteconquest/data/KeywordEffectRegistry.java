package com.infiniteconquest.data;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class KeywordEffectRegistry<C> {
    private final Map<Keyword, KeywordEffect<C>> handlers = new EnumMap<>(Keyword.class);

    public void register(Keyword keyword, KeywordEffect<C> handler) {
        Objects.requireNonNull(keyword, "keyword");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(keyword, handler) != null) {
            throw new IllegalStateException("Keyword already registered: " + keyword);
        }
    }

    public KeywordEffect<C> require(Keyword keyword) {
        KeywordEffect<C> handler = handlers.get(Objects.requireNonNull(keyword, "keyword"));
        if (handler == null) throw new IllegalStateException("No effect registered for keyword: " + keyword);
        return handler;
    }

    public boolean supports(Keyword keyword) {
        return handlers.containsKey(keyword);
    }
}
