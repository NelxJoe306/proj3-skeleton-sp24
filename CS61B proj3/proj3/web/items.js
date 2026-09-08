/* ==========================================================================
   Items, Pickups & Powerup System
   Features: Health Potions (+HP), Attack Crystals (+ATK), Torches (+Vision)
   ========================================================================== */
(function (root) {
  'use strict';

  function createItemSystem() {
    var items = [];
    var time = 0;

    return {
      items: items,

      spawnItems: function (snap) {
        items.length = 0;
        var mapW = snap.width;
        var mapH = snap.height;
        var rows = snap.rows;

        var floorCoords = [];
        for (var y = 2; y < mapH - 2; y++) {
          for (var x = 2; x < mapW - 2; x++) {
            if (rows[y] && (rows[y][x] === 2 || rows[y][x] === 0)) {
              if (snap.avatar && Math.abs(x - snap.avatar[0]) + Math.abs(y - snap.avatar[1]) > 5) {
                floorCoords.push({ x: x, y: y });
              }
            }
          }
        }

        // Spawn 2-3 Torches
        for (var t = 0; t < 3 && floorCoords.length; t++) {
          var tIdx = Math.floor(Math.random() * floorCoords.length);
          var tPos = floorCoords.splice(tIdx, 1)[0];
          items.push({ type: 'TORCH', x: tPos.x, y: tPos.y, collected: false });
        }

        // Spawn 3-5 Health Potions
        for (var h = 0; h < 4 && floorCoords.length; h++) {
          var hIdx = Math.floor(Math.random() * floorCoords.length);
          var hPos = floorCoords.splice(hIdx, 1)[0];
          items.push({ type: 'HEALTH', x: hPos.x, y: hPos.y, collected: false });
        }

        // Spawn 2-3 Attack Crystals
        for (var a = 0; a < 3 && floorCoords.length; a++) {
          var aIdx = Math.floor(Math.random() * floorCoords.length);
          var aPos = floorCoords.splice(aIdx, 1)[0];
          items.push({ type: 'ATTACK', x: aPos.x, y: aPos.y, collected: false });
        }
      },

      update: function (player, onPickup) {
        time += 0.05;
        for (var i = 0; i < items.length; i++) {
          var item = items[i];
          if (item.collected) continue;

          var dx = player.x - item.x;
          var dy = player.y - item.y;
          if (dx * dx + dy * dy < 0.42) {
            item.collected = true;
            if (onPickup) onPickup(item);
          }
        }
      },

      render: function (ctx, mapH, camera) {
        var s = camera.scale;

        for (var i = 0; i < items.length; i++) {
          var item = items[i];
          if (item.collected) continue;

          var scr = camera.worldToScreen(item.x, item.y, mapH);
          var bob = Math.sin(time * 4 + i) * 3;

          ctx.save();
          ctx.translate(scr.x, scr.y + bob);

          if (item.type === 'TORCH') {
            ctx.fillStyle = '#8a5522';
            ctx.fillRect(-s * 0.1, -s * 0.1, s * 0.2, s * 0.4);
            ctx.fillStyle = '#ff8800';
            ctx.fillRect(-s * 0.15, -s * 0.35, s * 0.3, s * 0.25);
            ctx.fillStyle = '#ffee44';
            ctx.fillRect(-s * 0.08, -s * 0.3, s * 0.16, s * 0.16);
          } else if (item.type === 'HEALTH') {
            ctx.fillStyle = '#ff2a4b';
            ctx.fillRect(-s * 0.2, -s * 0.2, s * 0.4, s * 0.4);
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(-s * 0.06, -s * 0.32, s * 0.12, s * 0.12);
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(-s * 0.04, -s * 0.12, s * 0.08, s * 0.24);
            ctx.fillRect(-s * 0.12, -s * 0.04, s * 0.24, s * 0.08);
          } else if (item.type === 'ATTACK') {
            ctx.fillStyle = '#ffcc00';
            ctx.beginPath();
            ctx.moveTo(0, -s * 0.3);
            ctx.lineTo(s * 0.2, 0);
            ctx.lineTo(0, s * 0.3);
            ctx.lineTo(-s * 0.2, 0);
            ctx.closePath();
            ctx.fill();
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 1;
            ctx.stroke();
          }

          ctx.restore();
        }
      }
    };
  }

  var api = { create: createItemSystem };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }
  root.Items = api;
})(typeof self !== 'undefined' ? self : this);
