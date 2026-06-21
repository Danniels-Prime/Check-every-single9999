'use strict';

// ── State ──────────────────────────────────────────────────────────────────
let currentImageNaturalW = 0;
let currentImageNaturalH = 0;
let bubbleOpacity = 0.88;
let isProcessing = false;
let toastTimer = null;

// ── DOM refs ───────────────────────────────────────────────────────────────
const screenLanding   = document.getElementById('screenLanding');
const screenResult    = document.getElementById('screenResult');
const fileInput       = document.getElementById('fileInput');
const cameraInput     = document.getElementById('cameraInput');
const cameraBtn       = document.getElementById('cameraBtn');
const uploadBtn       = document.getElementById('uploadBtn');
const previewImg      = document.getElementById('previewImg');
const bubblesContainer= document.getElementById('bubblesContainer');
const imageWrap       = document.getElementById('imageWrap');
const scanOverlay     = document.getElementById('scanOverlay');
const progressSection = document.getElementById('progressSection');
const progressFill    = document.getElementById('progressFill');
const progressText    = document.getElementById('progressText');
const bottomBar       = document.getElementById('bottomBar');
const translateBtn    = document.getElementById('translateBtn');
const retryBtn        = document.getElementById('retryBtn');
const resultInfo      = document.getElementById('resultInfo');
const resultCount     = document.getElementById('resultCount');
const clearBubblesBtn = document.getElementById('clearBubblesBtn');
const settingsBtn     = document.getElementById('settingsBtn');
const settingsPanel   = document.getElementById('settingsPanel');
const settingsOverlay = document.getElementById('settingsOverlay');
const settingsClose   = document.getElementById('settingsClose');
const sourceLangSel   = document.getElementById('sourceLang');
const targetLangSel   = document.getElementById('targetLang');
const opacitySlider   = document.getElementById('opacitySlider');
const toast           = document.getElementById('toast');

// ── Settings persistence ───────────────────────────────────────────────────
function loadSettings() {
  try {
    const s = JSON.parse(localStorage.getItem('screenTranslateSettings') || '{}');
    if (s.sourceLang) sourceLangSel.value = s.sourceLang;
    if (s.targetLang) targetLangSel.value = s.targetLang;
    if (s.opacity)    { opacitySlider.value = s.opacity; bubbleOpacity = s.opacity / 100; }
  } catch (_) {}
}

function saveSettings() {
  localStorage.setItem('screenTranslateSettings', JSON.stringify({
    sourceLang: sourceLangSel.value,
    targetLang: targetLangSel.value,
    opacity:    opacitySlider.value
  }));
}

// ── Settings panel ─────────────────────────────────────────────────────────
settingsBtn.addEventListener('click', () => {
  settingsPanel.classList.remove('hidden');
  settingsOverlay.classList.remove('hidden');
});
[settingsClose, settingsOverlay].forEach(el =>
  el.addEventListener('click', () => {
    settingsPanel.classList.add('hidden');
    settingsOverlay.classList.add('hidden');
    saveSettings();
  })
);
opacitySlider.addEventListener('input', () => {
  bubbleOpacity = opacitySlider.value / 100;
  document.querySelectorAll('.translation-bubble').forEach(b => {
    b.style.backgroundColor = bubbleColor();
  });
});

// ── Image selection ────────────────────────────────────────────────────────
uploadBtn.addEventListener('click', () => fileInput.click());
cameraBtn.addEventListener('click', () => cameraInput.click());

fileInput.addEventListener('change', e => handleFile(e.target.files[0]));
cameraInput.addEventListener('change', e => handleFile(e.target.files[0]));

function handleFile(file) {
  if (!file || !file.type.startsWith('image/')) return;
  const url = URL.createObjectURL(file);
  loadImageForTranslation(url);
}

// Drag & drop on landing
document.addEventListener('dragover', e => e.preventDefault());
document.addEventListener('drop', e => {
  e.preventDefault();
  const file = e.dataTransfer.files[0];
  if (file?.type.startsWith('image/')) handleFile(file);
});

// Paste (Ctrl+V on desktop)
document.addEventListener('paste', e => {
  const item = Array.from(e.clipboardData.items).find(i => i.type.startsWith('image/'));
  if (item) handleFile(item.getAsFile());
});

function loadImageForTranslation(url) {
  screenLanding.classList.add('hidden');
  screenResult.classList.remove('hidden');
  clearBubbles();
  translateBtn.classList.add('hidden');
  resultInfo.classList.add('hidden');
  progressSection.classList.remove('hidden');
  scanOverlay.classList.remove('hidden');
  setProgress(0, 'Loading image…');

  previewImg.onload = () => {
    currentImageNaturalW = previewImg.naturalWidth;
    currentImageNaturalH = previewImg.naturalHeight;
    translateBtn.classList.remove('hidden');
    progressSection.classList.add('hidden');
    scanOverlay.classList.add('hidden');
    setProgress(0, '');
  };
  previewImg.src = url;
}

// ── Translate button ───────────────────────────────────────────────────────
translateBtn.addEventListener('click', () => {
  if (!isProcessing) startTranslation();
});

async function startTranslation() {
  if (isProcessing) return;
  isProcessing = true;

  translateBtn.disabled = true;
  translateBtn.textContent = 'Working…';
  clearBubbles();
  resultInfo.classList.add('hidden');
  progressSection.classList.remove('hidden');
  scanOverlay.classList.remove('hidden');

  try {
    // ── Phase 1: OCR ──────────────────────────────────────
    setProgress(2, 'Loading OCR engine…');

    const sourceLang = sourceLangSel.value === 'auto' ? 'eng+rus' : sourceLangSel.value;

    const { data } = await Tesseract.recognize(previewImg, sourceLang, {
      logger: m => {
        if (m.status === 'recognizing text') {
          const pct = Math.round(m.progress * 55);
          setProgress(pct, `Reading text… ${Math.round(m.progress * 100)}%`);
        } else if (m.status === 'loading tesseract core') {
          setProgress(5, 'Loading OCR core…');
        } else if (m.status === 'initializing tesseract') {
          setProgress(10, 'Initializing OCR…');
        } else if (m.status === 'loading language traineddata') {
          setProgress(15, 'Loading language data…');
        }
      }
    });

    scanOverlay.classList.add('hidden');

    const paragraphs = data.paragraphs.filter(p => p.text.trim().length > 2);

    if (paragraphs.length === 0) {
      showToast('No text detected in this image');
      resetProcessing();
      return;
    }

    // ── Phase 2: Translate ────────────────────────────────
    const targetLang = targetLangSel.value;
    const total = paragraphs.length;
    let translated = 0;
    let bubbleCount = 0;

    for (const para of paragraphs) {
      const pct = 55 + Math.round((translated / total) * 40);
      setProgress(pct, `Translating ${translated + 1} of ${total}…`);

      try {
        const result = await translateText(para.text.trim(), targetLang);
        if (result && result !== para.text.trim()) {
          placeBubble(para.bbox, para.text.trim(), result);
          bubbleCount++;
        }
      } catch (err) {
        console.warn('Block translation failed:', para.text, err);
      }

      translated++;
    }

    setProgress(100, 'Done!');

    setTimeout(() => {
      progressSection.classList.add('hidden');

      if (bubbleCount === 0) {
        showToast('No translations needed — text may already be in target language');
      } else {
        resultCount.textContent = `${bubbleCount} block${bubbleCount !== 1 ? 's' : ''} translated`;
        resultInfo.classList.remove('hidden');
      }

      translateBtn.textContent = 'Retranslate';
      translateBtn.disabled = false;
    }, 500);

  } catch (err) {
    console.error('Translation pipeline error:', err);
    showToast('Error: ' + (err.message || 'Something went wrong'));
    resetProcessing();
  } finally {
    isProcessing = false;
  }
}

// ── Translation API ────────────────────────────────────────────────────────
async function translateText(text, targetLang) {
  // Use Google's unofficial translate endpoint — fast, no key required
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${encodeURIComponent(targetLang)}&dt=t&q=${encodeURIComponent(text)}`;

  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    // Response format: [ [[translatedText, originalText, ...], ...], ... ]
    if (!json[0]) throw new Error('Unexpected response');
    return json[0].map(item => item[0]).filter(Boolean).join('').trim();
  } catch (err) {
    // Fallback: MyMemory API
    const fallbackUrl = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text.slice(0, 500))}&langpair=auto|${targetLang}`;
    const res2 = await fetch(fallbackUrl);
    const json2 = await res2.json();
    if (json2.responseStatus === 200) return json2.responseData.translatedText;
    throw err;
  }
}

// ── Bubble rendering ───────────────────────────────────────────────────────
function bubbleColor() {
  const alpha = Math.round(bubbleOpacity * 255).toString(16).padStart(2, '0');
  return `#1565C0${alpha}`;
}

function placeBubble(bbox, original, translated) {
  const img = previewImg;
  const rect = img.getBoundingClientRect();
  const wrapRect = imageWrap.getBoundingClientRect();

  // Offset of the img relative to the imageWrap (centered)
  const offsetX = rect.left - wrapRect.left + imageWrap.scrollLeft;
  const offsetY = rect.top  - wrapRect.top  + imageWrap.scrollTop;

  const scaleX = img.clientWidth  / currentImageNaturalW;
  const scaleY = img.clientHeight / currentImageNaturalH;

  const left   = offsetX + bbox.x0 * scaleX;
  const top    = offsetY + bbox.y0 * scaleY;
  const width  = (bbox.x1 - bbox.x0) * scaleX;
  const height = (bbox.y1 - bbox.y0) * scaleY;

  const bubble = document.createElement('div');
  bubble.className = 'translation-bubble';
  bubble.style.left            = left   + 'px';
  bubble.style.top             = top    + 'px';
  bubble.style.width           = Math.max(width, 40) + 'px';
  bubble.style.minHeight       = Math.max(height, 16) + 'px';
  bubble.style.backgroundColor = bubbleColor();

  const tspan = document.createElement('span');
  tspan.className = 'bubble-translated';
  tspan.textContent = translated;

  const ospan = document.createElement('span');
  ospan.className = 'bubble-original';
  ospan.textContent = original;

  bubble.appendChild(tspan);
  bubble.appendChild(ospan);

  bubble.addEventListener('click', e => {
    e.stopPropagation();
    bubble.classList.toggle('show-original');
  });

  bubblesContainer.appendChild(bubble);
}

function clearBubbles() {
  bubblesContainer.innerHTML = '';
  resultInfo.classList.add('hidden');
}

// ── Controls ───────────────────────────────────────────────────────────────
retryBtn.addEventListener('click', () => {
  if (isProcessing) return;
  clearBubbles();
  screenResult.classList.add('hidden');
  screenLanding.classList.remove('hidden');
  fileInput.value = '';
  cameraInput.value = '';
  translateBtn.textContent = 'Translate';
  translateBtn.disabled = false;
  translateBtn.classList.add('hidden');
  progressSection.classList.add('hidden');
  setProgress(0, '');
});

clearBubblesBtn.addEventListener('click', clearBubbles);

// Recalculate bubble positions on window resize
window.addEventListener('resize', () => {
  // Easiest approach: re-render page; bubbles will scale automatically since
  // we store them by percentage of image size rather than absolute pixels.
  // For a clean experience we just clear bubbles on resize — user can retranslate.
  if (bubblesContainer.children.length > 0) {
    showToast('Resize detected — tap Retranslate to refresh');
  }
});

// ── Helpers ────────────────────────────────────────────────────────────────
function setProgress(pct, text) {
  progressFill.style.width = pct + '%';
  progressText.textContent = text;
}

function resetProcessing() {
  isProcessing = false;
  translateBtn.disabled = false;
  translateBtn.textContent = 'Translate';
  progressSection.classList.add('hidden');
  scanOverlay.classList.add('hidden');
}

function showToast(msg, duration = 3000) {
  clearTimeout(toastTimer);
  toast.textContent = msg;
  toast.classList.remove('hidden');
  toastTimer = setTimeout(() => toast.classList.add('hidden'), duration);
}

// ── Init ───────────────────────────────────────────────────────────────────
loadSettings();

// Register service worker for PWA offline support
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  });
}
