/**
 * DAKD JARVIS - Main Application Coordinator
 */
document.addEventListener('DOMContentLoaded', () => {
  JarvisUI.init();
  JarvisVoice.init();

  let appCache = [];

  // 1. Native Event Dispatcher from Android Kotlin Bridge
  window.onJarvisEvent = function(eventName, payload) {
    console.log('[JARVIS Native Event]', eventName, payload);

    switch (eventName) {
      case 'bridge_ready':
        loadInitialState();
        break;

      case 'speech_start':
        JarvisVoice.setListening(true);
        break;

      case 'speech_end':
        JarvisVoice.setListening(false);
        break;

      case 'speech_rms':
        if (payload && typeof payload.rms === 'number') {
          JarvisVoice.updateRms(payload.rms);
        }
        break;

      case 'speech_result':
        if (payload && payload.text) {
          JarvisUI.addUserMessage(payload.text);
        }
        break;

      case 'speech_error':
        JarvisVoice.setListening(false);
        if (payload && payload.error) {
          JarvisUI.showCoreStatus(payload.error.toUpperCase());
          setTimeout(() => JarvisUI.showCoreStatus('ONLINE'), 3000);
        }
        break;

      case 'command_processing':
        JarvisUI.showCoreStatus('PROCESSING...');
        break;

      case 'command_result':
        if (payload) {
          JarvisUI.handleExecutionResult(payload);
        }
        break;

      case 'tts_state':
        if (payload && payload.speaking) {
          JarvisUI.showCoreStatus('SPEAKING...');
        } else {
          JarvisUI.showCoreStatus('ONLINE');
        }
        break;

      case 'torch_state':
        if (payload) {
          const btn = document.getElementById('btn-torch-toggle');
          if (payload.on) btn.classList.add('active');
          else btn.classList.remove('active');
        }
        break;

      case 'permissions_update':
        updatePermissionsUI(payload);
        break;

      case 'hands_free_update':
        if (payload && typeof payload.enabled === 'boolean') {
          JarvisVoice.updateHandsFreeUI(payload.enabled);
        }
        break;

      case 'accessibility_update':
        if (payload) {
          updateAccessibilityUI(payload.enabled);
        }
        break;
    }
  };

  // Back button handler for Android
  window.onAndroidBack = function() {
    const settingsModal = document.getElementById('settings-modal');
    const appsModal = document.getElementById('apps-modal');
    if (settingsModal && settingsModal.style.display !== 'none') {
      JarvisUI.closeModal('settings-modal');
      return true;
    }
    if (appsModal && appsModal.style.display !== 'none') {
      JarvisUI.closeModal('apps-modal');
      return true;
    }
    return false;
  };

  // 2. Load Initial State from Android Bridge or Local Storage
  function loadInitialState() {
    const state = JarvisStorage.getInitialState();
    if (state.profile) {
      JarvisUI.updateGreeting(state.profile.name || "Sir");
      document.getElementById('setting-user-name').value = state.profile.name || "Danish";
      document.getElementById('setting-assistant-name').value = state.profile.assistant_name || "DAKD JARVIS";
      document.getElementById('setting-pref-lang').value = state.profile.preferred_language || "Hindi / Roman Hindi / English";
      document.getElementById('setting-response-style').value = state.profile.response_style || "Normal";
    }

    if (state.ai_config) {
      document.getElementById('setting-ai-provider').value = state.ai_config.provider || "GEMINI";
      document.getElementById('setting-ai-model').value = state.ai_config.model || "gemini-3.5-flash";
      document.getElementById('setting-ai-endpoint').value = state.ai_config.endpoint || "";
      if (state.ai_config.custom_api_key) {
        document.getElementById('setting-custom-key').value = state.ai_config.custom_api_key;
      }
    }

    if (typeof state.hands_free_enabled === 'boolean') {
      JarvisVoice.updateHandsFreeUI(state.hands_free_enabled);
    }

    if (state.confirmation_mode) {
      document.getElementById('setting-confirm-mode').value = state.confirmation_mode;
    }

    if (typeof state.wake_word_enabled === 'boolean') {
      document.getElementById('setting-wake-word-enable').checked = state.wake_word_enabled;
    }
    if (state.wake_word_phrase) {
      document.getElementById('setting-wake-phrase').value = state.wake_word_phrase;
    }

    if (typeof state.tts_enabled === 'boolean') {
      document.getElementById('setting-tts-enable').checked = state.tts_enabled;
    }
    if (state.tts_speed) {
      document.getElementById('setting-tts-speed').value = state.tts_speed;
      document.getElementById('tts-speed-val').textContent = state.tts_speed + 'x';
    }

    if (state.torch_on) {
      document.getElementById('btn-torch-toggle').classList.add('active');
    }

    if (state.permissions) {
      updatePermissionsUI(state.permissions);
    }

    if (typeof state.accessibility_enabled === 'boolean') {
      updateAccessibilityUI(state.accessibility_enabled);
    }
  }

  function updatePermissionsUI(perms) {
    if (!perms) return;
    setPermBadge('perm-mic-status', perms.microphone);
    setPermBadge('perm-call-status', perms.call);
    setPermBadge('perm-contacts-status', perms.contacts);
  }

  function updateAccessibilityUI(enabled) {
    setPermBadge('perm-acc-status', enabled);
  }

  function setPermBadge(elemId, granted) {
    const el = document.getElementById(elemId);
    if (!el) return;
    if (granted) {
      el.textContent = 'Granted';
      el.className = 'perm-badge green';
    } else {
      el.textContent = 'Disabled';
      el.className = 'perm-badge red';
    }
  }

  // 3. Setup UI Interaction Listeners
  // Mic Button
  const micButton = document.getElementById('mic-button');
  micButton.addEventListener('click', () => {
    JarvisVoice.toggleListening();
  });

  // Core Center Tap also activates Mic / Wake response
  const coreCenter = document.getElementById('core-center');
  coreCenter.addEventListener('click', () => {
    JarvisVoice.toggleListening();
  });

  // Text Input Send
  const sendButton = document.getElementById('send-button');
  const commandInput = document.getElementById('command-input');

  function submitInput() {
    const text = commandInput.value.trim();
    if (text) {
      commandInput.value = '';
      JarvisCommands.handleUserQuery(text);
    }
  }

  sendButton.addEventListener('click', submitInput);
  commandInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      submitInput();
    }
  });

  // Torch Toggle
  document.getElementById('btn-torch-toggle').addEventListener('click', () => {
    JarvisCommands.toggleTorchDirect();
  });

  // Sound Toggle (Mute / Unmute)
  document.getElementById('btn-sound-toggle').addEventListener('click', () => {
    JarvisVoice.toggleMute();
  });

  // Quick Action Buttons
  document.querySelectorAll('.quick-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const action = btn.getAttribute('data-action');
      JarvisCommands.handleQuickAction(action);
    });
  });

  // Confirmation Card Buttons
  document.getElementById('btn-confirm-ok').addEventListener('click', () => {
    const pending = JarvisUI.getPendingAction();
    if (pending) {
      JarvisUI.hideConfirmation();
      JarvisAI.confirmAction(pending);
    }
  });

  document.getElementById('btn-confirm-cancel').addEventListener('click', () => {
    JarvisUI.hideConfirmation();
    JarvisUI.addJarvisMessage("Command cancelled by user Sir.");
  });

  // Settings Modal Controls
  document.getElementById('btn-open-settings').addEventListener('click', () => {
    JarvisUI.openModal('settings-modal');
  });

  document.getElementById('close-settings').addEventListener('click', () => {
    JarvisUI.closeModal('settings-modal');
  });

  document.getElementById('setting-tts-speed').addEventListener('input', (e) => {
    document.getElementById('tts-speed-val').textContent = e.target.value + 'x';
  });

  // Permission Request Triggers
  document.getElementById('btn-req-acc').addEventListener('click', () => {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.openAccessibilitySettings === 'function') {
      window.AndroidBridge.openAccessibilitySettings();
    } else {
      alert("Accessibility settings available on Android device.");
    }
  });

  document.getElementById('btn-req-mic').addEventListener('click', () => {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.requestPermission === 'function') {
      window.AndroidBridge.requestPermission('microphone');
    }
  });

  document.getElementById('btn-req-call').addEventListener('click', () => {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.requestPermission === 'function') {
      window.AndroidBridge.requestPermission('call');
    }
  });

  document.getElementById('btn-req-contacts').addEventListener('click', () => {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.requestPermission === 'function') {
      window.AndroidBridge.requestPermission('contacts');
    }
  });

  // Toggle Key Visibility
  const toggleKeyBtn = document.getElementById('btn-toggle-key-view');
  if (toggleKeyBtn) {
    toggleKeyBtn.addEventListener('click', () => {
      const input = document.getElementById('setting-custom-key');
      if (input.type === 'password') {
        input.type = 'text';
        toggleKeyBtn.textContent = '🔒';
      } else {
        input.type = 'password';
        toggleKeyBtn.textContent = '👁️';
      }
    });
  }

  // Test API Connection
  const testApiBtn = document.getElementById('btn-test-api');
  const testResultBox = document.getElementById('api-test-result');
  if (testApiBtn && testResultBox) {
    testApiBtn.addEventListener('click', () => {
      const customKey = document.getElementById('setting-custom-key').value.trim();
      const model = document.getElementById('setting-ai-model').value.trim() || 'gemini-3.5-flash';
      testResultBox.style.display = 'block';
      testResultBox.className = 'api-test-box';
      testResultBox.textContent = 'Testing connection to Google AI server... Please wait.';

      setTimeout(() => {
        if (JarvisStorage.isNative() && typeof window.AndroidBridge.testAIConnection === 'function') {
          try {
            const raw = window.AndroidBridge.testAIConnection(customKey, model);
            const res = JSON.parse(raw);
            if (res.success) {
              testResultBox.className = 'api-test-box success';
              testResultBox.textContent = res.message;
            } else {
              testResultBox.className = 'api-test-box error';
              testResultBox.innerHTML = res.message;
            }
          } catch (e) {
            testResultBox.className = 'api-test-box error';
            testResultBox.textContent = 'Test failed: ' + e.message;
          }
        } else {
          testResultBox.className = 'api-test-box success';
          testResultBox.textContent = 'Browser preview mode: API key stored locally.';
        }
      }, 50);
    });
  }

  // Hands-free checkbox change listener
  const hfCheckbox = document.getElementById('setting-hands-free-enable');
  if (hfCheckbox) {
    hfCheckbox.addEventListener('change', (e) => {
      JarvisVoice.setHandsFree(e.target.checked);
    });
  }

  // Save Settings
  document.getElementById('btn-save-settings').addEventListener('click', () => {
    const profile = {
      name: document.getElementById('setting-user-name').value.trim() || "Danish",
      assistant_name: document.getElementById('setting-assistant-name').value.trim() || "DAKD JARVIS",
      preferred_language: document.getElementById('setting-pref-lang').value,
      voice_gender: "Male",
      response_style: document.getElementById('setting-response-style').value
    };

    const aiCfg = {
      provider: document.getElementById('setting-ai-provider').value,
      model: document.getElementById('setting-ai-model').value.trim() || "gemini-3.5-flash",
      endpoint: document.getElementById('setting-ai-endpoint').value.trim(),
      custom_api_key: document.getElementById('setting-custom-key').value.trim()
    };

    const handsFreeEnabled = document.getElementById('setting-hands-free-enable') ?
      document.getElementById('setting-hands-free-enable').checked : true;

    const settings = {
      confirmation_mode: document.getElementById('setting-confirm-mode').value,
      ai_config: aiCfg,
      hands_free_enabled: handsFreeEnabled,
      wake_word_enabled: document.getElementById('setting-wake-word-enable').checked,
      wake_word_phrase: document.getElementById('setting-wake-phrase').value.trim() || "Ok Jaan",
      tts_enabled: document.getElementById('setting-tts-enable').checked,
      tts_speed: parseFloat(document.getElementById('setting-tts-speed').value),
      fg_notif_enabled: document.getElementById('setting-fg-notif').checked
    };

    JarvisStorage.saveProfile(profile);
    JarvisStorage.saveSettings(settings);
    JarvisVoice.setHandsFree(handsFreeEnabled);

    JarvisUI.updateGreeting(profile.name);
    JarvisUI.closeModal('settings-modal');
    JarvisUI.addJarvisMessage("Settings successfully updated Sir.");
  });

  // Clear History
  document.getElementById('btn-clear-history').addEventListener('click', () => {
    if (confirm("Are you sure you want to clear command history?")) {
      JarvisStorage.clearHistory();
      JarvisUI.addJarvisMessage("Command history cleared Sir.");
    }
  });

  // Delete All Data
  document.getElementById('btn-delete-all').addEventListener('click', () => {
    if (confirm("WARNING: This will reset all preferences and encrypted data. Proceed?")) {
      JarvisStorage.deleteAllData();
      location.reload();
    }
  });

  // Installed Apps Modal Controls
  document.getElementById('btn-open-apps').addEventListener('click', () => {
    JarvisUI.openModal('apps-modal');
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.getInstalledApps === 'function') {
      try {
        const raw = window.AndroidBridge.getInstalledApps();
        appCache = JSON.parse(raw);
        JarvisUI.renderInstalledApps(appCache);
      } catch (e) {
        console.error(e);
      }
    } else {
      // Mock apps for preview
      appCache = [
        { name: "WhatsApp", package: "com.whatsapp" },
        { name: "Instagram", package: "com.instagram.android" },
        { name: "Facebook", package: "com.facebook.katana" },
        { name: "Chrome", package: "com.android.chrome" },
        { name: "YouTube", package: "com.google.android.youtube" },
        { name: "Camera", package: "com.android.camera" },
        { name: "Gallery", package: "com.android.gallery3d" },
        { name: "Settings", package: "com.android.settings" },
        { name: "Maps", package: "com.google.android.apps.maps" },
        { name: "Play Store", package: "com.android.vending" }
      ];
      JarvisUI.renderInstalledApps(appCache);
    }
  });

  document.getElementById('close-apps').addEventListener('click', () => {
    JarvisUI.closeModal('apps-modal');
  });

  // App Search Filter
  document.getElementById('app-search-input').addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    if (!q) {
      JarvisUI.renderInstalledApps(appCache);
    } else {
      const filtered = appCache.filter(a => a.name.toLowerCase().includes(q) || a.package.toLowerCase().includes(q));
      JarvisUI.renderInstalledApps(filtered);
    }
  });

  // Run initial state setup
  loadInitialState();
});
