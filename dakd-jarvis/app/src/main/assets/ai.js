/**
 * DAKD JARVIS - AI Engine Bridge
 */
const JarvisAI = (function() {
  function processQuery(query) {
    JarvisUI.showCoreStatus('PROCESSING COMMAND...');

    if (JarvisStorage.isNative() && typeof window.AndroidBridge.processCommand === 'function') {
      window.AndroidBridge.processCommand(query);
    } else {
      // Mock processing for browser fallback
      setTimeout(() => {
        const lower = query.toLowerCase();
        let reply = `Sir, your command '${query}' received.`;
        let action = "speak_only";
        let target = "";
        let requiresConfirm = false;

        if (lower.includes("whatsapp")) {
          reply = "Sure Sir, opening WhatsApp.";
          action = "open_app";
          target = "whatsapp";
        } else if (lower.includes("torch") || lower.includes("light")) {
          reply = "Toggling flashlight Sir.";
          action = "toggle_torch";
        } else if (lower.includes("camera")) {
          reply = "Camera ready Sir.";
          action = "open_camera";
        } else if (lower.includes("call") || lower.includes("phone")) {
          reply = "Danish ko call karne ki permission hai?";
          action = "make_call";
          requiresConfirm = true;
        }

        JarvisUI.handleExecutionResult({
          success: true,
          reply: reply,
          action_executed: action,
          requires_confirmation: requiresConfirm,
          pending_action: requiresConfirm ? { action, target, reply } : null
        });
      }, 700);
    }
  }

  function confirmAction(pendingAction) {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.executeConfirmedAction === 'function') {
      window.AndroidBridge.executeConfirmedAction(JSON.stringify(pendingAction));
    } else {
      JarvisUI.handleExecutionResult({
        success: true,
        reply: "Action authorized and successfully executed Sir.",
        action_executed: pendingAction.action || "confirmed",
        requires_confirmation: false
      });
    }
  }

  return {
    processQuery,
    confirmAction
  };
})();
