// Short sounds made with the browser's Web Audio API, so the app needs no audio files.
// "received" is the two-note bell for incoming money, like the payment apps people already know.
const KEY = 'ershak.sound';
let ctx = null;

export const soundOn = () => {
  try {
    return localStorage.getItem(KEY) !== 'off';
  } catch {
    return true;
  }
};

export const setSoundOn = (on) => {
  try {
    localStorage.setItem(KEY, on ? 'on' : 'off');
  } catch {
    /* storage blocked: the choice just lasts until reload */
  }
};

function audio() {
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return null;
  if (!ctx) ctx = new AC();
  if (ctx.state === 'suspended') ctx.resume().catch(() => {});
  return ctx;
}

/** One bell strike: a main tone plus two inharmonic overtones that fade faster, which is what makes it sound like metal. */
function bell(ac, freq, at, { gain = 0.22, decay = 0.9 } = {}) {
  [[1, 1], [2.76, 0.35], [5.4, 0.15]].forEach(([mult, level]) => {
    const osc = ac.createOscillator();
    const g = ac.createGain();
    osc.type = 'sine';
    osc.frequency.value = freq * mult;
    g.gain.setValueAtTime(0, at);
    g.gain.linearRampToValueAtTime(gain * level, at + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, at + decay / mult);
    osc.connect(g).connect(ac.destination);
    osc.start(at);
    osc.stop(at + decay / mult + 0.05);
  });
}

function beep(ac, freq, at, dur, { type = 'triangle', gain = 0.18 } = {}) {
  const osc = ac.createOscillator();
  const g = ac.createGain();
  osc.type = type;
  osc.frequency.value = freq;
  g.gain.setValueAtTime(0, at);
  g.gain.linearRampToValueAtTime(gain, at + 0.01);
  g.gain.exponentialRampToValueAtTime(0.0001, at + dur);
  osc.connect(g).connect(ac.destination);
  osc.start(at);
  osc.stop(at + dur + 0.03);
}

const sounds = {
  // Money arrived: "ching ching".
  received: (ac, t) => {
    bell(ac, 1318.5, t, { decay: 0.8 });
    bell(ac, 1760, t + 0.16, { gain: 0.26, decay: 1.2 });
  },
  // Your transfer went through: a quick rising chime.
  success: (ac, t) => {
    bell(ac, 880, t, { gain: 0.16, decay: 0.5 });
    bell(ac, 1174.7, t + 0.09, { gain: 0.16, decay: 0.5 });
    bell(ac, 1568, t + 0.18, { gain: 0.18, decay: 0.9 });
  },
  // Needs your attention: held, locked, or a wrong code.
  alert: (ac, t) => {
    beep(ac, 523.3, t, 0.18, { type: 'square', gain: 0.1 });
    beep(ac, 392, t + 0.22, 0.28, { type: 'square', gain: 0.1 });
  },
  // A code arrived by SMS.
  otp: (ac, t) => {
    bell(ac, 1046.5, t, { gain: 0.2, decay: 0.6 });
    bell(ac, 1046.5, t + 0.22, { gain: 0.14, decay: 0.6 });
  },
  // Any other new notification.
  notify: (ac, t) => bell(ac, 987.8, t, { gain: 0.18, decay: 0.7 }),
};

export function play(name, { force = false } = {}) {
  if (!force && !soundOn()) return;
  try {
    const ac = audio();
    if (ac && sounds[name]) sounds[name](ac, ac.currentTime + 0.02);
  } catch {
    /* sound is a nice extra, never worth breaking the screen for */
  }
}

/** Which sound goes with a notification kind. */
export const soundFor = (kind) => ({
  RECEIVED: 'received', INTEREST: 'received', GOAL: 'success', REVIEW: 'notify',
  HOLD: 'alert', LOCK: 'alert',
}[kind] || 'notify');
