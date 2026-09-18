/**
 * DAKD JARVIS - Voice Controller
 * Integrates Android native SpeechRecognizer & TextToSpeech with visual feedback
 */
const JarvisVoice = (function() {
  let isListening = false;
  let isMuted = false;
  let isHandsFree = true;
  let webSpeechRecognizer = null;

  function init() {
    // Check web speech fallback if not on Android
    if (!JarvisStorage.isNative() && ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window)) {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      webSpeechRecognizer = new SpeechRecognition();
      webSpeechRecognizer.continuous = true;
      webSpeechRecognizer.interimResults = false;
      webSpeechRecognizer.lang = 'hi-IN';

      webSpeechRecognizer.onstart = () => {
        setListening(true);
      };
      webSpeechRecognizer.onend = () => {
        setListening(false);
        if (isHandsFree) {
          setTimeout(() => {
            try { webSpeechRecognizer.start(); } catch (e) {}
          }, 1000);
        }
      };
      webSpeechRecognizer.onerror = (e) => {
        setListening(false);
      };
      webSpeechRecognizer.onresult = (event) => {
        const text = event.results[event.results.length - 1][0].transcript;
        JarvisCommands.handleUserQuery(text);
      };
    }

    // Bind hands-free toggle bar button
    const hfToggleBtn = document.getElementById('hf-btn-toggle');
    if (hfToggleBtn) {
      hfToggleBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleHandsFree();
      });
    }

    // Default hands-free UI state
    updateHandsFreeUI(isHandsFree);
  }

  function toggleListening() {
    if (isListening) {
      stopListening();
    } else {
      startListening();
    }
  }

  function startListening() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.startVoiceRecognition === 'function') {
      window.AndroidBridge.startVoiceRecognition('hi-IN');
      setListening(true);
    } else if (webSpeechRecognizer) {
      try {
        webSpeechRecognizer.start();
        setListening(true);
      } catch (e) {
        console.warn('Web speech recognizer already active');
      }
    } else {
      const sample = prompt("Simulate Voice Input (or type command in bottom box):", "WhatsApp kholo");
      if (sample) {
        JarvisCommands.handleUserQuery(sample);
      }
    }
  }

  function stopListening() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.stopVoiceRecognition === 'function') {
      window.AndroidBridge.stopVoiceRecognition();
    } else if (webSpeechRecognizer) {
      try { webSpeechRecognizer.stop(); } catch (e) {}
    }
    setListening(false);
  }

  function setHandsFree(enabled) {
    isHandsFree = enabled;
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.setHandsFree === 'function') {
      window.AndroidBridge.setHandsFree(enabled);
    }
    updateHandsFreeUI(enabled);
  }

  function toggleHandsFree() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.toggleHandsFree === 'function') {
      isHandsFree = window.AndroidBridge.toggleHandsFree();
    } else {
      isHandsFree = !isHandsFree;
      if (isHandsFree) startListening();
      else stopListening();
    }
    updateHandsFreeUI(isHandsFree);
    return isHandsFree;
  }

  function updateHandsFreeUI(enabled) {
    const bar = document.getElementById('handsfree-status-bar');
    const label = document.getElementById('hf-label');
    const btn = document.getElementById('hf-btn-toggle');
    const micHint = document.getElementById('mic-hint');

    if (bar) {
      if (enabled) {
        bar.classList.remove('muted');
        if (label) label.innerHTML = '🎙️ Hands-Free: <b>ACTIVE</b> (Bina touch kare bole)';
        if (btn) btn.textContent = 'Pause';
        if (!isListening && micHint) {
          micHint.textContent = '🎙️ Listening continuously... Speak command anytime!';
        }
      } else {
        bar.classList.add('muted');
        if (label) label.innerHTML = '⏸️ Hands-Free: <b>PAUSED</b> (Touch mic to speak)';
        if (btn) btn.textContent = 'Resume';
        if (!isListening && micHint) {
          micHint.textContent = 'Tap to Speak (Hindi / English)';
        }
      }
    }

    const settingToggle = document.getElementById('setting-hands-free-enable');
    if (settingToggle) {
      settingToggle.checked = enabled;
    }
  }

  function setListening(active) {
    isListening = active;
    const micBtn = document.getElementById('mic-button');
    const micHint = document.getElementById('mic-hint');
    const waveRing = document.getElementById('wave-ring');

    if (active) {
      micBtn.classList.add('listening');
      micHint.textContent = 'Listening... Speak now';
      JarvisUI.showCoreStatus('LISTENING...');
      if (waveRing) {
        waveRing.style.borderColor = 'rgba(255, 51, 102, 0.7)';
        waveRing.style.transform = 'scale(1.2)';
      }
    } else {
      micBtn.classList.remove('listening');
      if (isHandsFree) {
        micHint.textContent = '🎙️ Listening continuously... Speak command anytime!';
      } else {
        micHint.textContent = 'Tap to Speak (Hindi / English)';
      }
      JarvisUI.showCoreStatus('ONLINE');
      if (waveRing) {
        waveRing.style.borderColor = 'transparent';
        waveRing.style.transform = 'scale(1.0)';
      }
    }
  }

  function updateRms(rms) {
    const waveRing = document.getElementById('wave-ring');
    if (!waveRing) return;
    const scale = Math.min(1.4, Math.max(1.0, 1.0 + (rms / 15.0)));
    waveRing.style.transform = `scale(${scale})`;
    waveRing.style.borderColor = `rgba(0, 240, 255, ${Math.min(0.9, 0.3 + (rms / 12.0))})`;
  }

  function speak(text) {
    if (isMuted) return;
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.speakText === 'function') {
      window.AndroidBridge.speakText(text);
    } else if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'hi-IN';
      window.speechSynthesis.speak(utterance);
    }
  }

  function stopSpeaking() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.stopSpeaking === 'function') {
      window.AndroidBridge.stopSpeaking();
    } else if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
  }

  function toggleMute() {
    isMuted = !isMuted;
    const btn = document.getElementById('btn-sound-toggle');
    if (isMuted) {
      stopSpeaking();
      btn.classList.add('active');
    } else {
      btn.classList.remove('active');
    }
    return isMuted;
  }

  return {
    init,
    toggleListening,
    startListening,
    stopListening,
    setListening,
    setHandsFree,
    toggleHandsFree,
    updateHandsFreeUI,
    updateRms,
    speak,
    stopSpeaking,
    toggleMute
  };
})();
