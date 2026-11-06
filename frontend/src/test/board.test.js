import { describe, expect, it } from 'vitest';
import { BASE_SLOTS, COLOR_START, HOME_COLUMN, SAFE_CELLS, TRACK, cellFor, globalIndex, stackOffset } from '../lib/board';

describe('board geometry', () => {
  it('has 52 distinct track cells inside the 15x15 grid', () => {
    expect(TRACK).toHaveLength(52);
    expect(new Set(TRACK.map((c) => c.join(','))).size).toBe(52);
    TRACK.forEach(([c, r]) => { expect(c).toBeGreaterThanOrEqual(0); expect(c).toBeLessThan(15); expect(r).toBeGreaterThanOrEqual(0); expect(r).toBeLessThan(15); });
  });

  it('consecutive track cells touch, with exactly four diagonal steps at the corners of the centre square', () => {
    let diagonals = 0;
    for (let i = 0; i < 52; i += 1) {
      const [a, b] = [TRACK[i], TRACK[(i + 1) % 52]];
      const dx = Math.abs(a[0] - b[0]);
      const dy = Math.abs(a[1] - b[1]);
      if (dx === 1 && dy === 1) diagonals += 1; else expect(dx + dy).toBe(1);
    }
    expect(diagonals).toBe(4);
  });

  it('maps relative steps to the global track using each colour start', () => {
    expect(globalIndex('RED', 0)).toBe(0);
    expect(globalIndex('GREEN', 0)).toBe(13);
    expect(globalIndex('YELLOW', 10)).toBe(36);
    expect(globalIndex('BLUE', 20)).toBe(7); // wraps past 51
  });

  it('places base tokens in the colour yard and finished tokens in the centre', () => {
    expect(cellFor('RED', -1, 2)).toEqual(BASE_SLOTS.RED[2]);
    expect(cellFor('BLUE', 56, 0)).toEqual([7, 7]);
  });

  it('home columns continue from the last track cell of each colour (step 50 -> 51)', () => {
    Object.keys(COLOR_START).forEach((color) => {
      const last = cellFor(color, 50);
      const first = cellFor(color, 51);
      expect(first).toEqual(HOME_COLUMN[color][0]);
      expect(Math.abs(last[0] - first[0]) + Math.abs(last[1] - first[1])).toBe(1);
      expect(cellFor(color, 55)).toEqual(HOME_COLUMN[color][4]);
    });
  });

  it('start squares of each colour are safe cells', () => {
    Object.values(COLOR_START).forEach((s) => expect(SAFE_CELLS).toContain(s));
  });

  it('stackOffset is zero for single tokens and spreads several', () => {
    expect(stackOffset(0, 1)).toEqual([0, 0]);
    const [a, b] = [stackOffset(0, 2), stackOffset(1, 2)];
    expect(a).not.toEqual(b);
  });
});

describe('home corners (dice + player panel placement)', () => {
  it('maps every colour to the corner of its own yard', async () => {
    const { homeCorner } = await import('../lib/board');
    expect(homeCorner('RED')).toEqual({ side: 'left', edge: 'top' });
    expect(homeCorner('GREEN')).toEqual({ side: 'right', edge: 'top' });
    expect(homeCorner('YELLOW')).toEqual({ side: 'right', edge: 'bottom' });
    expect(homeCorner('BLUE')).toEqual({ side: 'left', edge: 'bottom' });
    expect(homeCorner('PURPLE')).toBeNull();
  });

  it('the dice docks at a different spot for each colour, on that colour\'s side', async () => {
    const { diceDockStyle } = await import('../components/BoardStage');
    const docks = ['RED', 'GREEN', 'YELLOW', 'BLUE'].map(diceDockStyle);
    expect(new Set(docks.map((d) => JSON.stringify(d))).size).toBe(4);
    expect(diceDockStyle('RED').left).not.toMatch(/100%/); // left side
    expect(diceDockStyle('GREEN').left).toMatch(/100%/); // right side
    expect(diceDockStyle('YELLOW').top).toMatch(/100%/); // bottom strip
    expect(diceDockStyle('RED').top).not.toMatch(/100%/); // top strip
    expect(diceDockStyle(null)).toBeNull();
  });
});
