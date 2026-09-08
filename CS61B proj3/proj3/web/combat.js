/* ==========================================================================
   Combat & Particle System (Player Ranged & VFX)
   Features: White/Yellow particle beam, Wall sparks, Hit-flash, Damage numbers
   ========================================================================== */
(function (root) {
  'use strict';

  function createCombatSystem() {
    var bullets = [];
    var sparks = [];
    var damagePopups = [];

    return {
      bullets: bullets,
      sparks: sparks,
      damagePopups: damagePopups,

      shoot: function (fromX, fromY, toX, toY, atkLevel) {
        var dx = toX - fromX;
        var dy = toY - fromY;
        var dist = Math.sqrt(dx * dx + dy * dy);
        if (dist === 0) return;

        var speed = 0.45;
        var vx = (dx / dist) * speed;
        var vy = (dy / dist) * speed;

        bullets.push({
          x: fromX,
          y: fromY,
          vx: vx,
          vy: vy,
          damage: 15 + atkLevel * 10,
          life: 45,
          trail: []
        });
      },

      spawnSparks: function (x, y, color) {
        for (var i = 0; i < 8; i++) {
          var angle = Math.random() * Math.PI * 2;
          var spd = 0.05 + Math.random() * 0.15;
          sparks.push({
            x: x,
            y: y,
            vx: Math.cos(angle) * spd,
            vy: Math.sin(angle) * spd,
            color: color || '#ffd800',
            size: 2 + Math.random() * 2,
            life: 18 + Math.random() * 10,
            maxLife: 28
          });
        }
      },

      spawnPopup: function (x, y, text, color) {
        damagePopups.push({
          x: x,
          y: y,
          text: text,
          color: color || '#ffd800',
          vy: 0.03, // float upwards in bottom-origin world coordinates (+y)
          alpha: 1.0,
          life: 35
        });
      },

      update: function (mapRows, mapW, mapH, monsters, onMonsterKilled) {
        for (var i = bullets.length - 1; i >= 0; i--) {
          var b = bullets[i];
          b.trail.push({ x: b.x, y: b.y });
          if (b.trail.length > 5) b.trail.shift();

          b.x += b.vx;
          b.y += b.vy;
          b.life--;

          var ix = Math.floor(b.x);
          var iy = Math.floor(b.y);

          var hitWall = false;
          if (ix < 0 || ix >= mapW || iy < 0 || iy >= mapH) {
            hitWall = true;
          } else if (mapRows && mapRows[iy] && (mapRows[iy][ix] === 1 || mapRows[iy][ix] === 3)) {
            hitWall = true;
          }

          if (hitWall) {
            this.spawnSparks(b.x, b.y, '#ffe600');
            bullets.splice(i, 1);
            continue;
          }

          var hitMonster = false;
          if (monsters && monsters.length) {
            for (var m = 0; m < monsters.length; m++) {
              var monster = monsters[m];
              if (monster.dead) continue;
              var mdx = monster.x - b.x;
              var mdy = monster.y - b.y;
              if (mdx * mdx + mdy * mdy < 0.45) {
                hitMonster = true;
                monster.hp -= b.damage;
                monster.hitFlash = 6;
                this.spawnSparks(b.x, b.y, '#ffffff');
                this.spawnPopup(monster.x, monster.y + 0.5, '-' + b.damage, '#ffdd44');

                if (monster.hp <= 0 && !monster.dead) {
                  monster.dead = true;
                  this.spawnSparks(monster.x, monster.y, '#ff4444');
                  this.spawnPopup(monster.x, monster.y + 0.8, 'ELIMINATED', '#ff5533');
                  if (onMonsterKilled) onMonsterKilled(monster);
                }
                break;
              }
            }
          }

          if (hitMonster || b.life <= 0) {
            bullets.splice(i, 1);
          }
        }

        for (var s = sparks.length - 1; s >= 0; s--) {
          var sp = sparks[s];
          sp.x += sp.vx;
          sp.y += sp.vy;
          sp.life--;
          if (sp.life <= 0) {
            sparks.splice(s, 1);
          }
        }

        for (var p = damagePopups.length - 1; p >= 0; p--) {
          var pop = damagePopups[p];
          pop.y += pop.vy;
          pop.life--;
          pop.alpha = pop.life / 35;
          if (pop.life <= 0) {
            damagePopups.splice(p, 1);
          }
        }
      },

      render: function (ctx, mapH, camera) {
        // 1. Bullets
        for (var i = 0; i < bullets.length; i++) {
          var b = bullets[i];
          var scr = camera.worldToScreen(b.x, b.y, mapH);

          for (var t = 0; t < b.trail.length; t++) {
            var tr = camera.worldToScreen(b.trail[t].x, b.trail[t].y, mapH);
            ctx.fillStyle = 'rgba(255, 230, 0, ' + (0.2 + t * 0.15) + ')';
            ctx.fillRect(tr.x - 2, tr.y - 2, 4, 4);
          }

          ctx.fillStyle = '#ffcc00';
          ctx.fillRect(scr.x - 4, scr.y - 4, 8, 8);
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(scr.x - 2, scr.y - 2, 4, 4);
        }

        // 2. Sparks
        for (var j = 0; j < sparks.length; j++) {
          var sp = sparks[j];
          var spScr = camera.worldToScreen(sp.x, sp.y, mapH);
          var alpha = sp.life / sp.maxLife;
          ctx.fillStyle = sp.color;
          ctx.globalAlpha = alpha;
          ctx.fillRect(spScr.x, spScr.y, sp.size, sp.size);
        }
        ctx.globalAlpha = 1.0;

        // 3. Popups
        ctx.font = 'bold 12px Consolas, monospace';
        ctx.textAlign = 'center';
        for (var k = 0; k < damagePopups.length; k++) {
          var pop = damagePopups[k];
          var pScr = camera.worldToScreen(pop.x, pop.y, mapH);
          ctx.fillStyle = pop.color;
          ctx.globalAlpha = Math.max(0, pop.alpha);
          ctx.fillText(pop.text, pScr.x, pScr.y);
        }
        ctx.globalAlpha = 1.0;
      }
    };
  }

  var api = { create: createCombatSystem };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }
  root.Combat = api;
})(typeof self !== 'undefined' ? self : this);
