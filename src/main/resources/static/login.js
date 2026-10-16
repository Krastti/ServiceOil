'use strict';

(async () => {
  const message = document.getElementById('login-message');
  if (new URLSearchParams(location.search).has('error')) {
    message.textContent = 'Неверный логин или пароль. Проверьте данные и повторите вход.';
    message.hidden = false;
  }
  try {
    const response = await fetch('/api/v1/admin/session', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw Error();
    const session = await response.json();
    if (session.authenticated) { location.replace('/admin'); return; }
    document.getElementById('login-csrf').value = session.csrfToken;
    document.getElementById('login-submit').disabled = false;
  } catch {
    message.textContent = 'Не удалось подготовить вход. Обновите страницу и повторите попытку.';
    message.hidden = false;
  }
})();
