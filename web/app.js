/* proj3C web client entry. Menu -> POST /api/replay -> first frame; WASD drives the
   session (ticket 06); a HUD reports world/seed, steps and avatar->goal distance and a
   banner announces the win (ticket 08); Save&Quit / Quit / Load / Continue close the
   progress loop (ticket 09). view.js/render.js/keypipe.js stay DOM-free; this owns the
   DOM, the fetch, the state machine MENU -> PLAYING, and the browser-side session.

   The server is stateless (ADR-0004): a "new" game is the pure function (size, seed,
   keys) -> world and a "load" is (server save file) -> world, so every step re-POSTs
   the whole cumulative key history and the current frame is *exactly* replay(session).
   Keys stream through a KeyPipe pipeline (one request in flight, no lost presses);
   every acknowledged snapshot is drawn. The seed travels as an exact decimal string.

   A "session" here is the descriptor whose replay reproduces the frame on screen:
     {kind:'new',  size, seedText} + keys    — started from the menu
     {kind:'load'}               + keys    — resumed from the server save file
   Movement appends one key to the cumulative stream. Continue saves that session
   (kind + base fields + keys + steps) to localStorage on every acknowledged frame, so
   a refresh can replay the identical session and land on the identical frame.

   Save & Quit appends ':Q' to the acknowledged keys and replays once: the engine
   writes its V1 save file as a pure side effect (ticket 03) and truncates. Continue is
   left at the same spot (file and Continue agree), so both the menu's Continue and its
   Load resume that frame. Plain Quit abandons the live session — it clears Continue and
   leaves the file untouched, so Load still returns the last explicit save. The engine
   stays the source of truth:
   load-missing arrives as snapshot.saveMissing, which the menu reports, never crashes. */
(function () {
  'use strict';

  var STORE_KEY = 'proj3c.session.v1'; // the localStorage entry for Continue

  var MENU = document.getElementById('menu');
  var FORM = document.getElementById('new-game-form');
  var SEED_INPUT = document.getElementById('seed');
  var ERROR = document.getElementById('menu-error');
  var CANVAS = document.getElementById('world');
  var ctx = CANVAS.getContext('2d');

  var HUD = document.getElementById('hud');
  var HUD_WORLD = document.getElementById('hud-world');
  var HUD_STEPS = document.getElementById('hud-steps');
  var HUD_DIST = document.getElementById('hud-dist');
  var BANNER = document.getElementById('banner');
  var BANNER_TEXT = document.getElementById('banner-text');

  var CONTINUE_BTN = document.getElementById('continue-btn');
  var LOAD_BTN = document.getElementById('load-btn');
  var GAME_BTNS = document.getElementById('game-btns');
  var SAVE_BTN = document.getElementById('save-btn');
  var QUIT_BTN = document.getElementById('quit-btn');

  var SIZE_LABEL = { small: '小 40×40', medium: '中 80×80', large: '大 120×120' };
  var TIER_BY_WIDTH = { 40: '小', 80: '中', 120: '大' };
  var MOVE_KEYS = { w: 1, a: 1, s: 1, d: 1 };

  // state = { snap, view, session, cameraS, steps } while PLAYING. session is the base
  // descriptor whose cumulative keys (held by the pipeline) reproduce the frame.
  var state = null;
  var quitting = false; // true while a Save&Quit POST is in flight: block new moves

  var pipe = KeyPipe.createPipeline(function (keys) {
    // send() resolves only once the acknowledged snapshot is drawn, so the pipeline
    // commits one frame at a time, strictly in order. `keys` is the full cumulative
    // stream that produced the incoming snapshot — it becomes the stored Continue point.
    return postSession(withKeys(baseBody(state.session), keys))
        .then(function (snap) {
          if (!state) {
            return;
          }
          countStep(snap);
          state.snap = snap;
          layoutAndDraw();
          updateHUD();
          persistSession(keys);
        });
  }, function (err) {
    console.error('move replay failed:', err); // pending keys stay queued; next key retries
  });

  function baseBody(session) {
    // The load replay carries size/seed itself (from the server file); only a new game
    // needs them sent. Returns a fresh object so callers can add keys.
    if (session.kind === 'new') {
      return { kind: 'new', size: session.size, seed: session.seedText };
    }
    return { kind: 'load' };
  }

  function withKeys(body, keys) {
    body.keys = keys;
    return body;
  }

  /* A "step" is an acknowledged move that actually displaced the avatar (engine coords
     are truth). Pressing into a wall or edge leaves the avatar put, so it isn't a step.
     The first frame of a fresh episode leaves the avatar put too, so no special case. */
  function countStep(snap) {
    var prev = state.snap;
    if (!prev || !prev.avatar || !snap.avatar) {
      return;
    }
    if (snap.avatar[0] !== prev.avatar[0] || snap.avatar[1] !== prev.avatar[1]) {
      state.steps += 1;
    }
  }

  function randSeedText() {
    if (window.crypto && crypto.getRandomValues) {
      var a = new Uint32Array(1);
      crypto.getRandomValues(a);
      return String(a[0]); // 0..4294967295 — exact as a decimal string
    }
    return String(Math.floor(Math.random() * 0x7fffffff));
  }

  function report(msg) {
    ERROR.textContent = msg;
    ERROR.hidden = !msg;
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
    });
  }

  /* ---- Continue: localStorage session ------------------------------------ */

  function persistSession(keys) {
    if (!state) {
      return;
    }
    var stored = { kind: state.session.kind, keys: keys || '', steps: state.steps };
    if (state.session.kind === 'new') {
      stored.size = state.session.size;
      stored.seedText = state.session.seedText;
    }
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(stored));
    } catch (_) {
      // private mode / quota: Continue simply won't survive a refresh
    }
  }

  function clearStoredSession() {
    try {
      localStorage.removeItem(STORE_KEY);
    } catch (_) {
      /* ignore */
    }
  }

  /* Reads and validates the stored Continue session, or null. */
  function readStoredSession() {
    var o;
    try {
      o = JSON.parse(localStorage.getItem(STORE_KEY));
    } catch (_) {
      return null;
    }
    if (!o || typeof o !== 'object') {
      return null;
    }
    var keys = typeof o.keys === 'string' ? o.keys : '';
    var steps = Number.isFinite(o.steps) && o.steps >= 0 ? Math.floor(o.steps) : 0;
    if (o.kind === 'new') {
      if (!/^(small|medium|large)$/.test(o.size || '')
          || typeof o.seedText !== 'string' || !/^\d+$/.test(o.seedText)) {
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
    // No stored session: Continue dims but stays clickable so it can explain itself.
    CONTINUE_BTN.classList.toggle('off', !readStoredSession());
  }

  /* ---- installing a live game -------------------------------------------- */

  /* Enters PLAYING from a successful initial replay. `keys` is the cumulative stream
     that produced `snap` ('' for a brand-new or freshly loaded game, the stored stream
     for a Continue) and becomes the pipeline's committed baseline, so each later move
     re-replays the whole session. */
  function adoptGame(session, snap, keys, steps) {
    state = {
      snap: snap,
      session: session,
      cameraS: null, // null = start at the whole-map overview (ticket 07)
      steps: steps
    };
    pipe.reset(keys);
    SAVE_BTN.disabled = false;
    QUIT_BTN.disabled = false;
    MENU.hidden = true;
    HUD.hidden = false;
    GAME_BTNS.hidden = false;
    BANNER.hidden = true;
    layoutAndDraw();
    updateHUD();
    persistSession(keys);
  }

  FORM.addEventListener('submit', function (ev) {
    ev.preventDefault();
    var size = document.querySelector('input[name="size"]:checked').value;
    var text = SEED_INPUT.value.trim();
    var seedText = text === '' ? randSeedText() : text;
    if (!/^\d+$/.test(seedText) || seedText.length > 19
        || (seedText.length === 19 && seedText > '9223372036854775807')) {
      report('Seed 需为非负整数；留空则随机');
      return;
    }
    report('');
    beginNewGame(size, seedText);
  });

  function beginNewGame(size, seedText) {
    var session = { kind: 'new', size: size, seedText: seedText };
    postSession(withKeys(baseBody(session), ''))
      .then(function (snap) {
        adoptGame(session, snap, '', 0);
      })
      .catch(function (err) {
        report('开始失败：' + err.message);
      });
  }

  CONTINUE_BTN.addEventListener('click', function () {
    var stored = readStoredSession();
    if (!stored) {
      report('没有可继续的一局：先开一局 New Game，途中刷新后这里就能续上');
      return;
    }
    report('');
    var session = stored.session;
    postSession(withKeys(baseBody(session), stored.keys))
      .then(function (snap) {
        if (snap.saveMissing) {
          // A load-continue whose server save file has gone (e.g. cleaned): report,
          // drop the stale Continue, don't enter a blank world.
          clearStoredSession();
          refreshContinue();
          report('存档已不存在，无法继续——请重新开一局');
          return;
        }
        adoptGame(session, snap, stored.keys, stored.steps);
      })
      .catch(function (err) {
        report('继续失败：' + err.message);
      });
  });

  LOAD_BTN.addEventListener('click', function () {
    report('');
    postSession(withKeys(baseBody({ kind: 'load' }), ''))
      .then(function (snap) {
        if (snap.saveMissing) {
          report('没有存档：先在游戏里点「保存并退出」，再回来读档');
          return;
        }
        adoptGame({ kind: 'load' }, snap, '', 0);
      })
      .catch(function (err) {
        report('读档失败：' + err.message);
      });
  });

  /* ---- leaving a game ---------------------------------------------------- */

  /* Save & Quit: replay the acknowledged keys once with ':Q' appended so the engine
     writes its V1 save file (pure side effect, ticket 03), then go to the menu. The
     keys we send are the pipeline's committed stream = the frame on screen; any move
     still in flight was pressed but never drawn, so it is (correctly) not saved. The
     local Continue is left pointing at the same spot (Save&Quit is additive to it — the
     file and Continue agree), so both menu paths resume the identical frame. Plain Quit
     below is the one that abandons the session. */
  function saveAndQuit() {
    if (!state || quitting) {
      return;
    }
    var keys = pipe.appliedKeys() + ':Q';
    quitting = true;
    SAVE_BTN.disabled = true;
    QUIT_BTN.disabled = true;
    postSession(withKeys(baseBody(state.session), keys))
      .then(function () {
        quitting = false;
        backToMenu();
      })
      .catch(function (err) {
        quitting = false;
        SAVE_BTN.disabled = false;
        QUIT_BTN.disabled = false;
        window.alert('保存失败，仍在本局：' + err.message);
      });
  }

  function quitGame() {
    if (!state || quitting) {
      return;
    }
    clearStoredSession(); // abandoning the live session: Continue must not resurrect it
    backToMenu();
  }

  SAVE_BTN.addEventListener('click', saveAndQuit);
  QUIT_BTN.addEventListener('click', quitGame);

  function backToMenu() {
    state = null;
    pipe.reset('');
    HUD.hidden = true;
    GAME_BTNS.hidden = true;
    BANNER.hidden = true;
    MENU.hidden = false;
    refreshContinue();
  }

  document.getElementById('banner-again').addEventListener('click', backToMenu);

  /* ---- input & drawing (tickets 06-08, unchanged behaviour) --------------- */

  window.addEventListener('keydown', function (ev) {
    if (!state || state.snap.won || quitting) {
      return; // menu, no game, already won (engine froze), or saving is underway
    }
    if (ev.ctrlKey || ev.metaKey || ev.altKey) {
      return; // never swallow shortcuts like Ctrl+W
    }
    var t = ev.target;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT'
        || t.isContentEditable)) {
      return; // typing in a field must not move the avatar
    }
    var key = ev.key.toLowerCase();
    if (key.length !== 1 || !MOVE_KEYS[key]) {
      return;
    }
    ev.preventDefault();
    pipe.press(key);
  });

  /* One camera rule drives every level of zoom (ticket 07): aim at the avatar and clamp
     to the map — which degenerates to centring the whole map when it still fits at the
     current scale. Starting cameraS=null draws the whole-map overview; wheel zoom raises
     it; movement acks redraw with the same scale, so the avatar stays centred while
     zoomed and the world scrolls under it. */
  function layoutAndDraw() {
    if (!state) {
      return;
    }
    resizeCanvas();
    var snap = state.snap;
    var vw = state.vw;
    var vh = state.vh;
    var fit = View.fitTileSize(vw, vh, snap.width, snap.height);
    var s = state.cameraS == null ? fit : Math.max(state.cameraS, fit);
    state.view = snap.avatar
        ? View.followCamera(s, vw, vh, snap.width, snap.height, snap.avatar)
        : View.overviewCamera(vw, vh, snap.width, snap.height);
    Render.drawFrame(ctx, vw, vh, snap, state.view);
  }

  window.addEventListener('wheel', function (ev) {
    if (!state) {
      return;
    }
    ev.preventDefault(); // also stops the browser's Ctrl+wheel page zoom (pinch scroll)
    var snap = state.snap;
    var cw = Math.max(1, CANVAS.clientWidth);
    var ch = Math.max(1, CANVAS.clientHeight);
    var fit = View.fitTileSize(cw, ch, snap.width, snap.height);
    var ground = View.groundScale(cw, ch, snap.width, snap.height);
    var cur = state.cameraS == null ? fit : Math.max(state.cameraS, fit);
    state.cameraS = View.zoomStep(cur, ev.deltaY < 0, fit, ground, 1.25);
    layoutAndDraw();
  }, { passive: false });

  function resizeCanvas() {
    var dpr = window.devicePixelRatio || 1;
    state.vw = CANVAS.clientWidth;
    state.vh = CANVAS.clientHeight;
    var bw = Math.max(1, Math.round(state.vw * dpr));
    var bh = Math.max(1, Math.round(state.vh * dpr));
    if (CANVAS.width !== bw) {
      CANVAS.width = bw;
    }
    if (CANVAS.height !== bh) {
      CANVAS.height = bh;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  window.addEventListener('resize', function () {
    if (state) {
      layoutAndDraw();
    }
  });

  /* ---- HUD (ticket 08) ---------------------------------------------------- */

  /* World line for the HUD: dimension tier + grid size, and the seed. A new session
     shows the exact seed string the player chose/rolled; a loaded one shows the seed
     re-derived by the server (from the save file). */
  function hudWorldText() {
    var snap = state.snap;
    var tier = TIER_BY_WIDTH[snap.width] || '';
    var seedText = state.session.kind === 'new' ? state.session.seedText : String(snap.seed);
    return (tier ? tier + ' ' : '') + snap.width + '×' + snap.height + ' · seed ' + seedText;
  }

  function updateHUD() {
    if (!state) {
      return;
    }
    var snap = state.snap;
    HUD_WORLD.textContent = hudWorldText();
    HUD_STEPS.textContent = '步数 ' + state.steps;

    var dist = null;
    if (snap.avatar && snap.goal) {
      var dx = snap.avatar[0] - snap.goal[0];
      var dy = snap.avatar[1] - snap.goal[1];
      dist = Math.sqrt(dx * dx + dy * dy);
    }
    if (snap.won) {
      HUD_DIST.textContent = '已到达 ★';
      HUD_DIST.style.color = '#ffd800';
    } else if (dist === null) {
      HUD_DIST.textContent = '';
      HUD_DIST.style.color = '';
    } else {
      HUD_DIST.textContent = '距 ★ ' + dist.toFixed(1) + ' 格';
      var diag = Math.sqrt(snap.width * snap.width + snap.height * snap.height);
      var t = Math.min(1, dist / diag); // 1 = far (cool) ... 0 = on goal (hot)
      var hue = Math.round(210 * t);    // hue 0 red -> hue 210 blue
      HUD_DIST.style.color = 'hsl(' + hue + ', 85%, 62%)';
    }

    BANNER.hidden = !snap.won;
    if (snap.won) {
      BANNER_TEXT.innerHTML = '到达 ★ 通关！<small>移动已冻结，WASD 不再生效 · 引擎为准</small>';
    }
  }

  refreshContinue(); // reflect any stored Continue session as soon as the menu shows
})();
