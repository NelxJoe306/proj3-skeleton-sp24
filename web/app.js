/* proj3C web client entry: main menu -> POST /api/replay -> first frame on the canvas.

   The server is stateless (ADR-0004): every request re-sends the whole Session, and a
   "new" game is the pure function (size, seed, keys) -> world. So this file sends the
   seed as an exact decimal string (JSON number would lose precision past 2^53).

   State machine: MENU -> PLAYING. Ticket 06 appends WASD to the key history and re-POSTs
   the same session; ticket 07 swaps the overviewCamera for a zoom/pan camera; ticket 09
   adds Continue (kind:load). view.js/render.js stay DOM-free; this file owns DOM + fetch. */
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

  var state = null; // { snap, view, seedText, sizeLabel } while PLAYING

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
    startGame({ kind: 'new', size: size, seed: seedText, keys: '' }, seedText, size);
  });

  function startGame(body, seedText, size) {
    MENU.hidden = true;
    fetch('/api/replay', {
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
    }).then(function (snap) {
      enterGame(snap, seedText, size);
    }).catch(function (err) {
      MENU.hidden = false;
      report('开始失败：' + err.message);
    });
  }

  function enterGame(snap, seedText, size) {
    state = { snap: snap, seedText: seedText, sizeLabel: SIZE_LABEL[size] || size };
    STATUS.hidden = false;
    STATUS.textContent = state.sizeLabel + ' · seed ' + seedText;
    layoutAndDraw();
  }

  /* Whole-map overview on the full canvas (ticket 05). Ticket 07 replaces the camera. */
  function layoutAndDraw() {
    if (!state) {
      return;
    }
    resizeCanvas();
    var vw = state.vw;
    var vh = state.vh;
    var view = View.overviewCamera(vw, vh, state.snap.width, state.snap.height);
    state.view = view;
    Render.drawFrame(ctx, vw, vh, state.snap, view);
  }

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
