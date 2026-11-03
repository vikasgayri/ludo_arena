package com.ludoarena.game.model;

/** Player colours in clockwise board order. startIndex = global track cell where the colour enters. */
public enum Color {
    RED(0), GREEN(13), YELLOW(26), BLUE(39);

    private final int startIndex;

    Color(int startIndex) {
        this.startIndex = startIndex;
    }

    public int startIndex() {
        return startIndex;
    }
}
