package com.ludoarena.game.model;

import java.util.List;

public record MoveOutcome(int tokenIndex, int from, int to, List<TokenRef> captured, boolean reachedHome, boolean leftBase) {
}
