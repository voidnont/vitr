import { createBackend } from './backend.mjs';

const tauri = window.__TAURI__;

if (tauri?.core?.invoke) {
  window.vitrPlatformBackend = createBackend({
    invoke: tauri.core.invoke,
    listen: tauri.event?.listen || null,
    convertFileSrc: tauri.core.convertFileSrc || ((value) => value),
  });
  window.dispatchEvent(new CustomEvent('vitr-backend-ready'));
} else {
  console.warn('Vitr desktop backend bridge is unavailable.');
}
