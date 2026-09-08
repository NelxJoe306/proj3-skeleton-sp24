/* ==========================================================================
   SIGNALIS Industrial Angular Cybernetic Eye (No Circles)
   Features: Polygonal titanium visor, Octagonal aperture blades, Diamond sensor core,
             Dynamic real-time mouse cursor gaze tracking
   ========================================================================== */
(function (root) {
  'use strict';

  var mouseX = 0;
  var mouseY = 0;
  var targetLookX = 0;
  var targetLookY = 0;
  var currentLookX = 0;
  var currentLookY = 0;

  function setMouse(nx, ny) {
    // nx, ny in range -1.0 .. 1.0 relative to canvas center
    targetLookX = Math.max(-1, Math.min(1, nx));
    targetLookY = Math.max(-1, Math.min(1, ny));
  }

  function drawAngularEye(ctx, width, height, time) {
    if (!ctx) return;
    ctx.save();
    ctx.clearRect(0, 0, width, height);

    // Smooth inertia tracking towards mouse target
    currentLookX += (targetLookX - currentLookX) * 0.08;
    currentLookY += (targetLookY - currentLookY) * 0.08;

    var cx = width / 2;
    var cy = height / 2;
    var scale = Math.min(width / 360, height / 180);

    ctx.translate(cx, cy);
    ctx.scale(scale, scale);

    // 1. Dark Industrial Housing Background
    ctx.fillStyle = '#080a0f';
    ctx.fillRect(-180, -90, 360, 180);

    // Subtle background technical grid lines
    ctx.strokeStyle = 'rgba(255, 77, 38, 0.12)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(-160, 0); ctx.lineTo(160, 0);
    ctx.moveTo(0, -75); ctx.lineTo(0, 75);
    ctx.stroke();

    // 2. Outer Hexagonal Armor Visor Frame (Strictly Angular)
    var breath = Math.sin(time * 1.5) * 1.5;
    var blink = Math.sin(time * 0.4);
    var isBlink = blink > 0.96;
    var openRatio = isBlink ? 0.08 : (1.0 + breath * 0.04);

    var hx = 135;
    var hy = 46 * openRatio;

    // Outer Sclera Polygon
    ctx.beginPath();
    ctx.moveTo(-hx, 0);
    ctx.lineTo(-hx * 0.65, -hy);
    ctx.lineTo(hx * 0.65, -hy);
    ctx.lineTo(hx, 0);
    ctx.lineTo(hx * 0.65, hy);
    ctx.lineTo(-hx * 0.65, hy);
    ctx.closePath();

    ctx.fillStyle = '#161c26';
    ctx.fill();
    ctx.strokeStyle = '#283344';
    ctx.lineWidth = 2.5;
    ctx.stroke();

    // 3. Sensor Core Tracking Position (Bounded within visor)
    var lookShiftX = currentLookX * 24;
    var lookShiftY = currentLookY * 12 * openRatio;

    ctx.save();
    // Clip inside the outer hexagonal visor
    ctx.beginPath();
    ctx.moveTo(-hx, 0);
    ctx.lineTo(-hx * 0.65, -hy);
    ctx.lineTo(hx * 0.65, -hy);
    ctx.lineTo(hx, 0);
    ctx.lineTo(hx * 0.65, hy);
    ctx.lineTo(-hx * 0.65, hy);
    ctx.closePath();
    ctx.clip();

    // Shutter Aperture Plates (Segmented Octagon)
    var ox = lookShiftX;
    var oy = lookShiftY;
    var rOuter = 32;

    ctx.beginPath();
    ctx.moveTo(ox - rOuter, oy);
    ctx.lineTo(ox - rOuter * 0.7, oy - rOuter * 0.7);
    ctx.lineTo(ox, oy - rOuter);
    ctx.lineTo(ox + rOuter * 0.7, oy - rOuter * 0.7);
    ctx.lineTo(ox + rOuter, oy);
    ctx.lineTo(ox + rOuter * 0.7, oy + rOuter * 0.7);
    ctx.lineTo(ox, oy + rOuter);
    ctx.lineTo(ox - rOuter * 0.7, oy + rOuter * 0.7);
    ctx.closePath();

    ctx.fillStyle = '#0b0f16';
    ctx.fill();
    ctx.strokeStyle = '#ff4d26';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // Inner Diamond Optic Sensor (Glowing Red Sensor Core)
    var rInner = 14;
    ctx.beginPath();
    ctx.moveTo(ox, oy - rInner);
    ctx.lineTo(ox + rInner * 1.2, oy);
    ctx.lineTo(ox, oy + rInner);
    ctx.lineTo(ox - rInner * 1.2, oy);
    ctx.closePath();

    ctx.fillStyle = '#ff2200';
    ctx.fill();
    ctx.strokeStyle = '#ff7755';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // Crisp Center Rectangular Aperture Slit
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(ox - 2.5, oy - 2.5, 5, 5);

    // Dynamic Tracking Laser Tick
    ctx.strokeStyle = 'rgba(255, 220, 0, 0.7)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(ox - 8, oy); ctx.lineTo(ox + 8, oy);
    ctx.moveTo(ox, oy - 8); ctx.lineTo(ox, oy + 8);
    ctx.stroke();

    ctx.restore(); // end clip

    // 4. Industrial Eyebrow Armor Contours (Sharp 45° Lines)
    ctx.strokeStyle = '#ff4d26';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(-155, -42 - breath);
    ctx.lineTo(-60, -56 - breath);
    ctx.lineTo(70, -52 - breath);
    ctx.lineTo(155, -36 - breath);
    ctx.stroke();

    ctx.strokeStyle = '#222b38';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(-145, 42);
    ctx.lineTo(0, 54);
    ctx.lineTo(145, 40);
    ctx.stroke();

    // Corner Status Indicator Bar
    ctx.fillStyle = (Math.floor(time * 4) % 2 === 0) ? '#ff4d26' : '#551508';
    ctx.fillRect(120, -42, 8, 3);

    // Corner Bracket Decals
    ctx.strokeStyle = '#ff4d26';
    ctx.lineWidth = 1;
    ctx.strokeRect(-170, -80, 10, 10);
    ctx.strokeRect(160, -80, 10, 10);

    ctx.restore();
  }

  function init(canvasOrId) {
    var canvas = typeof canvasOrId === 'string' ? document.getElementById(canvasOrId) : canvasOrId;
    if (!canvas || !canvas.getContext) return null;

    var ctx = canvas.getContext('2d');
    var running = true;
    var startTime = performance.now();

    // Track mouse pointer across window for the eye
    function onMouseMove(ev) {
      var rect = canvas.getBoundingClientRect();
      var canvasCenterX = rect.left + rect.width / 2;
      var canvasCenterY = rect.top + rect.height / 2;
      var nx = (ev.clientX - canvasCenterX) / (window.innerWidth / 2);
      var ny = (ev.clientY - canvasCenterY) / (window.innerHeight / 2);
      setMouse(nx, ny);
    }

    window.addEventListener('mousemove', onMouseMove);

    function loop(now) {
      if (!running) return;
      var t = (now - startTime) / 1000;
      drawAngularEye(ctx, canvas.width, canvas.height, t);
      requestAnimationFrame(loop);
    }
    requestAnimationFrame(loop);

    return {
      destroy: function () {
        running = false;
        window.removeEventListener('mousemove', onMouseMove);
      },
      setMouse: setMouse
    };
  }

  var api = {
    draw: drawAngularEye,
    init: init,
    setMouse: setMouse
  };

  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }
  root.SignalisEye = api;
})(typeof self !== 'undefined' ? self : this);
