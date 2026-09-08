/* ==========================================================================
   SIGNALIS UI, Pixel Hearts HUD & Mission Settlement
   Features: Pixel hearts, Stat badges, Mission report settlement modal
   ========================================================================== */
(function (root) {
  'use strict';

  function drawPixelHeart(ctx, x, y, size, filled) {
    ctx.save();
    ctx.fillStyle = filled ? '#ff2a4b' : '#3d1620';
    var u = size / 8;

    // Pixel Heart Map (8x8 grid)
    var rows = [
      [0,1,1,0,0,1,1,0],
      [1,1,1,1,1,1,1,1],
      [1,1,1,1,1,1,1,1],
      [1,1,1,1,1,1,1,1],
      [0,1,1,1,1,1,1,0],
      [0,0,1,1,1,1,0,0],
      [0,0,0,1,1,0,0,0]
    ];

    for (var r = 0; r < rows.length; r++) {
      for (var c = 0; c < rows[r].length; c++) {
        if (rows[r][c] === 1) {
          ctx.fillRect(x + c * u, y + r * u, u, u);
        }
      }
    }

    if (filled) {
      // Highlight pixel
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(x + 2 * u, y + 1 * u, u, u);
    }

    ctx.restore();
  }

  function renderHearts(container, currentHp, maxHp) {
    container.innerHTML = '';
    var totalHearts = Math.ceil(maxHp);
    for (var i = 0; i < totalHearts; i++) {
      var canvas = document.createElement('canvas');
      canvas.width = 24;
      canvas.height = 22;
      canvas.className = 'pixel-heart';
      var ctx = canvas.getContext('2d');
      drawPixelHeart(ctx, 2, 2, 20, i < currentHp);
      container.appendChild(canvas);
    }
  }

  function calculateRank(timeSec, kills, hpRemaining) {
    var score = kills * 200 + hpRemaining * 100 - timeSec * 2;
    if (score >= 800) return 'S';
    if (score >= 500) return 'A';
    if (score >= 200) return 'B';
    return 'C';
  }

  root.UI = {
    renderHearts: renderHearts,
    calculateRank: calculateRank
  };
})(typeof self !== 'undefined' ? self : this);
