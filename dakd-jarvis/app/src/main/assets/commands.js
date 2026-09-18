/**
 * DAKD JARVIS - Command Registry & Dispatcher
 */
const JarvisCommands = (function() {

  function handleUserQuery(query) {
    const trimmed = query.trim();
    if (!trimmed) return;

    JarvisUI.addUserMessage(trimmed);
    JarvisAI.processQuery(trimmed);
  }

  function handleQuickAction(actionKey) {
    switch (actionKey) {
      case 'whatsapp':
        handleUserQuery("WhatsApp kholo");
        break;
      case 'instagram':
        handleUserQuery("Instagram open karo");
        break;
      case 'facebook':
        handleUserQuery("Facebook kholo");
        break;
      case 'camera':
        handleUserQuery("Camera ready karo");
        break;
      case 'call':
        handleUserQuery("Danish ko call lagao");
        break;
      case 'torch':
        handleUserQuery("Torch toggle karo");
        break;
      case 'browser':
        handleUserQuery("Chrome browser kholo");
        break;
      case 'settings':
        handleUserQuery("Settings kholo");
        break;
      default:
        handleUserQuery(`Open ${actionKey}`);
    }
  }

  function toggleTorchDirect() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.toggleTorch === 'function') {
      try {
        const res = JSON.parse(window.AndroidBridge.toggleTorch(""));
        const btn = document.getElementById('btn-torch-toggle');
        if (res.is_on) {
          btn.classList.add('active');
          JarvisUI.addJarvisMessage("Flashlight ON kar di hai Sir.");
        } else {
          btn.classList.remove('active');
          JarvisUI.addJarvisMessage("Flashlight OFF kar di hai Sir.");
        }
      } catch (e) {
        console.error(e);
      }
    } else {
      const btn = document.getElementById('btn-torch-toggle');
      btn.classList.toggle('active');
      JarvisUI.addJarvisMessage("Torch state toggled (Simulation mode).");
    }
  }

  return {
    handleUserQuery,
    handleQuickAction,
    toggleTorchDirect
  };
})();
