/* ==========================================================================
   Monster AI & Green Slash Melee Combat System
   Features: A* chase, Green crescent slash VFX, Hit-flash, Screen shake
   ========================================================================== */
(function (root) {
  'use strict';

  function createMonsterSystem() {
    var monsters = [];
    var slashes = [];

    return {
      monsters: monsters,
      slashes: slashes,

      spawnMonsters: function (snap, count) {
        monsters.length = 0;
        var mapW = snap.width;
        var mapH = snap.height;
        var rows = snap.rows;
        var spawned = 0;

        // Collect floor candidate coordinates (FLOOR id is 2 in TETile)
        var floorCoords = [];
        for (var y = 2; y < mapH - 2; y++) {
          for (var x = 2; x < mapW - 2; x++) {
            if (rows[y] && (rows[y][x] === 2 || rows[y][x] === 0)) {
              if (snap.avatar && Math.abs(x - snap.avatar[0]) + Math.abs(y - snap.avatar[1]) > 8) {
                floorCoords.push({ x: x, y: y });
              }
            }
          }
        }

        while (spawned < count && floorCoords.length > 0) {
          var idx = Math.floor(Math.random() * floorCoords.length);
          var pos = floorCoords.splice(idx, 1)[0];
          monsters.push({
            id: spawned + 1,
            x: pos.x,
            y: pos.y,
            startX: pos.x,
            startY: pos.y,
            hp: 40,
            maxHp: 40,
            speed: 0.045,
            attackRange: 1.35,
            cooldown: 0,
            dead: false,
            hitFlash: 0,
            state: 'PATROL'
          });
          spawned++;
        }
      },

      update: function (player, mapRows, mapW, mapH, onPlayerDamaged, camera) {
        for (var i = 0; i < monsters.length; i++) {
          var m = monsters[i];
          if (m.dead) continue;
          if (m.hitFlash > 0) m.hitFlash--;
          if (m.cooldown > 0) m.cooldown--;

          var dx = player.x - m.x;
          var dy = player.y - m.y;
          var dist = Math.sqrt(dx * dx + dy * dy);

          if (dist < 8.5) {
            m.state = 'CHASE';
          } else {
            m.state = 'PATROL';
          }

          if (m.state === 'CHASE') {
            if (dist > m.attackRange) {
              var moveX = (dx / dist) * m.speed;
              var moveY = (dy / dist) * m.speed;
              var nextX = m.x + moveX;
              var nextY = m.y + moveY;
              var nix = Math.floor(nextX);
              var niy = Math.floor(nextY);

              // Check walkable tile (not wall=1 and not void=3)
              if (niy >= 0 && niy < mapH && nix >= 0 && nix < mapW && mapRows[niy] && mapRows[niy][nix] !== 1 && mapRows[niy][nix] !== 3) {
                m.x = nextX;
                m.y = nextY;
              }
            } else if (m.cooldown <= 0) {
              m.cooldown = 45; // ~0.75s
              slashes.push({
                x: m.x + (dx / dist) * 0.6,
                y: m.y + (dy / dist) * 0.6,
                angle: Math.atan2(dy, dx),
                life: 14,
                maxLife: 14
              });

              if (onPlayerDamaged) {
                onPlayerDamaged(1);
                if (camera) camera.shake(7);
              }
            }
          }
        }

        for (var s = slashes.length - 1; s >= 0; s--) {
          var sl = slashes[s];
          sl.life--;
          if (sl.life <= 0) {
            slashes.splice(s, 1);
          }
        }
      },

      render: function (ctx, mapH, camera) {
        var s = camera.scale;

        for (var i = 0; i < monsters.length; i++) {
          var m = monsters[i];
          if (m.dead) continue;
          var scr = camera.worldToScreen(m.x, m.y, mapH);

          ctx.save();
          ctx.translate(scr.x, scr.y);

          // Monster Body (Cyber Stalker)
          if (m.hitFlash > 0) {
            ctx.fillStyle = '#ffffff';
          } else {
            ctx.fillStyle = '#1e2633';
          }
          ctx.fillRect(-s * 0.35, -s * 0.35, s * 0.7, s * 0.7);

          // Armor outline
          ctx.strokeStyle = '#ff3344';
          ctx.lineWidth = 1.5;
          ctx.strokeRect(-s * 0.35, -s * 0.35, s * 0.7, s * 0.7);

          // Red glowing eye
          ctx.fillStyle = '#ff2200';
          ctx.fillRect(-s * 0.1, -s * 0.1, s * 0.2, s * 0.2);

          // Health Bar
          var barW = s * 0.8;
          var barH = 3.5;
          ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
          ctx.fillRect(-barW / 2, -s * 0.55, barW, barH);
          ctx.fillStyle = '#ff4d26';
          ctx.fillRect(-barW / 2, -s * 0.55, barW * (m.hp / m.maxHp), barH);

          ctx.restore();
        }

        // Green Slash VFX
        for (var j = 0; j < slashes.length; j++) {
          var sl = slashes[j];
          var slScr = camera.worldToScreen(sl.x, sl.y, mapH);
          var prog = 1 - sl.life / sl.maxLife;

          ctx.save();
          ctx.translate(slScr.x, slScr.y);
          ctx.rotate(sl.angle);

          ctx.strokeStyle = 'rgba(0, 255, 102, ' + (1 - prog) + ')';
          ctx.lineWidth = 3.5;
          ctx.beginPath();
          ctx.arc(0, 0, s * (0.4 + prog * 0.35), -Math.PI * 0.4, Math.PI * 0.4);
          ctx.stroke();

          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = 1.5;
          ctx.beginPath();
          ctx.arc(0, 0, s * (0.35 + prog * 0.3), -Math.PI * 0.3, Math.PI * 0.3);
          ctx.stroke();

          ctx.restore();
        }
      }
    };
  }

  var api = { create: createMonsterSystem };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }
  root.MonsterAI = api;
})(typeof self !== 'undefined' ? self : this);
