'use strict';

const retryError = document.getElementById('retry-error');
if (retryError && !retryError.hidden) {
  retryError.addEventListener('click', () => window.location.reload());
}

// SB Admin 2 меняет вид панели; синхронизируем состояние её кнопок для скринридеров.
function syncErrorSidebar() {
  const expanded = !document.getElementById('accordionSidebar').classList.contains('toggled');
  for (const id of ['sidebarToggle', 'sidebarToggleTop']) {
    const button = document.getElementById(id);
    button.setAttribute('aria-expanded', String(expanded));
    button.setAttribute('aria-label', expanded ? 'Свернуть боковую панель' : 'Развернуть боковую панель');
  }
}

for (const id of ['sidebarToggle', 'sidebarToggleTop']) {
  document.getElementById(id).addEventListener('click', syncErrorSidebar);
}
window.addEventListener('resize', syncErrorSidebar);
syncErrorSidebar();
