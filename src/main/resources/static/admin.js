'use strict';

(() => {
  let busy = false, imported = false, templateBusy = false, accountBusy = false;
  const get = id => document.getElementById(id);
  const feedback = (text, type) => {
    get('import-feedback').textContent = text;
    get('import-feedback').className = 'alert mb-0 alert-' + type;
    get('import-feedback').hidden = false;
  };
  async function session() {
    const response = await fetch('/api/v1/admin/session', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw Error('Не удалось проверить вход администратора.');
    const value = await response.json();
    get('admin-login-item').hidden = value.authenticated;
    get('admin-import-item').hidden = !value.authenticated;
    get('admin-logout-item').hidden = !value.authenticated;
    get('admin-account-item').hidden = !value.authenticated;
    get('add-vehicle-template').hidden = !value.authenticated;
    get('admin-name').textContent = value.username || '';
    get('logout-csrf').value = value.csrfToken;
    if (value.authenticated) document.querySelector('.topbar-title > span').textContent = 'Панель администратора';
    return value;
  }
  const date = value => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
  const today = new Date(), from = new Date();
  from.setDate(from.getDate() - 14);
  get('import-from').value = date(from);
  get('import-to').value = date(today);

  $('#rosstat-import-modal').on('hide.bs.modal', event => { if (busy) event.preventDefault(); });
  $('#rosstat-import-modal').on('hidden.bs.modal', () => { if (imported) location.reload(); });
  get('rosstat-import-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (busy) return;
    const from = get('import-from').value, to = get('import-to').value;
    if (!from || !to || from > to || (Date.parse(to) - Date.parse(from)) / 86400000 >= 366) {
      feedback('Выберите период от 1 до 366 дней: начало не позже конца.', 'danger');
      return;
    }
    busy = true;
    get('import-submit').disabled = true;
    document.querySelectorAll('.import-close').forEach(button => button.disabled = true);
    get('import-login-link').hidden = true;
    feedback('Загружаем публикации и сохраняем недельные цены…', 'info');
    try {
      const auth = await session();
      if (!auth.authenticated) { get('import-login-link').hidden = false; throw Error('Сессия завершена. Войдите снова.'); }
      const response = await fetch('/api/v1/admin/rosstat/import', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [auth.csrfHeader]: auth.csrfToken },
        body: JSON.stringify({ from, to })
      });
      const result = await response.json();
      if (!response.ok) {
        if (response.status === 401) { get('import-login-link').hidden = false; await session(); }
        throw Error(result.message || 'Не удалось выполнить импорт.');
      }
      feedback(`Импорт завершён. Найдено публикаций: ${result.publicationsFound}. Обработано строк: ${result.rowsImported}.`, 'success');
      imported = true;
    } catch (error) {
      feedback(error.message || 'Сервер недоступен. Повторите попытку.', 'danger');
    } finally {
      busy = false;
      get('import-submit').disabled = false;
      document.querySelectorAll('.import-close').forEach(button => button.disabled = false);
    }
  });
  $('#vehicle-template-modal').on('hide.bs.modal', event => { if (templateBusy) event.preventDefault(); });
  $('#vehicle-template-modal').on('show.bs.modal', () => {
    get('template-feedback').hidden = true;
    get('template-login-link').hidden = true;
  });
  get('vehicle-template-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (templateBusy) return;
    const name = get('template-name').value.trim();
    if (!name) { get('template-name').focus(); return; }
    templateBusy = true;
    get('template-submit').disabled = true;
    document.querySelectorAll('.template-close').forEach(button => button.disabled = true);
    get('template-login-link').hidden = true;
    const feedback = (text, type) => {
      get('template-feedback').textContent = text;
      get('template-feedback').className = 'alert mb-0 alert-' + type;
      get('template-feedback').hidden = false;
    };
    feedback('Сохраняем шаблон…', 'info');
    try {
      const auth = await session();
      if (!auth.authenticated) { get('template-login-link').hidden = false; throw Error('Сессия завершена. Войдите снова.'); }
      const response = await fetch('/api/v1/admin/vehicle-templates', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [auth.csrfHeader]: auth.csrfToken },
        body: JSON.stringify({ name, tankCapacity: Number(get('template-capacity').value), fuelCode: get('template-fuel').value })
      });
      const result = await response.json();
      if (!response.ok) {
        if (response.status === 401) { get('template-login-link').hidden = false; await session(); }
        throw Error(result.message || 'Не удалось сохранить шаблон.');
      }
      get('vehicle-template-form').reset();
      feedback('Шаблон «' + result.name + '» сохранён и добавлен в список техники.', 'success');
      document.dispatchEvent(new CustomEvent('vehicle-template-created', { detail: result }));
    } catch (error) {
      feedback(error.message || 'Не удалось сохранить шаблон. Повторите попытку.', 'danger');
    } finally {
      templateBusy = false;
      get('template-submit').disabled = false;
      document.querySelectorAll('.template-close').forEach(button => button.disabled = false);
    }
  });
  const validatePasswords = () => {
    const password = get('account-new-password'), confirmation = get('account-confirm-password');
    password.setCustomValidity(password.value && new TextEncoder().encode(password.value).length > 72
      ? 'Новый пароль должен занимать не более 72 байт в UTF-8.' : '');
    confirmation.setCustomValidity(password.value !== confirmation.value ? 'Пароли не совпадают.' : '');
  };
  ['account-new-password', 'account-confirm-password'].forEach(id => get(id).addEventListener('input', validatePasswords));
  $('#admin-account-modal').on('show.bs.modal', () => {
    get('admin-account-form').reset();
    get('account-username').value = get('admin-name').textContent;
    get('account-feedback').hidden = true;
    get('account-login-link').hidden = true;
    validatePasswords();
  });
  $('#admin-account-modal').on('hide.bs.modal', event => { if (accountBusy) event.preventDefault(); });
  $('#admin-account-modal').on('hidden.bs.modal', () => { get('admin-account-form').reset(); validatePasswords(); });
  get('admin-account-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (accountBusy) return;
    validatePasswords();
    if (!get('admin-account-form').reportValidity()) return;
    const body = { username: get('account-username').value.trim(), currentPassword: get('account-current-password').value };
    if (get('account-new-password').value) body.newPassword = get('account-new-password').value;
    const feedback = (text, type) => {
      get('account-feedback').textContent = text;
      get('account-feedback').className = 'alert mb-0 alert-' + type;
      get('account-feedback').hidden = false;
    };
    accountBusy = true;
    get('account-submit').disabled = true;
    document.querySelectorAll('.account-close').forEach(button => button.disabled = true);
    get('account-login-link').hidden = true;
    feedback('Сохраняем реквизиты…', 'info');
    try {
      const auth = await session();
      if (!auth.authenticated) { get('account-login-link').hidden = false; throw Error('Сессия завершена. Войдите снова.'); }
      const response = await fetch('/api/v1/admin/account', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [auth.csrfHeader]: auth.csrfToken },
        body: JSON.stringify(body)
      });
      if (!response.ok) {
        const result = await response.json().catch(() => ({}));
        if (response.status === 401) { get('account-login-link').hidden = false; await session(); }
        throw Error(result.message || 'Не удалось изменить реквизиты.');
      }
      get('admin-account-form').reset();
      location.replace('/login?changed');
    } catch (error) {
      feedback(error.message || 'Не удалось получить ответ. Попробуйте войти с новыми реквизитами.', 'danger');
      get('account-current-password').value = '';
    } finally {
      accountBusy = false;
      get('account-submit').disabled = false;
      document.querySelectorAll('.account-close').forEach(button => button.disabled = false);
    }
  });
  session().catch(() => { /* Публичный просмотр цен доступен и при ошибке проверки сессии. */ });
})();
