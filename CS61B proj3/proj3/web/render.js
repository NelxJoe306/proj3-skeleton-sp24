/* Draws a GameSnapshot onto a 2D canvas context through a View camera.

   Pure rendering: reads snap + view, writes pixels on ctx. The caller fills ctx once
   per frame (start, WASD loop, zoom/resize) — this file knows nothing about menus or
   keys, so later tickets keep touching callers only. Every tile is drawn from the
   snapshot's own legend: bg fill + fg glyph, so the client never hard-codes a tile.

   Cells are filled to *integer* pixel boundaries (floor of each cell's left/top and
   the next cell's left/top), so zoom levels that make s fractional stay contiguous —
   no 1px gaps, no overlapping glyph bleed. Exposes window.Render (classic script). */
(function (root) {
  'use strict';

  var STAGE = '#0b0f14'; // page ground — the world's void tiles (black) read as one block
  var MISSING = { glyph: ' ', fg: '#ffffff', bg: '#000000' };
  var FONT_STACK = 'Consolas, "Courier New", monospace';

  function paletteFor(snap) {
    var p = {};
    var list = snap.legend || [];
    for (var i = 0; i < list.length; i++) {
      var e = list[i];
      p[e.id] = { glyph: e.glyph, fg: e.fg, bg: e.bg };
    }
    return p;
  }

  /* Draws world tile rows[y][x] through the camera view. display tile (col, row) maps
     y = mapH-1-row (View.mapYFromRow) and occupies canvas px
     [floor(col*s - camX), floor((col+1)*s - camX)) x [floor(row*s - camY), ...). */
  function drawFrame(ctx, viewportW, viewportH, snap, view) {
    var palette = paletteFor(snap);
    ctx.fillStyle = STAGE;
    ctx.fillRect(0, 0, viewportW, viewportH);

    var rows = snap.rows;
    var W = snap.width;
    var H = snap.height;
    var s = view.s;
    var vis = View.visibleTiles(view.camX, view.camY, viewportW, viewportH, s, W, H);
    if (!vis) {
      return;
    }

    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (var row = vis.row0; row <= vis.row1; row++) {
      var y = View.mapYFromRow(row, H);
      var rowTiles = rows[y];
      var py0 = Math.floor(row * s - view.camY);
      var py1 = Math.floor((row + 1) * s - view.camY);
      var cellH = py1 - py0;
      var drawGlyph = cellH >= 4;
      if (drawGlyph) {
        ctx.font = cellH + 'px ' + FONT_STACK;
      }
      for (var col = vis.col0; col <= vis.col1; col++) {
        var px0 = Math.floor(col * s - view.camX);
        var px1 = Math.floor((col + 1) * s - view.camX);
        var e = palette[rowTiles[col]] || MISSING;
        ctx.fillStyle = e.bg;
        ctx.fillRect(px0, py0, px1 - px0, cellH);
        var g = e.glyph;
        if (drawGlyph && g && g !== ' ') {
          ctx.fillStyle = e.fg;
          ctx.fillText(g, (px0 + px1) / 2, (py0 + py1) / 2 + 1); // +1 optically centres most glyphs
        }
      }
    }

    if (snap.goal) {
      drawGoalMarker(ctx, view, W, H, vis, snap.goal); // over tiles: still shows when the avatar stands on it
    }
  }

  /* Gold ring around the goal cell (ticket 08). snap.goal is the goal *entity*
     [x, y], reported separately from rows — so this keeps marking the goal even
     when the avatar's tile has overlaid it on landing. Same integer-boundary cell
     rect as drawFrame, so the ring hugs the tile at every (fractional) zoom. */
  function drawGoalMarker(ctx, view, W, H, vis, goalXY) {
    var gx = goalXY[0];
    var row = View.mapYFromRow(goalXY[1], H); // engine y (bottom-origin) -> display row
    if (gx < vis.col0 || gx > vis.col1 || row < vis.row0 || row > vis.row1) {
      return;
    }
    var x0 = Math.floor(gx * view.s - view.camX);
    var x1 = Math.floor((gx + 1) * view.s - view.camX);
    var y0 = Math.floor(row * view.s - view.camY);
    var y1 = Math.floor((row + 1) * view.s - view.camY);
    var inset = Math.max(1, view.s * 0.18);
    var rx = x0 + inset;
    var ry = y0 + inset;
    var rw = (x1 - x0) - 2 * inset;
    var rh = (y1 - y0) - 2 * inset;
    if (rw <= 0 || rh <= 0) {
      return; // tile is sub-pixel at this zoom — nothing to ring
    }
    ctx.lineWidth = Math.max(1.5, view.s * 0.12);
    ctx.strokeStyle = 'rgba(255, 216, 0, 0.35)';
    ctx.strokeRect(rx, ry, rw, rh); // soft outer halo
    ctx.strokeStyle = '#ffd800';
    ctx.strokeRect(rx, ry, rw, rh);
  }

  var api = { drawFrame: drawFrame };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.Render = api;
  }
})(typeof self !== 'undefined' ? self : this);
