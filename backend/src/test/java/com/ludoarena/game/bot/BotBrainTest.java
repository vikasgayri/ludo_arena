package com.ludoarena.game.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludoarena.game.DiceService;
import com.ludoarena.game.LudoEngineService;
import com.ludoarena.game.LudoRulesService;
import com.ludoarena.game.TokenMovementService;
import com.ludoarena.game.TurnManagerService;
import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.Color;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.SeatInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotBrainTest {
    private LudoRulesService rules;
    private BotBrain brain;

    @BeforeEach
    void setUp() {
        rules = new LudoRulesService();
        brain = new BotBrain(rules).withRandom(new Random(7));
    }

    // ------------------------------------------------------------------ fixtures

    private static PlayerState player(String id, Color c, int... tokens) {
        PlayerState p = new PlayerState();
        p.userId = id;
        p.name = id;
        p.color = c;
        p.tokens = tokens.clone();
        return p;
    }

    /** Steps a token of colour {@code c} needs to stand on global track cell {@code global}. */
    private static int stepsAt(Color c, int global) {
        return Math.floorMod(global - c.startIndex(), Board.TRACK_LENGTH);
    }

    /** RED (the bot, to move) vs YELLOW, with the given dice already rolled. */
    private GameState redToMove(PlayerState red, PlayerState yellow, int dice) {
        GameState g = new GameState();
        g.players.add(red);
        g.players.add(yellow);
        g.turnIndex = 0;
        g.diceValue = dice;
        g.validMoves = rules.validMoves(red, dice);
        g.awaitingMove = !g.validMoves.isEmpty();
        return g;
    }

    private static final int B = Board.BASE;

    // ------------------------------------------------------------- strategy cases

    @Test
    void hardAndMediumCaptureWhenPossible() {
        PlayerState red = player("bot", Color.RED, 10, 30, B, B);
        PlayerState yellow = player("human", Color.YELLOW, stepsAt(Color.YELLOW, 14), B, B, B);
        GameState g = redToMove(red, yellow, 4); // token 0: 10 -> 14 captures; token 1: 30 -> 34 (safe star)

        assertEquals(0, brain.chooseMove(g, red, BotDifficulty.HARD));
        assertEquals(0, brain.chooseMove(g, red, BotDifficulty.MEDIUM));
    }

    @Test
    void mediumCapturesTheMostAdvancedVictim() {
        PlayerState red = player("bot", Color.RED, 26, 20, B, B);
        // yellow on global 30 has made 4 steps; yellow on global 24 has made 50 steps (about to turn home)
        PlayerState yellow = player("human", Color.YELLOW, stepsAt(Color.YELLOW, 30), stepsAt(Color.YELLOW, 24), B, B);
        GameState g = redToMove(red, yellow, 4); // token 0: 26 -> 30 hits the fresh one; token 1: 20 -> 24 hits the advanced one

        assertEquals(1, brain.chooseMove(g, red, BotDifficulty.MEDIUM));
    }

    @Test
    void preferSafeCellOverExposedCell() {
        PlayerState red = player("bot", Color.RED, 4, 20, B, B);
        // yellow token on global 19 can reach global 24 with a 5
        PlayerState yellow = player("human", Color.YELLOW, stepsAt(Color.YELLOW, 19), B, B, B);
        GameState g = redToMove(red, yellow, 4); // token 0: 4 -> 8 (star, safe); token 1: 20 -> 24 (exposed)

        assertEquals(0, brain.chooseMove(g, red, BotDifficulty.HARD));
        assertEquals(0, brain.chooseMove(g, red, BotDifficulty.MEDIUM));
    }

    @Test
    void hardRunsAwayFromAChaserWhileMediumJustAdvancesTheLeader() {
        PlayerState red = player("bot", Color.RED, 30, 40, B, B);
        // yellow token on global 27 threatens red token 0 (3 cells ahead)
        PlayerState yellow = player("human", Color.YELLOW, stepsAt(Color.YELLOW, 27), B, B, B);
        GameState g = redToMove(red, yellow, 5); // token 0: 30 -> 35 escapes; token 1: 40 -> 45

        assertEquals(0, brain.chooseMove(g, red, BotDifficulty.HARD));
        assertEquals(1, brain.chooseMove(g, red, BotDifficulty.MEDIUM));
    }

    @Test
    void finishingATokenBeatsPlainProgress() {
        PlayerState red = player("bot", Color.RED, 52, 10, B, B);
        PlayerState yellow = player("human", Color.YELLOW, B, B, B, B);
        GameState g = redToMove(red, yellow, 4); // 52 -> 56 HOME

        assertEquals(0, brain.chooseMove(g, red, BotDifficulty.HARD));
        assertEquals(0, brain.chooseMove(g, red, BotDifficulty.MEDIUM));
    }

    @Test
    void aSixUnlocksATokenFromBase() {
        PlayerState red = player("bot", Color.RED, B, 15, B, B);
        PlayerState yellow = player("human", Color.YELLOW, B, B, B, B);
        GameState g = redToMove(red, yellow, 6);

        int hard = brain.chooseMove(g, red, BotDifficulty.HARD);
        int medium = brain.chooseMove(g, red, BotDifficulty.MEDIUM);
        assertEquals(B, red.tokens[hard]);
        assertEquals(B, red.tokens[medium]);
    }

    @Test
    void threatsIgnoreSafeCellsHomeColumnAndOpponentsThatWouldTurnHome() {
        PlayerState red = player("bot", Color.RED, 8, 53, 20, B);
        PlayerState yellow = player("human", Color.YELLOW, 48, B, B, B); // yellow step 48 = global (26 + 48) % 52 = 22
        GameState g = redToMove(red, yellow, 1);

        assertEquals(0, brain.threats(g, red, 8));   // safe star
        assertEquals(0, brain.threats(g, red, 53));  // home column
        assertEquals(0, brain.threats(g, red, 20));  // behind the yellow token, not in front of it
        assertEquals(1, brain.threats(g, red, 24));  // global 24 is 2 cells ahead of yellow's 22 (48 + 2 <= 50)
        assertEquals(0, brain.threats(g, red, 28));  // 6 ahead would need yellow step 54: it turns into its home column
    }

    @Test
    void refusesWhenThereIsNothingToChooseOrItIsNotTheBotsTurn() {
        PlayerState red = player("bot", Color.RED, B, B, B, B);
        PlayerState yellow = player("human", Color.YELLOW, 10, B, B, B);
        GameState noMove = redToMove(red, yellow, 3);
        assertThrows(EngineException.class, () -> brain.chooseMove(noMove, red, BotDifficulty.HARD));

        PlayerState red2 = player("bot", Color.RED, 10, B, B, B);
        GameState g = redToMove(red2, yellow, 3);
        g.turnIndex = 1;
        assertThrows(EngineException.class, () -> brain.chooseMove(g, red2, BotDifficulty.EASY));
    }

    // ------------------------------------------------------------- legality fuzz

    @Test
    void everyDifficultyAlwaysReturnsALegalMoveOnRandomBoards() {
        Random r = new Random(42);
        int checked = 0;
        for (int i = 0; i < 10_000; i++) {
            PlayerState red = player("bot", Color.RED, randomTokens(r));
            PlayerState yellow = player("human", Color.YELLOW, randomTokens(r));
            GameState g = redToMove(red, yellow, 1 + r.nextInt(6));
            if (!g.awaitingMove) continue;
            for (BotDifficulty d : BotDifficulty.values()) {
                int idx = brain.chooseMove(g, red, d);
                assertTrue(g.validMoves.contains(idx), d + " chose illegal token " + idx);
            }
            checked++;
        }
        assertTrue(checked > 5_000, "fuzz should exercise many positions, got " + checked);
    }

    private static int[] randomTokens(Random r) {
        int[] t = new int[4];
        for (int i = 0; i < 4; i++) {
            int k = r.nextInt(10);
            t[i] = k == 0 ? B : k == 1 ? Board.HOME : r.nextInt(Board.HOME); // base, finished, or anywhere 0..55
        }
        return t;
    }

    // ---------------------------------------------------- full games through the engine

    /** Seeded dice so the simulations are reproducible; bots still never touch the dice themselves. */
    static final class SeededDice extends DiceService {
        private final Random r;

        SeededDice(long seed) {
            r = new Random(seed);
        }

        @Override
        public int roll() {
            return r.nextInt(6) + 1;
        }

        @Override
        public int pick(int bound) {
            return r.nextInt(bound);
        }
    }

    private static LudoEngineService engine(DiceService dice, LudoRulesService rules) {
        return new LudoEngineService(dice, rules, new TokenMovementService(rules), new TurnManagerService());
    }

    /** Plays a bot-only game to the end using only public engine entry points. Returns the finished state. */
    private GameState play(long seed, BotDifficulty... seats) {
        LudoEngineService engine = engine(new SeededDice(seed), rules);
        List<SeatInfo> infos = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) infos.add(new SeatInfo("bot" + i, "Bot " + i, null));
        GameState g = engine.startGame(infos, new GameRules(), 0L);
        for (int i = 0; i < seats.length; i++) {
            g.players.get(i).bot = true;
            g.players.get(i).botDifficulty = seats[i].name();
        }
        long now = 0;
        for (int step = 0; step < 20_000 && !g.isFinished(); step++) {
            PlayerState p = g.current();
            now += 1000;
            if (!g.awaitingMove) {
                engine.rollDice(g, p.userId, now);
            } else {
                engine.moveToken(g, p.userId, brain.chooseMove(g, p, BotDifficulty.parse(p.botDifficulty)), now);
            }
        }
        return g;
    }

    @Test
    void twoAndFourPlayerBotGamesAlwaysFinishWithAValidWinner() {
        for (long seed = 1; seed <= 30; seed++) {
            GameState two = play(seed, BotDifficulty.EASY, BotDifficulty.HARD);
            assertTrue(two.isFinished(), "2P game " + seed + " did not finish");
            assertEquals(Board.TOKENS_PER_PLAYER, two.player(two.winnerUserId).finishedTokens());

            GameState four = play(seed, BotDifficulty.EASY, BotDifficulty.MEDIUM, BotDifficulty.HARD, BotDifficulty.MEDIUM);
            assertTrue(four.isFinished(), "4P game " + seed + " did not finish");
            assertEquals(Board.TOKENS_PER_PLAYER, four.player(four.winnerUserId).finishedTokens());
        }
    }

    @Test
    void hardBeatsEasyClearlyOverManyGames() {
        int hardWins = 0;
        int games = 200;
        for (long seed = 1; seed <= games; seed++) {
            // alternate seats so first-move advantage does not bias the result
            boolean hardFirst = seed % 2 == 0;
            GameState g = hardFirst ? play(seed, BotDifficulty.HARD, BotDifficulty.EASY) : play(seed, BotDifficulty.EASY, BotDifficulty.HARD);
            String hardId = hardFirst ? "bot0" : "bot1";
            if (hardId.equals(g.winnerUserId)) hardWins++;
        }
        assertTrue(hardWins >= games * 0.65, "HARD should win clearly more often than EASY, won " + hardWins + "/" + games);
    }

    @Test
    void mediumBeatsEasyOverManyGames() {
        int mediumWins = 0;
        int games = 200;
        for (long seed = 1; seed <= games; seed++) {
            boolean mediumFirst = seed % 2 == 0;
            GameState g = mediumFirst ? play(seed, BotDifficulty.MEDIUM, BotDifficulty.EASY) : play(seed, BotDifficulty.EASY, BotDifficulty.MEDIUM);
            if ((mediumFirst ? "bot0" : "bot1").equals(g.winnerUserId)) mediumWins++;
        }
        assertTrue(mediumWins >= games * 0.55, "MEDIUM should beat EASY more often, won " + mediumWins + "/" + games);
    }
}
