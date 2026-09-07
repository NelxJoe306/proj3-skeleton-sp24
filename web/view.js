/* Pure camera/view geometry for the proj3C canvas.

   The engine grid is rows[y][x]: y counts up from the BOTTOM (screen up = higher y,
   matching the desktop StdDraw renderer). The browser canvas origin is the TOP-left,
   so this module owns the flip and every pixel decision. It touches no DOM, so it
   runs under node as well as in the browser (classic script; exposes window.View,
   and module.exports under node for tests).

   Camera model: the viewport shows a rectangle of the map in *display pixels*
   (1 tile = s px) whose top-left corner sits at (camX, camY) in map-display space.
   A display tile (col, row) [top-origin] lands at canvas px (col*s - camX, row*s - camY).
   The whole-map overview (ticket 05) picks s + cam to centre the map; pan/zoom
   (ticket 07) just changes s/cam and calls the same renderer. */
(function (root) {
  'use strict';

  /* Largest integer tile size that fits mapW x mapH in a viewport (min 1 px/tile). */
  function fitTileSize(viewportW, viewportH, mapW, mapH) {
    var s = Math.floor(Math.min(viewportW / mapW, viewportH / mapH));
    return s < 1 ? 1 : s;
  }

  /* The camera that fits the whole map on one screen, centred in the viewport.
     camX/camY are negative when the map is smaller than the viewport (letterboxed). */
  function overviewCamera(viewportW, viewportH, mapW, mapH) {
    var s = fitTileSize(viewportW, viewportH, mapW, mapH);
    var camX = -Math.floor((viewportW - mapW * s) / 2);
    var camY = -Math.floor((viewportH - mapH * s) / 2);
    return { s: s, camX: camX, camY: camY };
  }

  /* Engine row y (bottom-origin) -> display row index (top-origin). */
  function mapYFromRow(row, mapH) {
    return mapH - 1 - row;
  }

  /* The display-tile window [col0..col1] x [row0..row1] currently under the camera,
     clamped to the map, or null when the camera sees no map tiles. */
  function visibleTiles(camX, camY, viewportW, viewportH, s, mapW, mapH) {
    var col0 = Math.max(0, Math.floor(camX / s));
    var col1 = Math.min(mapW - 1, Math.ceil((camX + viewportW) / s) - 1);
    var row0 = Math.max(0, Math.floor(camY / s));
    var row1 = Math.min(mapH - 1, Math.ceil((camY + viewportH) / s) - 1);
    return col0 <= col1 && row0 <= row1
        ? { col0: col0, col1: col1, row0: row0, row1: row1 }
        : null;
  }

  var api = {
    fitTileSize: fitTileSize,
    overviewCamera: overviewCamera,
    mapYFromRow: mapYFromRow,
    visibleTiles: visibleTiles
  };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.View = api;
  }
})(typeof self !== 'undefined' ? self : this);
