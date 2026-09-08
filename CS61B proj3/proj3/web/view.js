/* Pure camera/view geometry for the proj3C canvas.

   The engine grid is rows[y][x]: y counts up from the BOTTOM (screen up = higher y,
   matching the desktop StdDraw renderer). The browser canvas origin is the TOP-left,
   so this module owns the flip and every pixel decision. It touches no DOM, so it
   runs under node as well as in the browser (classic script; exposes window.View,
   and module.exports under node for tests).

   Camera model: the viewport shows a rectangle of the map in *display pixels*
   (1 tile = s px) whose top-left corner sits at (camX, camY) in map-display space.
   A display tile (col, row) [top-origin] lands around canvas px (col*s - camX, row*s - camY).

   One rule drives every level of zoom (ticket 07): the camera aims at the avatar's
   display-pixel centre, then clamps so it never shows void — and when the whole map
   at the current scale is SMALLER than the viewport, clamping degenerates to centring
   the whole map. So the whole-map overview (map fits -> centred, ticket 05) and the
   zoomed follow (map bigger -> avatar-centred, clamped to the map edges) are the same
   code path with no mode state. */
(function (root) {
  'use strict';

  function clamp(v, lo, hi) {
    return Math.min(hi, Math.max(lo, v));
  }

  /* Largest integer tile size that fits mapW x mapH in a viewport (min 1 px/tile).
     This is also the zoomed-out bound: at this scale the whole map is on screen. */
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

  /* The top bound of the zoom range — "ground level". Tiles get big enough to play in
     but never absurd; always at least the whole-map fit (so a tiny map on a huge screen
     still has a zoom-in headroom of zero rather than a meaningless range). */
  function groundScale(viewportW, viewportH, mapW, mapH) {
    return Math.max(fitTileSize(viewportW, viewportH, mapW, mapH), 48);
  }

  /* Next scale for one wheel notch: multiply by factor zooming in, divide zooming out,
     clamped to [sMin, sMax]; snaps to the bound when the notch would land just past it. */
  function zoomStep(s, zoomIn, sMin, sMax, factor) {
    var ns = clamp(s * (zoomIn ? factor : 1 / factor), sMin, sMax);
    return Math.abs(ns - s) < 0.25 ? (zoomIn ? sMax : sMin) : ns;
  }

  /* Engine row y (bottom-origin) -> display row index (top-origin). */
  function mapYFromRow(row, mapH) {
    return mapH - 1 - row;
  }

  /* Camera that aims at the avatar (engine coords [x, y], y up) and clamps to the map.
     When the map at scale s is no bigger than the viewport it just centres the map. */
  function followCamera(s, viewportW, viewportH, mapW, mapH, avatarXY) {
    var dw = mapW * s;
    var dh = mapH * s;
    var acx = (avatarXY[0] + 0.5) * s;
    var acy = (mapH - 0.5 - avatarXY[1]) * s;
    var camX = dw <= viewportW
        ? (dw - viewportW) / 2
        : clamp(acx - viewportW / 2, 0, dw - viewportW);
    var camY = dh <= viewportH
        ? (dh - viewportH) / 2
        : clamp(acy - viewportH / 2, 0, dh - viewportH);
    return { s: s, camX: camX, camY: camY };
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
    groundScale: groundScale,
    zoomStep: zoomStep,
    mapYFromRow: mapYFromRow,
    followCamera: followCamera,
    visibleTiles: visibleTiles
  };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.View = api;
  }
})(typeof self !== 'undefined' ? self : this);
