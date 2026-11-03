// Pure geometry for the 15x15 Ludo board. Coordinates are [col,row]. This file contains NO game rules:
// legal moves always come from the server (validMoves). It only maps (color, relativeSteps) -> a cell to draw.

export const COLORS = ['RED', 'GREEN', 'YELLOW', 'BLUE'];
export const COLOR_START = { RED: 0, GREEN: 13, YELLOW: 26, BLUE: 39 };
export const SAFE_CELLS = [0, 8, 13, 21, 26, 34, 39, 47];
export const HOME_STEPS = 56;

// 52 main-track cells in clockwise order starting at RED's start square.
export const TRACK = [
  [1, 6], [2, 6], [3, 6], [4, 6], [5, 6],
  [6, 5], [6, 4], [6, 3], [6, 2], [6, 1], [6, 0],
  [7, 0], [8, 0],
  [8, 1], [8, 2], [8, 3], [8, 4], [8, 5],
  [9, 6], [10, 6], [11, 6], [12, 6], [13, 6], [14, 6],
  [14, 7], [14, 8],
  [13, 8], [12, 8], [11, 8], [10, 8], [9, 8],
  [8, 9], [8, 10], [8, 11], [8, 12], [8, 13], [8, 14],
  [7, 14], [6, 14],
  [6, 13], [6, 12], [6, 11], [6, 10], [6, 9],
  [5, 8], [4, 8], [3, 8], [2, 8], [1, 8], [0, 8],
  [0, 7], [0, 6],
];

export const HOME_COLUMN = {
  RED: [[1, 7], [2, 7], [3, 7], [4, 7], [5, 7]],
  GREEN: [[7, 1], [7, 2], [7, 3], [7, 4], [7, 5]],
  YELLOW: [[13, 7], [12, 7], [11, 7], [10, 7], [9, 7]],
  BLUE: [[7, 13], [7, 12], [7, 11], [7, 10], [7, 9]],
};

export const BASE_SLOTS = {
  RED: [[2, 2], [4, 2], [2, 4], [4, 4]],
  GREEN: [[10, 2], [12, 2], [10, 4], [12, 4]],
  YELLOW: [[10, 10], [12, 10], [10, 12], [12, 12]],
  BLUE: [[2, 10], [4, 10], [2, 12], [4, 12]],
};

export const BASE_AREA = {
  RED: [0, 0], GREEN: [9, 0], YELLOW: [9, 9], BLUE: [0, 9],
};

export const CENTER = [7, 7];

/** Absolute track index (0..51) for a token on the main track. */
export function globalIndex(color, steps) {
  return (COLOR_START[color] + steps) % 52;
}

/**
 * Cell [col,row] for a token. steps: -1 base, 0..50 main track, 51..55 home column, 56 finished.
 * tokenIndex selects the base slot / finished spot.
 */
export function cellFor(color, steps, tokenIndex = 0) {
  if (steps < 0) return BASE_SLOTS[color][tokenIndex];
  if (steps <= 50) return TRACK[globalIndex(color, steps)];
  if (steps <= 55) return HOME_COLUMN[color][steps - 51];
  return CENTER;
}

/** Slight offset so several tokens sharing a cell remain individually visible/clickable. */
export function stackOffset(position, count) {
  if (count <= 1) return [0, 0];
  const r = 0.2;
  const angle = (2 * Math.PI * position) / count;
  return [Math.cos(angle) * r, Math.sin(angle) * r];
}

export function isSafeCell(index) {
  return SAFE_CELLS.includes(index);
}
