package com.ludoarena.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiceServiceTest {
    private final DiceService dice = new DiceService();

    @Test
    void rollsAreAlwaysBetweenOneAndSix() {
        for (int i = 0; i < 10_000; i++) {
            int v = dice.roll();
            assertTrue(v >= 1 && v <= 6, "dice out of range: " + v);
        }
    }

    @Test
    void everyFaceAppearsWithRoughlyEqualFrequency() {
        int[] counts = new int[7];
        int n = 6_000;
        for (int i = 0; i < n; i++) counts[dice.roll()]++;
        for (int face = 1; face <= 6; face++) {
            // expected 1000, std-dev ~29: a +/-200 window is ~7 sigma
            assertTrue(counts[face] > 800 && counts[face] < 1200, "face " + face + " count " + counts[face]);
        }
        assertEquals(n, counts[1] + counts[2] + counts[3] + counts[4] + counts[5] + counts[6]);
    }

    @Test
    void pickStaysInsideBound() {
        for (int i = 0; i < 1_000; i++) {
            int v = dice.pick(4);
            assertTrue(v >= 0 && v < 4);
        }
    }
}
