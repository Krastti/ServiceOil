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
    get('manage-vehicle-templates').hidden = !value.authenticated;
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
  let templatesBusy = false, savedTemplates = [], editingId = null, deletingId = null;
  const templatesFeedback = (text, type) => {
    get('templates-feedback').textContent = text;
    get('templates-feedback').className = 'alert alert-' + type;
    get('templates-feedback').hidden = false;
  };
  const templatesBusyState = value => {
    templatesBusy = value;
    get('vehicle-templates-modal').querySelectorAll('button, input, select').forEach(control => control.disabled = value);
    get('templates-list').setAttribute('aria-busy', String(value));
  };
  function closeTemplateActions() {
    editingId = deletingId = null;
    get('template-edit-form').hidden = true;
    get('template-delete-confirmation').hidden = true;
  }
  function renderTemplates() {
    get('templates-empty').hidden = savedTemplates.length > 0;
    get('templates-table').hidden = !savedTemplates.length;
    get('templates-list').replaceChildren(...savedTemplates.map(template => {
      const row = document.createElement('tr');
      [template.name, template.tankCapacity.toLocaleString('ru-RU'), template.fuelCode === 'DIESEL' ? 'Дизельное топливо' : 'АИ-92'].forEach(value => {
        const cell = document.createElement('td');
        cell.textContent = value;
        row.append(cell);
      });
      const actions = document.createElement('td');
      const button = (text, style, handler) => {
        const control = document.createElement('button');
        control.type = 'button';
        control.className = 'btn btn-sm btn-outline-' + style;
        control.textContent = text;
        control.setAttribute('aria-label', text + ' шаблон «' + template.name + '»');
        control.onclick = handler;
        actions.append(control);
      };
      button('Редактировать', 'primary', () => {
        closeTemplateActions();
        editingId = template.id;
        get('template-edit-name').value = template.name;
        get('template-edit-capacity').value = template.tankCapacity;
        get('template-edit-fuel').value = template.fuelCode;
        get('template-edit-form').hidden = false;
        get('template-edit-name').focus();
      });
      button('Удалить', 'danger', () => {
        closeTemplateActions();
        deletingId = template.id;
        get('template-delete-question').textContent = 'Удалить шаблон «' + template.name + '»?';
        get('template-delete-confirmation').hidden = false;
        get('template-delete-cancel').focus();
      });
      row.append(actions);
      return row;
    }));
  }
  async function refreshTemplates() {
    if (templatesBusy) return;
    closeTemplateActions();
    templatesBusyState(true);
    templatesFeedback('Загружаем шаблоны…', 'info');
    try {
      const response = await fetch('/api/v1/vehicle-templates', { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) throw Error('Не удалось загрузить шаблоны. Повторите попытку.');
      savedTemplates = await response.json();
      renderTemplates();
      get('templates-feedback').hidden = true;
      document.dispatchEvent(new CustomEvent('vehicle-templates-changed', { detail: savedTemplates }));
    } catch (error) {
      templatesFeedback(error.message || 'Сервер недоступен. Повторите попытку.', 'danger');
    } finally { templatesBusyState(false); }
  }
  async function changeTemplate(method, id, body) {
    templatesBusyState(true);
    get('templates-login-link').hidden = true;
    templatesFeedback(method === 'DELETE' ? 'Удаляем шаблон…' : 'Сохраняем изменения…', 'info');
    try {
      const auth = await session();
      if (!auth.authenticated) { get('templates-login-link').hidden = false; throw Error('Сессия завершена. Войдите снова.'); }
      const response = await fetch('/api/v1/admin/vehicle-templates/' + id, {
        method, credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [auth.csrfHeader]: auth.csrfToken },
        body: body ? JSON.stringify(body) : undefined
      });
      const result = response.status === 204 ? null : await response.json();
      if (!response.ok) {
        if (response.status === 401) { get('templates-login-link').hidden = false; await session(); }
        throw Error(result.message || 'Не удалось изменить шаблон. Обновите список и повторите попытку.');
      }
      savedTemplates = method === 'DELETE' ? savedTemplates.filter(template => template.id !== id)
        : savedTemplates.map(template => template.id === id ? result : template);
      savedTemplates.sort((a, b) => a.name.localeCompare(b.name, 'ru') || a.id - b.id);
      closeTemplateActions();
      renderTemplates();
      document.dispatchEvent(new CustomEvent('vehicle-templates-changed', { detail: savedTemplates }));
      templatesFeedback(method === 'DELETE' ? 'Шаблон удалён.' : 'Изменения сохранены. Данные в калькуляторе обновлены.', 'success');
    } catch (error) {
      templatesFeedback(error.message || 'Не удалось получить ответ. Обновите список перед повторной попыткой.', 'danger');
    } finally { templatesBusyState(false); get('templates-refresh').focus(); }
  }
  $('#vehicle-templates-modal').on('show.bs.modal', () => {
    savedTemplates = [];
    renderTemplates();
    get('templates-empty').hidden = true;
    get('templates-login-link').hidden = true;
    refreshTemplates();
  });
  $('#vehicle-templates-modal').on('hide.bs.modal', event => { if (templatesBusy) event.preventDefault(); });
  get('templates-refresh').onclick = refreshTemplates;
  get('template-edit-cancel').onclick = get('template-delete-cancel').onclick = () => {
    closeTemplateActions();
    get('templates-refresh').focus();
  };
  get('template-edit-form').addEventListener('submit', event => {
    event.preventDefault();
    if (templatesBusy || editingId === null) return;
    const name = get('template-edit-name').value.trim();
    if (!name) { get('template-edit-name').focus(); return; }
    changeTemplate('PUT', editingId, { name, tankCapacity: Number(get('template-edit-capacity').value), fuelCode: get('template-edit-fuel').value });
  });
  get('template-delete-submit').onclick = () => {
    if (!templatesBusy && deletingId !== null) changeTemplate('DELETE', deletingId);
  };
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
