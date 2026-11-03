package com.ludoarena.game;

import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.SeatInfo;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Test helpers: a dice source whose results are scripted so rule scenarios are deterministic. */
final class GameFixtures {
    private GameFixtures() {
    }

    static class ScriptedDice extends DiceService {
        final Deque<Integer> rolls = new ArrayDeque<>();
        int pickResult = 0;

        ScriptedDice with(int... values) {
            for (int v : values) rolls.addLast(v);
            return this;
        }

        @Override
        public int roll() {
            if (rolls.isEmpty()) throw new IllegalStateException("No scripted dice value left");
            return rolls.removeFirst();
        }

        @Override
        public int pick(int bound) {
            return pickResult;
        }
    }

    static LudoEngineService engine(DiceService dice) {
        LudoRulesService rules = new LudoRulesService();
        return new LudoEngineService(dice, rules, new TokenMovementService(rules), new TurnManagerService());
    }

    static GameState twoPlayers(LudoEngineService engine, GameRules rules) {
        return engine.startGame(List.of(new SeatInfo("u1", "Alice", null), new SeatInfo("u2", "Bob", null)), rules, 0L);
    }

    static GameState fourPlayers(LudoEngineService engine) {
        return engine.startGame(List.of(
                new SeatInfo("u1", "A", null), new SeatInfo("u2", "B", null),
                new SeatInfo("u3", "C", null), new SeatInfo("u4", "D", null)), new GameRules(), 0L);
    }
}
