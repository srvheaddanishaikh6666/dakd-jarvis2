/**
 * DAKD JARVIS - UI Controller
 */
const JarvisUI = (function() {
  let pendingActionData = null;

  function init() {
    updateGreeting();
  }

  function getTimeString() {
    const now = new Date();
    let hours = now.getHours();
    let minutes = now.getMinutes();
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    hours = hours ? hours : 12;
    minutes = minutes < 10 ? '0' + minutes : minutes;
    return `${hours}:${minutes} ${ampm}`;
  }

  function updateGreeting(userName = "Sir") {
    const now = new Date();
    const hr = now.getHours();
    let timeGreeting = "Good Evening";
    if (hr >= 4 && hr < 12) {
      timeGreeting = "Good Morning";
    } else if (hr >= 12 && hr < 17) {
      timeGreeting = "Good Afternoon";
    }

    const welcomeText = document.getElementById('welcome-text');
    const welcomeTime = document.getElementById('welcome-time');
    if (welcomeText) {
      welcomeText.textContent = `${timeGreeting} ${userName}. DAKD JARVIS online. How may I assist you?`;
    }
    if (welcomeTime) {
      welcomeTime.textContent = getTimeString();
    }
    showCoreStatus('ONLINE');
  }

  function showCoreStatus(text) {
    const statusText = document.getElementById('core-status-text');
    if (statusText) statusText.textContent = text;
  }

  function addUserMessage(text) {
    const panel = document.getElementById('conversation-panel');
    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble user-bubble';
    bubble.innerHTML = `
      <div class="bubble-header">
        <span>YOU</span>
        <span class="bubble-time">${getTimeString()}</span>
      </div>
      <div class="bubble-text">${escapeHtml(text)}</div>
    `;
    panel.appendChild(bubble);
    panel.scrollTop = panel.scrollHeight;
  }

  function addJarvisMessage(text, actionTag = null) {
    const panel = document.getElementById('conversation-panel');
    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble jarvis-bubble';
    
    let actionBadge = '';
    if (actionTag && actionTag !== 'speak_only' && actionTag !== 'none') {
      actionBadge = `<div class="bubble-action-tag">ACTION: ${actionTag.toUpperCase()}</div>`;
    }

    bubble.innerHTML = `
      <div class="bubble-header">
        <span>DAKD JARVIS</span>
        <span class="bubble-time">${getTimeString()}</span>
      </div>
      <div class="bubble-text">${escapeHtml(text)}</div>
      ${actionBadge}
    `;
    panel.appendChild(bubble);
    panel.scrollTop = panel.scrollHeight;
  }

  function handleExecutionResult(result) {
    showCoreStatus('ONLINE');

    if (result.requires_confirmation) {
      // Show confirmation prompt
      pendingActionData = result.pending_action;
      showConfirmation(result.reply || "Sir, is action ko execute karne ke liye confirmation chahiye.");
    } else {
      hideConfirmation();
      addJarvisMessage(result.reply, result.action_executed);
    }
  }

  function showConfirmation(message) {
    const card = document.getElementById('confirmation-card');
    const msgEl = document.getElementById('confirm-message');
    if (card && msgEl) {
      msgEl.textContent = message;
      card.style.display = 'block';
      card.scrollIntoView({ behavior: 'smooth' });
    }
  }

  function hideConfirmation() {
    const card = document.getElementById('confirmation-card');
    if (card) {
      card.style.display = 'none';
    }
    pendingActionData = null;
  }

  function getPendingAction() {
    return pendingActionData;
  }

  function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.style.display = 'flex';
  }

  function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.style.display = 'none';
  }

  function renderInstalledApps(apps) {
    const container = document.getElementById('apps-grid-container');
    if (!container) return;
    container.innerHTML = '';

    if (!apps || apps.length === 0) {
      container.innerHTML = '<div class="loading-text">No apps found.</div>';
      return;
    }

    apps.forEach(app => {
      const card = document.createElement('div');
      card.className = 'app-card';
      card.innerHTML = `
        <div class="app-card-icon">📱</div>
        <div class="app-card-name" title="${escapeHtml(app.name)}">${escapeHtml(app.name)}</div>
      `;
      card.onclick = () => {
        closeModal('apps-modal');
        if (JarvisStorage.isNative() && typeof window.AndroidBridge.launchApp === 'function') {
          window.AndroidBridge.launchApp(app.package);
          addJarvisMessage(`Launching ${app.name} Sir...`, 'LAUNCH_APP');
        } else {
          addJarvisMessage(`Launched ${app.name} (Simulation).`);
        }
      };
      container.appendChild(card);
    });
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  return {
    init,
    updateGreeting,
    showCoreStatus,
    addUserMessage,
    addJarvisMessage,
    handleExecutionResult,
    showConfirmation,
    hideConfirmation,
    getPendingAction,
    openModal,
    closeModal,
    renderInstalledApps
  };
})();
