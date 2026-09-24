package com.infiniteconquest.core;

public record ActionResult(boolean accepted, String message) {
    public static ActionResult accepted(String message) { return new ActionResult(true, message); }
    public static ActionResult rejected(String message) { return new ActionResult(false, message); }
}
