/* proj3C web client entry: menu -> POST /api/replay -> first frame; then WASD drives
   the session (ticket 06). view.js/render.js/keypipe.js stay DOM-free; this owns the
   DOM, the fetch, and the state machine MENU -> PLAYING.

   The server is stateless (ADR-0004): a "new" game is the pure function
   (size, seed, keys) -> world, so movement re-POSTs the whole cumulative key history.
   Keys stream through a KeyPipe pipeline (one request in flight, no lost presses);
   every acknowledged snapshot is drawn whole-map. The seed travels as an exact decimal
   string (JSON numbers lose precision past 2^53). */
(function () {
  'use strict';

  var MENU = document.getElementById('menu');
  var FORM = document.getElementById('new-game-form');
  var SEED_INPUT = document.getElementById('seed');
  var ERROR = document.getElementById('menu-error');
  var CANVAS = document.getElementById('world');
  var STATUS = document.getElementById('status');
  var ctx = CANVAS.getContext('2d');

  var SIZE_LABEL = { small: '小 40×40', medium: '中 80×80', large: '大 120×120' };
  var MOVE_KEYS = { w: 1, a: 1, s: 1, d: 1 };

  var state = null; // { snap, view, size, seedText, sizeLabel } while PLAYING
  var pipe = KeyPipe.createPipeline(function (keys) {
    // send() resolves only once the acknowledged snapshot is drawn, so the pipeline
    // commits one frame at a time, strictly in order.
    return postSession({
      kind: 'new',
      size: state.size,
      seed: state.seedText,
      keys: keys
    }).then(function (snap) {
      if (!state) {
        return;
      }
      state.snap = snap;
      layoutAndDraw();
    });
  }, function (err) {
    console.error('move replay failed:', err); // pending keys stay queued; next key retries
  });

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
    MENU.hidden = true;
    STATUS.hidden = false;
    STATUS.textContent = SIZE_LABEL[size] + ' · seed ' + seedText + ' · 用 WASD 移动';
    postSession({ kind: 'new', size: size, seed: seedText, keys: '' })
      .then(function (snap) {
        state = {
          snap: snap,
          size: size,
          seedText: seedText,
          sizeLabel: SIZE_LABEL[size],
          cameraS: null // null = start at the whole-map overview (ticket 07)
        };
        pipe.reset('');
        layoutAndDraw();
      })
      .catch(function (err) {
        MENU.hidden = false;
        STATUS.hidden = true;
        report('开始失败：' + err.message);
      });
  }

  window.addEventListener('keydown', function (ev) {
    if (!state || state.snap.won) {
      return; // menu, no game, or the avatar already reached the goal (engine froze)
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
})();
