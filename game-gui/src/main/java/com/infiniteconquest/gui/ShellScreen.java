package com.infiniteconquest.gui;

/** Lifecycle hook for shell screens so animated screens can pause when hidden. */
interface ShellScreen {
    default void onShow() { }
    default void onHide() { }
}
