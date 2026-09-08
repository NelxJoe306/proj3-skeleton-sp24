/* ==========================================================================
   Pixel Rain Particle System & Weather Mode
   Features: Slower atmospheric rain streaks, Floor water ripples, Cold blue ambiance
   ========================================================================== */
(function (root) {
  'use strict';

  function createWeatherSystem() {
    var drops = [];
    var splashes = [];
    var MAX_DROPS = 140;

    return {
      enabled: false,

      toggle: function (val) {
        this.enabled = typeof val === 'boolean' ? val : !this.enabled;
      },

      update: function (viewportW, viewportH) {
        if (!this.enabled) return;

        while (drops.length < MAX_DROPS) {
          drops.push({
            x: Math.random() * (viewportW + 300) - 150,
            y: Math.random() * -80,
            length: 6 + Math.random() * 6,
            speed: 4.8 + Math.random() * 2.8, // Relaxed atmospheric fall speed
            alpha: 0.25 + Math.random() * 0.35
          });
        }

        for (var i = drops.length - 1; i >= 0; i--) {
          var d = drops[i];
          d.x -= d.speed * 0.22;
          d.y += d.speed;

          if (d.y >= viewportH - Math.random() * 50) {
            if (Math.random() < 0.35) {
              splashes.push({
                x: d.x,
                y: d.y,
                rad: 1,
                maxRad: 2.5 + Math.random() * 2.5,
                alpha: 0.55
              });
            }
            drops.splice(i, 1);
          }
        }

        for (var j = splashes.length - 1; j >= 0; j--) {
          var s = splashes[j];
          s.rad += 0.18;
          s.alpha -= 0.035;
          if (s.alpha <= 0) {
            splashes.splice(j, 1);
          }
        }
      },

      render: function (ctx, viewportW, viewportH) {
        if (!this.enabled) return;

        ctx.save();

        // Atmospheric tint
        ctx.fillStyle = 'rgba(6, 18, 38, 0.2)';
        ctx.fillRect(0, 0, viewportW, viewportH);

        // Rain Drops
        ctx.strokeStyle = '#99ccee';
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        for (var i = 0; i < drops.length; i++) {
          var d = drops[i];
          ctx.moveTo(d.x, d.y);
          ctx.lineTo(d.x - d.length * 0.22, d.y + d.length);
        }
        ctx.stroke();

        // Floor Splashes
        for (var j = 0; j < splashes.length; j++) {
          var s = splashes[j];
          ctx.strokeStyle = 'rgba(160, 210, 255, ' + s.alpha + ')';
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.strokeRect(s.x - s.rad, s.y - s.rad * 0.4, s.rad * 2, s.rad * 0.8);
        }

        ctx.restore();
      }
    };
  }

  var api = { create: createWeatherSystem };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }
  root.Weather = api;
})(typeof self !== 'undefined' ? self : this);
