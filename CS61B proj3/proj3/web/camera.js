/* ==========================================================================
   Advanced Cinematic 2D Camera for Smooth Action & Zoom
   Features: Unified World/Screen coordinate translation, Lerp damp follow, Screen Shake
   ========================================================================== */
(function (root) {
  'use strict';

  function createCamera() {
    return {
      x: 0,
      y: 0,
      targetX: 0,
      targetY: 0,
      scale: 36,
      targetScale: 36,
      lerpSpeed: 0.12,
      shakeIntensity: 0,
      shakeDecay: 0.88,
      shakeOffsetX: 0,
      shakeOffsetY: 0,

      shake: function (amount) {
        this.shakeIntensity = Math.min(25, this.shakeIntensity + amount);
      },

      update: function (playerX, playerY, mapW, mapH, viewportW, viewportH) {
        var s = this.scale;
        var dw = mapW * s;
        var dh = mapH * s;

        // Player position (playerX, playerY) is continuous world coordinates (0..mapW, 0..mapH, bottom-origin)
        // Screen pixel position of player: px = playerX * s, py = (mapH - playerY) * s
        var px = playerX * s;
        var py = (mapH - playerY) * s;

        if (dw <= viewportW) {
          this.targetX = (dw - viewportW) / 2;
        } else {
          this.targetX = Math.max(0, Math.min(dw - viewportW, px - viewportW / 2));
        }

        if (dh <= viewportH) {
          this.targetY = (dh - viewportH) / 2;
        } else {
          this.targetY = Math.max(0, Math.min(dh - viewportH, py - viewportH / 2));
        }

        // Smooth Lerp Follow
        this.x += (this.targetX - this.x) * this.lerpSpeed;
        this.y += (this.targetY - this.y) * this.lerpSpeed;

        // Smooth Zoom Interpolation
        this.scale += (this.targetScale - this.scale) * 0.15;

        // Screen Shake calculation
        if (this.shakeIntensity > 0.1) {
          this.shakeOffsetX = (Math.random() * 2 - 1) * this.shakeIntensity;
          this.shakeOffsetY = (Math.random() * 2 - 1) * this.shakeIntensity;
          this.shakeIntensity *= this.shakeDecay;
        } else {
          this.shakeIntensity = 0;
          this.shakeOffsetX = 0;
          this.shakeOffsetY = 0;
        }
      },

      // World point (wx, wy, bottom-origin) to Screen pixel
      worldToScreen: function (wx, wy, mapH) {
        var s = this.scale;
        var sx = Math.floor(wx * s - this.x + this.shakeOffsetX);
        var sy = Math.floor((mapH - wy) * s - this.y + this.shakeOffsetY);
        return { x: sx, y: sy };
      },

      // Screen pixel to World point (bottom-origin)
      screenToWorld: function (sx, sy, mapH) {
        var s = this.scale;
        var rx = (sx + this.x - this.shakeOffsetX) / s;
        var ry = mapH - (sy + this.y - this.shakeOffsetY) / s;
        return { x: rx, y: ry };
      }
    };
  }

  var api = { create: createCamera };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }
  root.Camera = api;
})(typeof self !== 'undefined' ? self : this);
