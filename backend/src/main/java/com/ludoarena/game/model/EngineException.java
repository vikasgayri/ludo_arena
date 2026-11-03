package com.ludoarena.game.model;

/** Thrown when an action is rejected by the rules (wrong turn, illegal move, finished game, ...). */
public class EngineException extends RuntimeException {
    private final String code;

    public EngineException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
