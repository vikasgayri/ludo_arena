package com.ludoarena.game;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;

/** The only source of randomness in the game. Dice are generated server-side with SecureRandom. */
@Service
public class DiceService {
    private final SecureRandom random = new SecureRandom();

    /** @return an integer 1..6 */
    public int roll() {
        return random.nextInt(6) + 1;
    }

    /** @return an integer in [0, bound) */
    public int pick(int bound) {
        return random.nextInt(bound);
    }
}
