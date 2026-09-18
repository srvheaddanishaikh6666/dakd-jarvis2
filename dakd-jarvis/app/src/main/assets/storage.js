/**
 * DAKD JARVIS - Storage Service
 * Connects to Android native SQLite/SharedPreferences via AndroidBridge
 */
const JarvisStorage = (function() {
  const LOCAL_CACHE_KEY = 'dakd_jarvis_cache';

  function isNative() {
    return typeof window.AndroidBridge !== 'undefined';
  }

  function getInitialState() {
    if (isNative() && typeof window.AndroidBridge.getInitialState === 'function') {
      try {
        const raw = window.AndroidBridge.getInitialState();
        return JSON.parse(raw);
      } catch (e) {
        console.error('Failed to get initial state from bridge:', e);
      }
    }

    // Web Fallback Cache
    const cached = localStorage.getItem(LOCAL_CACHE_KEY);
    if (cached) {
      try { return JSON.parse(cached); } catch (e) {}
    }

    return {
      profile: {
        name: "Danish",
        assistant_name: "DAKD JARVIS",
        preferred_language: "Hindi / Roman Hindi / English",
        voice_gender: "Male",
        response_style: "Normal"
      },
      confirmation_mode: "BALANCED",
      ai_config: {
        provider: "GEMINI",
        endpoint: "",
        model: "gemini-3.5-flash",
        has_custom_key: false
      },
      wake_word_enabled: false,
      fg_notif_enabled: true,
      tts_enabled: true,
      tts_speed: 1.0,
      torch_on: false,
      torch_supported: true,
      accessibility_enabled: false,
      permissions: {
        microphone: true,
        call: true,
        contacts: true,
        camera: true,
        notifications: true
      },
      history: [],
      automations: []
    };
  }

  function saveProfile(profile) {
    if (isNative() && typeof window.AndroidBridge.saveProfile === 'function') {
      window.AndroidBridge.saveProfile(JSON.stringify(profile));
    }
    const state = getInitialState();
    state.profile = profile;
    localStorage.setItem(LOCAL_CACHE_KEY, JSON.stringify(state));
  }

  function saveSettings(settings) {
    if (isNative() && typeof window.AndroidBridge.saveSettings === 'function') {
      window.AndroidBridge.saveSettings(JSON.stringify(settings));
    }
    const state = getInitialState();
    Object.assign(state, settings);
    localStorage.setItem(LOCAL_CACHE_KEY, JSON.stringify(state));
  }

  function clearHistory() {
    if (isNative() && typeof window.AndroidBridge.clearHistory === 'function') {
      window.AndroidBridge.clearHistory();
    }
  }

  function deleteAllData() {
    if (isNative() && typeof window.AndroidBridge.deleteAllData === 'function') {
      window.AndroidBridge.deleteAllData();
    }
    localStorage.removeItem(LOCAL_CACHE_KEY);
  }

  return {
    isNative,
    getInitialState,
    saveProfile,
    saveSettings,
    clearHistory,
    deleteAllData
  };
})();
