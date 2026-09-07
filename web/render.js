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
  }

  var api = { drawFrame: drawFrame };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.Render = api;
  }
})(typeof self !== 'undefined' ? self : this);
