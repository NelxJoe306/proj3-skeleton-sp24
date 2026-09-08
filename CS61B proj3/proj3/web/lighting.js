/* ==========================================================================
   2D Dynamic Lighting & Fog of War System
   Features: Offscreen canvas mask, Explored memory map, Torch expansion, Radial light holes
   ========================================================================== */
(function (root) {
  'use strict';

  function createLightingSystem(mapW, mapH) {
    var explored = [];
    for (var y = 0; y < mapH; y++) {
      explored[y] = new Uint8Array(mapW);
    }

    var offscreenCanvas = null;
    var offscreenCtx = null;

    return {
      explored: explored,
      time: 0,

      updateVision: function (px, py, radius) {
        var ipx = Math.round(px);
        var ipy = Math.round(py);
        var r = Math.ceil(radius);
        var minX = Math.max(0, ipx - r);
        var maxX = Math.min(mapW - 1, ipx + r);
        var minY = Math.max(0, ipy - r);
        var maxY = Math.min(mapH - 1, ipy + r);

        for (var x = minX; x <= maxX; x++) {
          for (var y = minY; y <= maxY; y++) {
            var dx = x - px;
            var dy = y - py;
            if (dx * dx + dy * dy <= radius * radius) {
              this.explored[y][x] = 1;
            }
          }
        }
      },

      render: function (ctx, viewportW, viewportH, player, torches, mapW, mapH, camera) {
        this.time += 0.05;
        var s = camera.scale;

        // Initialize / resize offscreen mask
        if (!offscreenCanvas) {
          offscreenCanvas = document.createElement('canvas');
        }
        if (offscreenCanvas.width !== viewportW || offscreenCanvas.height !== viewportH) {
          offscreenCanvas.width = Math.max(1, viewportW);
          offscreenCanvas.height = Math.max(1, viewportH);
          offscreenCtx = offscreenCanvas.getContext('2d');
        }

        var mctx = offscreenCtx;
        if (!mctx) return;

        // 1. Update exploration memory
        var baseRadius = player.hasTorch ? 9.5 : 4.5;
        this.updateVision(player.x, player.y, baseRadius);

        // 2. Fill offscreen mask with dark fog of war
        mctx.globalCompositeOperation = 'source-over';
        mctx.fillStyle = 'rgba(6, 8, 12, 0.96)';
        mctx.fillRect(0, 0, viewportW, viewportH);

        // 3. Punch light holes in the mask using 'destination-out'
        mctx.globalCompositeOperation = 'destination-out';

        // A. Draw Player Dynamic Light Halo
        var pScreen = camera.worldToScreen(player.x, player.y, mapH);
        var pRadiusPx = baseRadius * s;
        var flicker = (Math.sin(this.time * 6) + Math.cos(this.time * 9)) * (player.hasTorch ? 0.06 : 0.02);
        pRadiusPx *= (1 + flicker);

        var pGrad = mctx.createRadialGradient(pScreen.x, pScreen.y, s * 0.3, pScreen.x, pScreen.y, pRadiusPx);
        pGrad.addColorStop(0, 'rgba(0, 0, 0, 1.0)');
        pGrad.addColorStop(0.7, 'rgba(0, 0, 0, 0.9)');
        pGrad.addColorStop(1, 'rgba(0, 0, 0, 0.0)');

        mctx.fillStyle = pGrad;
        mctx.beginPath();
        mctx.arc(pScreen.x, pScreen.y, pRadiusPx, 0, Math.PI * 2);
        mctx.fill();

        // B. Draw World Torches Lights
        if (torches && torches.length) {
          for (var i = 0; i < torches.length; i++) {
            var t = torches[i];
            if (t.collected) continue;
            this.updateVision(t.x, t.y, 4.0);
            var tScreen = camera.worldToScreen(t.x, t.y, mapH);
            var tRadiusPx = 4.8 * s * (1 + Math.sin(this.time * 8 + i) * 0.05);

            var tGrad = mctx.createRadialGradient(tScreen.x, tScreen.y, s * 0.2, tScreen.x, tScreen.y, tRadiusPx);
            tGrad.addColorStop(0, 'rgba(0, 0, 0, 1.0)');
            tGrad.addColorStop(0.65, 'rgba(0, 0, 0, 0.8)');
            tGrad.addColorStop(1, 'rgba(0, 0, 0, 0.0)');

            mctx.fillStyle = tGrad;
            mctx.beginPath();
            mctx.arc(tScreen.x, tScreen.y, tRadiusPx, 0, Math.PI * 2);
            mctx.fill();
          }
        }

        // C. Give soft visibility to explored memory tiles
        mctx.fillStyle = 'rgba(0, 0, 0, 0.65)';
        var visCol0 = Math.max(0, Math.floor(camera.x / s));
        var visCol1 = Math.min(mapW - 1, Math.ceil((camera.x + viewportW) / s));
        var visRow0 = Math.max(0, Math.floor(camera.y / s));
        var visRow1 = Math.min(mapH - 1, Math.ceil((camera.y + viewportH) / s));

        for (var row = visRow0; row <= visRow1; row++) {
          var y = mapH - 1 - row;
          for (var col = visCol0; col <= visCol1; col++) {
            if (this.explored[y] && this.explored[y][col] === 1) {
              var sx = Math.floor(col * s - camera.x + camera.shakeOffsetX);
              var sy = Math.floor(row * s - camera.y + camera.shakeOffsetY);
              mctx.fillRect(sx, sy, Math.ceil(s), Math.ceil(s));
            }
          }
        }

        // 4. Composite the dark mask over the main game canvas
        ctx.save();
        ctx.globalCompositeOperation = 'source-over';
        ctx.drawImage(offscreenCanvas, 0, 0);

        // 5. Warm Amber Torch Light Glow Hue Overlay (when player is holding torch)
        if (player.hasTorch) {
          ctx.globalCompositeOperation = 'lighter';
          var torchHalo = ctx.createRadialGradient(pScreen.x, pScreen.y, s * 0.2, pScreen.x, pScreen.y, pRadiusPx * 0.85);
          torchHalo.addColorStop(0, 'rgba(255, 140, 20, 0.22)');
          torchHalo.addColorStop(0.6, 'rgba(255, 90, 0, 0.08)');
          torchHalo.addColorStop(1, 'rgba(0, 0, 0, 0.0)');
          ctx.fillStyle = torchHalo;
          ctx.beginPath();
          ctx.arc(pScreen.x, pScreen.y, pRadiusPx * 0.85, 0, Math.PI * 2);
          ctx.fill();
        }

        ctx.restore();
      }
    };
  }

  var api = { create: createLightingSystem };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }
  root.Lighting = api;
})(typeof self !== 'undefined' ? self : this);
