/* ==========================================================================
   CS61B Proj3 // DETERMINISTIC ACTION ENGINE
   Unified Game Client Loop, Camera, Combat, Lighting, AI, Items & HUD
   ========================================================================== */
(function () {
  'use strict';

  var STORE_KEY = 'proj3c.session.v1';

  // DOM Elements
  var CANVAS = document.getElementById('world');
  var ctx = CANVAS.getContext('2d');
  var MENU = document.getElementById('menu');
  var EYE_CANVAS = document.getElementById('eye-canvas');
  var SEED_INPUT = document.getElementById('seed');
  var WEATHER_TOGGLE = document.getElementById('weather-toggle');
  var WEATHER_LABEL = document.getElementById('weather-label');
  var ERROR = document.getElementById('menu-error');
  var SETTINGS_PANEL = document.getElementById('settings-panel');

  var START_BTN = document.getElementById('start-btn');
  var CONTINUE_BTN = document.getElementById('continue-btn');
  var LOAD_BTN = document.getElementById('load-btn');

  var GAME_HUD = document.getElementById('game-hud');
  var HEARTS_CONTAINER = document.getElementById('hearts-container');
  var HUD_ATK = document.getElementById('hud-atk');
  var HUD_TORCH = document.getElementById('hud-torch');
  var HUD_KILLS = document.getElementById('hud-kills');
  var HUD_DIST = document.getElementById('hud-dist');
  var RAIN_BTN = document.getElementById('rain-btn');
  var SAVE_BTN = document.getElementById('save-btn');
  var QUIT_BTN = document.getElementById('quit-btn');

  var CURSOR_CROSSHAIR = document.getElementById('cursor-crosshair');

  // Active Subsystems
  var eyeInstance = null;
  var camera = null;
  var lighting = null;
  var weather = null;
  var combat = null;
  var monsterAI = null;
  var items = null;

  // Game State
  var state = null;
  var quitting = false;
  var animFrameId = null;
  var mouse = { screenX: window.innerWidth / 2, screenY: window.innerHeight / 2, worldX: 0, worldY: 0 };
  var keysDown = {};

  // Initialize Mechanical Eye on Menu
  if (window.SignalisEye && SignalisEye.init) {
    eyeInstance = SignalisEye.init('eye-canvas');
  }

  // KeyPipe Replay Pipeline
  var pipe = KeyPipe.createPipeline(function (keys) {
    return postSession(withKeys(baseBody(state.session), keys))
      .then(function (snap) {
        if (!state) return;
        state.snap = snap;
        persistSession(keys);
      })
      .catch(function () {
        if (state) persistSession(keys);
      });
  }, function (err) {
    console.warn('move replay offline:', err);
  });

  function baseBody(session) {
    if (session.kind === 'new') {
      return { kind: 'new', size: session.size, seed: session.seedText };
    }
    return { kind: 'load' };
  }

  function withKeys(body, keys) {
    body.keys = keys;
    return body;
  }

  function randSeedText() {
    if (window.crypto && crypto.getRandomValues) {
      var a = new Uint32Array(1);
      crypto.getRandomValues(a);
      return String(a[0]);
    }
    return String(Math.floor(Math.random() * 0x7fffffff));
  }

  function report(msg) {
    if (!ERROR) return;
    ERROR.textContent = msg;
    ERROR.hidden = !msg;
  }

  /* ---- Standalone Deterministic Fallback Generator ----------------------- */
  function generateFallbackDungeon(size, seedStr) {
    var sizeMap = { small: [40, 40], medium: [80, 80], large: [120, 120] };
    var dims = sizeMap[size] || [80, 80];
    var W = dims[0];
    var H = dims[1];
    var seed = 0;
    for (var i = 0; i < seedStr.length; i++) {
      seed = (seed * 31 + seedStr.charCodeAt(i)) >>> 0;
    }

    function pseudoRandom() {
      seed = (seed * 1664525 + 1013904223) >>> 0;
      return seed / 4294967296;
    }

    var rows = [];
    for (var y = 0; y < H; y++) {
      rows[y] = new Int32Array(W);
      for (var x = 0; x < W; x++) rows[y][x] = 3; // NOTHING (3)
    }

    var rooms = [];
    var numRooms = Math.floor(12 + pseudoRandom() * 8);
    for (var r = 0; r < numRooms; r++) {
      var rw = Math.floor(6 + pseudoRandom() * 8);
      var rh = Math.floor(5 + pseudoRandom() * 6);
      var rx = Math.floor(2 + pseudoRandom() * (W - rw - 4));
      var ry = Math.floor(2 + pseudoRandom() * (H - rh - 4));

      for (var cy = ry; cy < ry + rh; cy++) {
        for (var cx = rx; cx < rx + rw; cx++) {
          rows[cy][cx] = 2; // FLOOR (2)
        }
      }
      rooms.push({ x: rx + Math.floor(rw / 2), y: ry + Math.floor(rh / 2) });
    }

    for (var k = 0; k < rooms.length - 1; k++) {
      var rA = rooms[k];
      var rB = rooms[k + 1];
      var curX = rA.x;
      var curY = rA.y;
      while (curX !== rB.x) {
        rows[curY][curX] = 2;
        curX += curX < rB.x ? 1 : -1;
      }
      while (curY !== rB.y) {
        rows[curY][curX] = 2;
        curY += curY < rB.y ? 1 : -1;
      }
    }

    for (var wy = 1; wy < H - 1; wy++) {
      for (var wx = 1; wx < W - 1; wx++) {
        if (rows[wy][wx] === 3) {
          var hasFloorNeighbor = false;
          for (var dy = -1; dy <= 1; dy++) {
            for (var dx = -1; dx <= 1; dx++) {
              if (rows[wy + dy][wx + dx] === 2) {
                hasFloorNeighbor = true;
                break;
              }
            }
            if (hasFloorNeighbor) break;
          }
          if (hasFloorNeighbor) rows[wy][wx] = 1; // WALL (1)
        }
      }
    }

    var avatarPos = [rooms[0].x, rooms[0].y];
    var goalPos = [rooms[rooms.length - 1].x, rooms[rooms.length - 1].y];

    var legend = [
      { id: 0, name: 'you', glyph: '@', fg: '#ffffff', bg: '#000000' },
      { id: 1, name: 'wall', glyph: '#', fg: '#d88080', bg: '#303030' },
      { id: 2, name: 'floor', glyph: '·', fg: '#80c080', bg: '#000000' },
      { id: 3, name: 'nothing', glyph: ' ', fg: '#000000', bg: '#000000' },
      { id: 13, name: 'goal', glyph: '★', fg: '#ffd800', bg: '#000000' }
    ];

    return {
      width: W,
      height: H,
      seed: seed,
      won: false,
      saveMissing: false,
      avatar: avatarPos,
      goal: goalPos,
      rows: rows,
      legend: legend
    };
  }

  function postSession(body) {
    return fetch('/api/replay', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (res) {
      return res.text().then(function (text) {
        var snap;
        try {
          snap = JSON.parse(text);
        } catch (_) {
          snap = null;
        }
        if (!res.ok) {
          throw new Error(snap && snap.error ? snap.error : 'HTTP ' + res.status);
        }
        return snap;
      });
    }).catch(function (err) {
      console.warn('Backend server /api/replay unreachable, launching in standalone client mode:', err.message);
      return generateFallbackDungeon(body.size || 'medium', String(body.seed || randSeedText()));
    });
  }

  /* ---- Session Storage --------------------------------------------------- */
  function persistSession(keys) {
    if (!state) return;
    var stored = {
      kind: state.session.kind,
      keys: keys || '',
      steps: state.steps
    };
    if (state.session.kind === 'new') {
      stored.size = state.session.size;
      stored.seedText = state.session.seedText;
    }
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(stored));
    } catch (_) {}
  }

  function clearStoredSession() {
    try {
      localStorage.removeItem(STORE_KEY);
    } catch (_) {}
  }

  function readStoredSession() {
    var o;
    try {
      o = JSON.parse(localStorage.getItem(STORE_KEY));
    } catch (_) {
      return null;
    }
    if (!o || typeof o !== 'object') return null;
    var keys = typeof o.keys === 'string' ? o.keys : '';
    var steps = Number.isFinite(o.steps) && o.steps >= 0 ? Math.floor(o.steps) : 0;
    if (o.kind === 'new') {
      if (!/^(small|medium|large)$/.test(o.size || '') || typeof o.seedText !== 'string') {
        return null;
      }
      return { session: { kind: 'new', size: o.size, seedText: o.seedText }, keys: keys, steps: steps };
    }
    if (o.kind === 'load') {
      return { session: { kind: 'load' }, keys: keys, steps: steps };
    }
    return null;
  }

  function refreshContinue() {
    var has = !!readStoredSession();
    if (CONTINUE_BTN) CONTINUE_BTN.classList.toggle('disabled', !has);
  }

  /* ---- Game Loop & Lifecycle --------------------------------------------- */
  function adoptGame(session, snap, keys, steps) {
    var mapW = snap.width;
    var mapH = snap.height;

    var startX = (snap.avatar ? snap.avatar[0] : Math.floor(mapW / 2)) + 0.5;
    var startY = (snap.avatar ? snap.avatar[1] : Math.floor(mapH / 2)) + 0.5;

    camera = Camera.create();
    camera.scale = 36;
    camera.targetScale = 36;
    camera.x = startX * camera.scale;
    camera.y = (mapH - startY) * camera.scale;

    lighting = Lighting.create(mapW, mapH);
    weather = Weather.create();
    weather.enabled = WEATHER_TOGGLE ? WEATHER_TOGGLE.checked : true;
    if (RAIN_BTN) RAIN_BTN.textContent = weather.enabled ? '[ RAIN // ACTIVE ]' : '[ RAIN // MUTED ]';

    combat = Combat.create();
    monsterAI = MonsterAI.create();
    monsterAI.spawnMonsters(snap, Math.min(18, Math.floor((mapW * mapH) / 120)));

    items = Items.create();
    items.spawnItems(snap);

    state = {
      snap: snap,
      session: session,
      steps: steps,
      startTime: Date.now(),
      won: false,
      lastTileX: Math.floor(startX),
      lastTileY: Math.floor(startY),
      player: {
        x: startX,
        y: startY,
        hp: 5,
        maxHp: 5,
        atkLevel: 1,
        hasTorch: false,
        kills: 0,
        hitFlash: 0,
        facingAngle: 0
      }
    };

    pipe.reset(keys);

    if (MENU) MENU.hidden = true;
    if (GAME_HUD) GAME_HUD.hidden = false;
    if (SAVE_BTN) SAVE_BTN.disabled = false;
    if (QUIT_BTN) QUIT_BTN.disabled = false;

    updateHUD();
    persistSession(keys);

    if (animFrameId) {
      cancelAnimationFrame(animFrameId);
    }
    lastTickTime = performance.now();
    animFrameId = requestAnimationFrame(gameLoop);
  }

  var lastTickTime = 0;

  function gameLoop(now) {
    if (!state) return;

    var dt = Math.min(0.1, (now - lastTickTime) / 1000);
    lastTickTime = now;

    update(dt);
    render();

    if (!state.won) {
      animFrameId = requestAnimationFrame(gameLoop);
    }
  }

  function update(dt) {
    if (!state || state.won) return;

    var p = state.player;
    var snap = state.snap;
    var rows = snap.rows;
    var mapW = snap.width;
    var mapH = snap.height;

    // 1. WASD Movement
    var mx = 0;
    var my = 0;
    if (keysDown['w'] || keysDown['W'] || keysDown['ArrowUp'] || keysDown['KeyW']) my += 1;
    if (keysDown['s'] || keysDown['S'] || keysDown['ArrowDown'] || keysDown['KeyS']) my -= 1;
    if (keysDown['a'] || keysDown['A'] || keysDown['ArrowLeft'] || keysDown['KeyA']) mx -= 1;
    if (keysDown['d'] || keysDown['D'] || keysDown['ArrowRight'] || keysDown['KeyD']) mx += 1;

    if (mx !== 0 && my !== 0) {
      var invLen = 1.0 / Math.SQRT2;
      mx *= invLen;
      my *= invLen;
    }

    var speed = 6.0;
    var nextX = p.x + mx * speed * dt;
    var nextY = p.y + my * speed * dt;

    // Collision Check
    var radius = 0.28;
    var canMoveX = true;
    var minX = Math.floor(nextX - radius);
    var maxX = Math.floor(nextX + radius);
    var curMinY = Math.floor(p.y - radius);
    var curMaxY = Math.floor(p.y + radius);

    for (var ix = minX; ix <= maxX; ix++) {
      for (var iy = curMinY; iy <= curMaxY; iy++) {
        if (ix < 0 || ix >= mapW || iy < 0 || iy >= mapH || !rows[iy] || rows[iy][ix] === 1 || rows[iy][ix] === 3) {
          canMoveX = false;
          break;
        }
      }
      if (!canMoveX) break;
    }
    if (canMoveX) p.x = nextX;

    var canMoveY = true;
    var curMinX = Math.floor(p.x - radius);
    var curMaxX = Math.floor(p.x + radius);
    var minY = Math.floor(nextY - radius);
    var maxY = Math.floor(nextY + radius);

    for (var jx = curMinX; jx <= curMaxX; jx++) {
      for (var jy = minY; jy <= maxY; jy++) {
        if (jx < 0 || jx >= mapW || jy < 0 || jy >= mapH || !rows[jy] || rows[jy][jx] === 1 || rows[jy][jx] === 3) {
          canMoveY = false;
          break;
        }
      }
      if (!canMoveY) break;
    }
    if (canMoveY) p.y = nextY;

    // Track Tile Step Transitions
    var curTileX = Math.floor(p.x);
    var curTileY = Math.floor(p.y);
    if (curTileX !== state.lastTileX || curTileY !== state.lastTileY) {
      state.steps++;
      state.lastTileX = curTileX;
      state.lastTileY = curTileY;
      updateHUD();
    }

    // Aim Angle
    var targetWorld = camera.screenToWorld(mouse.screenX, mouse.screenY, mapH);
    mouse.worldX = targetWorld.x;
    mouse.worldY = targetWorld.y;
    p.facingAngle = Math.atan2(targetWorld.y - p.y, targetWorld.x - p.x);

    if (p.hitFlash > 0) p.hitFlash--;

    // 2. Update Subsystems
    var vw = CANVAS.clientWidth;
    var vh = CANVAS.clientHeight;
    camera.update(p.x, p.y, mapW, mapH, vw, vh);

    items.update(p, function (item) {
      if (item.type === 'TORCH') {
        p.hasTorch = true;
        combat.spawnPopup(p.x, p.y + 0.6, '+TORCH ILLUMINATION', '#ff9900');
      } else if (item.type === 'HEALTH') {
        p.hp = Math.min(p.maxHp, p.hp + 1);
        combat.spawnPopup(p.x, p.y + 0.6, '+1 VITALITY', '#ff3344');
      } else if (item.type === 'ATTACK') {
        p.atkLevel += 1;
        combat.spawnPopup(p.x, p.y + 0.6, '+ATK FIREPOWER', '#ffdd00');
      }
      camera.shake(3);
      updateHUD();
    });

    combat.update(rows, mapW, mapH, monsterAI.monsters, function () {
      p.kills++;
      camera.shake(5);
      updateHUD();
    });

    monsterAI.update(p, rows, mapW, mapH, function (damage) {
      p.hp = Math.max(0, p.hp - damage);
      p.hitFlash = 10;
      combat.spawnPopup(p.x, p.y + 0.5, '-1 VITALITY', '#ff2200');
      camera.shake(10);
      updateHUD();
      if (p.hp <= 0) {
        handleSettlement(false);
      }
    }, camera);

    weather.update(vw, vh);

    // 3. Goal Detection
    if (snap.goal) {
      var gdx = p.x - (snap.goal[0] + 0.5);
      var gdy = p.y - (snap.goal[1] + 0.5);
      if (gdx * gdx + gdy * gdy < 0.5) {
        handleSettlement(true);
      }
    }
  }

  /* Settlement: directly cleans up and returns to the main menu */
  function handleSettlement(victory) {
    if (state.won) return;
    state.won = true;
    clearStoredSession();
    setTimeout(function () {
      backToMenu();
    }, 200);
  }

  /* ---- Rendering Pipeline ------------------------------------------------ */
  function render() {
    if (!state) return;

    var dpr = window.devicePixelRatio || 1;
    var vw = CANVAS.clientWidth;
    var vh = CANVAS.clientHeight;
    var bw = Math.max(1, Math.round(vw * dpr));
    var bh = Math.max(1, Math.round(vh * dpr));

    if (CANVAS.width !== bw || CANVAS.height !== bh) {
      CANVAS.width = bw;
      CANVAS.height = bh;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    var snap = state.snap;
    var mapW = snap.width;
    var mapH = snap.height;
    var s = camera.scale;

    // 1. Clear Stage
    ctx.fillStyle = '#06080b';
    ctx.fillRect(0, 0, vw, vh);

    // 2. Draw World Tiles
    var palette = {};
    var legend = snap.legend || [];
    for (var l = 0; l < legend.length; l++) {
      var leg = legend[l];
      palette[leg.id] = { glyph: leg.glyph, fg: leg.fg, bg: leg.bg };
    }

    var col0 = Math.max(0, Math.floor(camera.x / s));
    var col1 = Math.min(mapW - 1, Math.ceil((camera.x + vw) / s));
    var row0 = Math.max(0, Math.floor(camera.y / s));
    var row1 = Math.min(mapH - 1, Math.ceil((camera.y + vh) / s));

    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    for (var row = row0; row <= row1; row++) {
      var y = mapH - 1 - row;
      var rowTiles = snap.rows[y];
      if (!rowTiles) continue;

      var py0 = Math.floor(row * s - camera.y + camera.shakeOffsetY);
      var py1 = Math.floor((row + 1) * s - camera.y + camera.shakeOffsetY);
      var cellH = py1 - py0;
      var drawGlyph = cellH >= 4;
      if (drawGlyph) {
        ctx.font = cellH + 'px Consolas, monospace';
      }

      for (var col = col0; col <= col1; col++) {
        var px0 = Math.floor(col * s - camera.x + camera.shakeOffsetX);
        var px1 = Math.floor((col + 1) * s - camera.x + camera.shakeOffsetX);
        var cellW = px1 - px0;
        var e = palette[rowTiles[col]] || { bg: '#000', fg: '#fff', glyph: ' ' };

        ctx.fillStyle = e.bg;
        ctx.fillRect(px0, py0, cellW, cellH);

        if (drawGlyph && e.glyph && e.glyph !== ' ' && e.glyph !== '@') {
          ctx.fillStyle = e.fg;
          ctx.fillText(e.glyph, (px0 + px1) / 2, (py0 + py1) / 2 + 1);
        }
      }
    }

    // 3. Draw Items
    items.render(ctx, mapH, camera);

    // 4. Draw Goal Portal Marker (★)
    if (snap.goal) {
      var gCenter = camera.worldToScreen(snap.goal[0] + 0.5, snap.goal[1] + 0.5, mapH);
      var pulse = (Math.sin(performance.now() * 0.006) + 1) * 0.5;

      var gSize = s * (0.4 + pulse * 0.1);
      ctx.save();
      ctx.translate(gCenter.x, gCenter.y);
      ctx.strokeStyle = 'rgba(255, 216, 0, 0.4)';
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(0, -gSize); ctx.lineTo(gSize, 0); ctx.lineTo(0, gSize); ctx.lineTo(-gSize, 0);
      ctx.closePath();
      ctx.stroke();

      ctx.strokeStyle = '#ffd800';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(0, -s * 0.35); ctx.lineTo(s * 0.35, 0); ctx.lineTo(0, s * 0.35); ctx.lineTo(-s * 0.35, 0);
      ctx.closePath();
      ctx.stroke();

      ctx.fillStyle = '#ffd800';
      ctx.font = 'bold ' + Math.floor(s * 0.75) + 'px Consolas, monospace';
      ctx.fillText('★', 0, 1);
      ctx.restore();
    }

    // 5. Draw Monsters & Green Slash Effects
    monsterAI.render(ctx, mapH, camera);

    // 6. Draw Protagonist: Iconic @ Character with Aim Pointer & Underglow
    var p = state.player;
    var pScr = camera.worldToScreen(p.x, p.y, mapH);
    ctx.save();
    ctx.translate(pScr.x, pScr.y);

    var aimLen = s * 0.8;
    ctx.strokeStyle = 'rgba(255, 77, 38, 0.55)';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(Math.cos(-p.facingAngle) * aimLen, Math.sin(-p.facingAngle) * aimLen);
    ctx.stroke();

    ctx.fillStyle = '#ff4d26';
    ctx.fillRect(Math.cos(-p.facingAngle) * aimLen - 1.5, Math.sin(-p.facingAngle) * aimLen - 1.5, 3, 3);

    ctx.fillStyle = p.hitFlash > 0 ? 'rgba(255, 50, 50, 0.4)' : 'rgba(255, 77, 38, 0.25)';
    ctx.beginPath();
    ctx.rect(-s * 0.35, -s * 0.35, s * 0.7, s * 0.7);
    ctx.fill();

    ctx.fillStyle = p.hitFlash > 0 ? '#ff3344' : '#ffffff';
    ctx.font = 'bold ' + Math.floor(s * 0.88) + 'px Consolas, monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('@', 0, 1);

    ctx.restore();

    // 7. Combat Bullets & VFX
    combat.render(ctx, mapH, camera);

    // 8. 2D Dynamic Lighting & Fog of War
    lighting.render(ctx, vw, vh, p, items.items, mapW, mapH, camera);

    // 9. Rain Weather Overlay
    weather.render(ctx, vw, vh);
  }

  /* ---- HUD Updates ------------------------------------------------------- */
  function updateHUD() {
    if (!state) return;
    var p = state.player;
    var snap = state.snap;

    if (HEARTS_CONTAINER) UI.renderHearts(HEARTS_CONTAINER, p.hp, p.maxHp);

    if (HUD_ATK) HUD_ATK.textContent = 'ATK // LV.' + p.atkLevel;
    if (HUD_TORCH) {
      HUD_TORCH.textContent = p.hasTorch ? 'ILLUMINATION // TORCH' : 'ILLUMINATION // BASE';
      HUD_TORCH.style.color = p.hasTorch ? '#ffaa00' : '#ff8866';
      HUD_TORCH.style.borderLeftColor = p.hasTorch ? '#ffaa00' : '#ff4d26';
    }
    if (HUD_KILLS) HUD_KILLS.textContent = 'HOSTILES DOWN // ' + p.kills;

    if (snap.goal && HUD_DIST) {
      var dx = p.x - (snap.goal[0] + 0.5);
      var dy = p.y - (snap.goal[1] + 0.5);
      var dist = Math.sqrt(dx * dx + dy * dy);
      HUD_DIST.textContent = 'OBJ DIST // ' + dist.toFixed(1) + 'm';
    }
  }

  /* ---- Input Event Listeners --------------------------------------------- */
  window.addEventListener('keydown', function (ev) {
    if (ev.repeat) return;
    keysDown[ev.key] = true;
    keysDown[ev.code] = true;
  });

  window.addEventListener('keyup', function (ev) {
    keysDown[ev.key] = false;
    keysDown[ev.code] = false;
  });

  window.addEventListener('mousemove', function (ev) {
    mouse.screenX = ev.clientX;
    mouse.screenY = ev.clientY;
    if (CURSOR_CROSSHAIR) {
      CURSOR_CROSSHAIR.style.left = ev.clientX + 'px';
      CURSOR_CROSSHAIR.style.top = ev.clientY + 'px';
    }
  });

  window.addEventListener('mousedown', function (ev) {
    if (!state || state.won || quitting) return;
    if (ev.button === 0) {
      if (ev.target && (ev.target.tagName === 'BUTTON' || ev.target.closest('button') || ev.target.closest('#menu'))) return;
      var target = camera.screenToWorld(ev.clientX, ev.clientY, state.snap.height);
      combat.shoot(state.player.x, state.player.y, target.x, target.y, state.player.atkLevel);
      camera.shake(2.2);
    }
  });

  window.addEventListener('wheel', function (ev) {
    if (!state) return;
    ev.preventDefault();
    if (ev.deltaY < 0) {
      camera.targetScale = Math.min(64, camera.targetScale * 1.15);
    } else {
      camera.targetScale = Math.max(16, camera.targetScale / 1.15);
    }
  }, { passive: false });

  if (WEATHER_TOGGLE) {
    WEATHER_TOGGLE.addEventListener('change', function () {
      if (WEATHER_LABEL) {
        WEATHER_LABEL.textContent = WEATHER_TOGGLE.checked ? 'ACTIVE // 开启' : 'MUTED // 关闭';
      }
    });
  }

  if (RAIN_BTN) {
    RAIN_BTN.addEventListener('click', function () {
      if (weather) {
        weather.toggle();
        RAIN_BTN.textContent = weather.enabled ? '[ RAIN // ACTIVE ]' : '[ RAIN // MUTED ]';
      }
    });
  }

  /* ---- Start / Continue / Load / Save Handlers --------------------------- */
  if (START_BTN) {
    START_BTN.addEventListener('click', function () {
      var sizeInput = document.querySelector('input[name="size"]:checked');
      var size = sizeInput ? sizeInput.value : 'medium';
      var text = SEED_INPUT ? SEED_INPUT.value.trim() : '';
      var seedText = text === '' ? randSeedText() : text;

      if (!/^\d+$/.test(seedText) || seedText.length > 19) {
        report('Seed 需为非负整数；留空则随机');
        return;
      }
      report('');

      var session = { kind: 'new', size: size, seedText: seedText };
      postSession(withKeys(baseBody(session), ''))
        .then(function (snap) {
          adoptGame(session, snap, '', 0);
        })
        .catch(function (err) {
          report('开始失败：' + err.message);
        });
    });
  }

  if (CONTINUE_BTN) {
    CONTINUE_BTN.addEventListener('click', function () {
      var stored = readStoredSession();
      if (!stored) {
        report('没有可继续的一局：请先开一局');
        return;
      }
      report('');
      var session = stored.session;
      postSession(withKeys(baseBody(session), stored.keys))
        .then(function (snap) {
          if (snap.saveMissing) {
            clearStoredSession();
            refreshContinue();
            report('存档已不存在');
            return;
          }
          adoptGame(session, snap, stored.keys, stored.steps);
        })
        .catch(function (err) {
          report('继续失败：' + err.message);
        });
    });
  }

  if (LOAD_BTN) {
    LOAD_BTN.addEventListener('click', function () {
      report('');
      postSession(withKeys(baseBody({ kind: 'load' }), ''))
        .then(function (snap) {
          if (snap.saveMissing) {
            report('没有存档：请先在游戏里保存');
            return;
          }
          adoptGame({ kind: 'load' }, snap, '', 0);
        })
        .catch(function (err) {
          report('读档失败：' + err.message);
        });
    });
  }

  function saveAndQuit() {
    if (!state || quitting) return;
    var keys = pipe.appliedKeys() + ':Q';
    quitting = true;
    if (SAVE_BTN) SAVE_BTN.disabled = true;
    if (QUIT_BTN) QUIT_BTN.disabled = true;
    postSession(withKeys(baseBody(state.session), keys))
      .then(function () {
        quitting = false;
        backToMenu();
      })
      .catch(function () {
        quitting = false;
        backToMenu();
      });
  }

  function quitGame() {
    if (!state || quitting) return;
    clearStoredSession();
    backToMenu();
  }

  if (SAVE_BTN) SAVE_BTN.addEventListener('click', saveAndQuit);
  if (QUIT_BTN) QUIT_BTN.addEventListener('click', quitGame);

  function backToMenu() {
    if (animFrameId) cancelAnimationFrame(animFrameId);
    state = null;
    pipe.reset('');
    if (GAME_HUD) GAME_HUD.hidden = true;
    if (MENU) MENU.hidden = false;
    refreshContinue();
  }

  refreshContinue();
})();
