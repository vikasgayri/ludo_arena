package com.ludoarena.game;

import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.MoveOutcome;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.TokenRef;
import java.util.List;
import org.springframework.stereotype.Service;

/** Applies a single legal token move, including captures. */
@Service
public class TokenMovementService {
    private final LudoRulesService rules;

    public TokenMovementService(LudoRulesService rules) {
        this.rules = rules;
    }

    public MoveOutcome apply(GameState game, PlayerState player, int tokenIndex, int dice) {
        if (tokenIndex < 0 || tokenIndex >= player.tokens.length) {
            throw new EngineException("INVALID_TOKEN", "Token index out of range");
        }
        int from = player.tokens[tokenIndex];
        if (!rules.canMove(from, dice)) {
            throw new EngineException("ILLEGAL_MOVE", "That token cannot move " + dice);
        }
        int to = rules.targetSteps(from, dice);
        player.tokens[tokenIndex] = to;
        List<TokenRef> captured = rules.captureTargets(game, player, to);
        for (TokenRef ref : captured) {
            game.player(ref.userId()).tokens[ref.tokenIndex()] = Board.BASE;
        }
        return new MoveOutcome(tokenIndex, from, to, captured, to == Board.HOME, from == Board.BASE);
    }
}
