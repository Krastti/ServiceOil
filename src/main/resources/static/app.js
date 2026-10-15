'use strict';

// The page uses the existing JSON API; all financial calculations stay on the server.
const byId = id => document.getElementById(id);
const money = value => Number(value).toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateText = date => new Date(date + 'T12:00:00').toLocaleDateString('ru-RU');
const chartColors = ['var(--series-a)', 'var(--series-b)'];
let catalog, loaded, active;
let serial = 0, estimateSerial = 0, rowId = 0;

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

async function postJson(path, body, options = {}) {
  let response;
  try {
    response = await fetch('/api/v1' + path, {
      ...options, method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw Error('Сервер недоступен. Проверьте запуск приложения и повторите запрос.');
  }
  let result;
  try { result = await response.json(); }
  catch { throw Error('Сервер вернул непонятный ответ.'); }
  if (!response.ok) throw Error(result.message || 'Не удалось загрузить данные.');
  return result;
}

function status(text, error = false) {
  byId('status').textContent = text;
  byId('status').className = 'app-status alert small ' + (error ? 'alert-danger' : 'alert-light border');
}
function option(value, label) {
  const node = element('option', label);
  node.value = value;
  return node;
}
function regionName(id) { return catalog.regions.find(region => region.regionId === id)?.name || id; }

function availablePeriod() {
  const period = catalog.availability.find(item => item.regionId === byId('region').value && item.fuelCode === byId('fuel').value);
  if (period) { byId('from').value = period.from; byId('to').value = period.to; }
}
function updateFuels() {
  const previous = byId('fuel').value;
  const supported = catalog.availability.filter(item => item.regionId === byId('region').value).map(item => item.fuelCode);
  byId('fuel').replaceChildren(...catalog.fuels.filter(fuel => supported.includes(fuel.code)).map(fuel => option(fuel.code, fuel.name)));
  byId('fuel').value = supported.includes(previous) ? previous : (supported.includes('DIESEL') ? 'DIESEL' : supported[0]);
  updateRegionsB();
}
function updateRegionsB() {
  const previous = byId('region-b').value;
  const regions = catalog.regions.filter(region => region.regionId !== byId('region').value
    && catalog.availability.some(item => item.regionId === region.regionId && item.fuelCode === byId('fuel').value));
  byId('region-b').replaceChildren(option('', 'Без сравнения'), ...regions.map(region => option(region.regionId, region.name)));
  if (regions.some(region => region.regionId === previous)) byId('region-b').value = previous;
}
function resetResults() {
  estimateSerial++;
  loaded = null;
  byId('calculate').disabled = true;
  ['summary', 'chart', 'points-table', 'legend', 'price-date', 'estimate-result'].forEach(id => byId(id).replaceChildren());
  byId('estimate-error').textContent = '';
}
function invalidate() {
  serial++;
  active?.abort();
  resetResults();
  byId('show').disabled = false;
  byId('history').setAttribute('aria-busy', 'false');
  byId('comparison-note').textContent = 'Нажмите «Показать», чтобы обновить выбранные данные.';
  status('Фильтры изменены. Нажмите «Показать».');
}

function source(metadata, points) {
  const modes = { DEMO: 'Демонстрационные данные', OPEN_DATA: 'Открытые данные' };
  const frequency = { MONTHLY: 'месячные', WEEKLY: 'недельные', DAILY: 'дневные' }[metadata.frequency];
  byId('data-mode').textContent = (modes[metadata.dataMode] || metadata.dataMode) + (frequency ? ' · ' + frequency : '');
  byId('data-mode').className = 'badge mb-3 ' + (metadata.dataMode === 'DEMO' ? 'badge-warning' : 'badge-primary');
  byId('source-name').textContent = metadata.sourceName;
  byId('source-method').textContent = metadata.methodology;
  byId('import-date').textContent = 'Загружено: ' + (metadata.lastImportedAt ? new Date(metadata.lastImportedAt).toLocaleString('ru-RU') : 'неизвестно');
  byId('warnings').replaceChildren(...metadata.warnings.map(warning => element('li', warning)));
  const links = [...new Set(points.map(point => point.sourceUrl))];
  byId('source-links').replaceChildren(...links.flatMap((url, index) => {
    try { if (new URL(url).protocol !== 'https:') return []; } catch { return []; }
    const link = element('a', 'Публикация источника ' + (index + 1) + ' ↗');
    link.href = url;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    return [link];
  }));
}

function metric(label, value, unit, detail, tone, icon) {
  const column = element('div', undefined, 'col-md-6 col-xl-3');
  const card = element('article', undefined, 'card border-left-' + tone + ' shadow metric-card mb-4');
  const body = element('div', undefined, 'card-body d-flex align-items-center justify-content-between');
  const text = element('div');
  const amount = element('div', value, 'metric-value');
  if (unit) amount.append(element('span', ' ' + unit, 'metric-unit'));
  text.append(element('h3', label, 'metric-label text-' + tone), amount, element('p', detail, 'metric-detail'));
  const image = element('i', undefined, 'fas ' + icon + ' metric-icon');
  image.setAttribute('aria-hidden', 'true');
  body.append(text, image);
  card.append(body);
  column.append(card);
  return column;
}
function renderSummary(summary, history, other, comparison) {
  const hasPrice = summary.price !== null;
  const priceCard = (id, item, tone) => metric(regionName(id), item.price === null ? 'Нет данных' : money(item.price),
    item.price === null ? '' : '₽/л', item.lastDate ? 'На ' + dateText(item.lastDate) : 'За выбранный период', tone, 'fa-gas-pump');
  const change = summary.delta === null ? '—' : (summary.delta > 0 ? '+' : '') + money(summary.delta);
  const cards = [priceCard(byId('region').value, summary, 'primary')];
  if (other) cards.push(priceCard(byId('region-b').value, other, 'success'));
  cards.push(metric('Изменение в основном регионе', change, summary.delta === null ? '' : '₽/л',
    summary.deltaPercent === null ? 'Нужно минимум два наблюдения' : money(summary.deltaPercent) + '% за выбранный период',
    'info', 'fa-chart-line'));
  if (other) {
    const point = comparison?.comparison;
    cards.push(metric('Разница между регионами', point ? money(point.difference) : '—', point ? '₽/л' : '',
      point ? 'На ' + dateText(point.date) + ' · основной минус второй' : 'Нет общей даты наблюдений', 'warning', 'fa-exchange-alt'));
  } else {
    cards.push(metric('Наблюдения', String(history.points.length), '', 'Опубликованные точки в выбранном периоде', 'success', 'fa-chart-bar'));
    cards.push(metric('Последняя дата', hasPrice ? dateText(summary.lastDate) : '—', '',
      hasPrice ? 'Дата опубликованной цены' : 'В периоде нет наблюдений', 'warning', 'fa-calendar-alt'));
  }
  byId('summary').replaceChildren(...cards);
}

function svg(tag, attributes, text) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
  if (text !== undefined) node.textContent = text;
  return node;
}
function draw(series) {
  const chart = byId('chart');
  chart.replaceChildren();
  byId('legend').replaceChildren();
  byId('points-table').replaceChildren();
  const all = series.flatMap(item => item.points);
  if (!all.length) {
    chart.append(svg('text', { x: 400, y: 145, 'text-anchor': 'middle' }, 'В этом периоде нет наблюдений'));
    return;
  }
  const dates = [...new Set(all.map(point => point.date))].sort();
  const start = Date.parse(dates[0]), end = Date.parse(dates.at(-1));
  const prices = all.map(point => Number(point.price));
  const low = Math.min(...prices), high = Math.max(...prices), padding = Math.max((high - low) * .2, .5);
  const min = low - padding, max = high + padding;
  const x = date => 70 + (Date.parse(date) - start) / (end - start || 1) * 680;
  const y = price => 230 - (Number(price) - min) / (max - min) * 200;
  for (let i = 0; i < 5; i++) {
    const price = min + (max - min) * i / 4;
    chart.append(svg('line', { x1: 70, y1: y(price), x2: 750, y2: y(price), stroke: 'var(--chart-grid)', 'stroke-dasharray': '4 4' }),
      svg('text', { x: 58, y: y(price) + 4, 'text-anchor': 'end' }, money(price)));
  }
  // Keep labels readable when an imported source contains many observations.
  const labelStep = Math.max(1, Math.ceil(dates.length / 6));
  dates.forEach((date, index) => {
    if (index % labelStep === 0 || index === dates.length - 1) chart.append(svg('text', { x: x(date), y: 266, 'text-anchor': 'middle' }, dateText(date)));
  });
  series.forEach((item, index) => {
    const color = chartColors[index % chartColors.length];
    const legend = element('span', regionName(item.id));
    legend.style.setProperty('--color', color);
    byId('legend').append(legend);
    let previous;
    item.points.forEach(point => {
      if (previous) {
        const a = new Date(previous.date + 'T12:00:00'), b = new Date(point.date + 'T12:00:00');
        const months = (b.getFullYear() - a.getFullYear()) * 12 + b.getMonth() - a.getMonth();
        const days = (Date.parse(point.date) - Date.parse(previous.date)) / 86400000;
        const contiguous = catalog.metadata.frequency === 'MONTHLY' ? months === 1 : days === (catalog.metadata.frequency === 'WEEKLY' ? 7 : 1);
        if (contiguous) chart.append(svg('line', { x1: x(previous.date), y1: y(previous.price), x2: x(point.date), y2: y(point.price), stroke: color, 'stroke-width': 3 }));
      }
      const label = regionName(item.id) + ' · ' + dateText(point.date) + ' · ' + money(point.price) + ' ₽/л';
      const dot = svg('circle', { cx: x(point.date), cy: y(point.price), r: 5, fill: color, stroke: 'var(--surface)', 'stroke-width': 2, tabindex: 0, 'aria-label': label });
      dot.append(svg('title', {}, label));
      chart.append(dot);
      previous = point;
      const row = element('tr');
      row.append(element('td', regionName(item.id)), element('td', dateText(point.date)), element('td', money(point.price)));
      byId('points-table').append(row);
    });
  });
}

async function load(event) {
  event?.preventDefault();
  const id = ++serial;
  active?.abort();
  active = new AbortController();
  const signal = active.signal;
  resetResults();
  byId('show').disabled = true;
  byId('history').setAttribute('aria-busy', 'true');
  status('Загружаем цены…');
  const a = byId('region').value, b = byId('region-b').value;
  const fuel = byId('fuel').value, from = byId('from').value, to = byId('to').value;
  if (!from || !to || from > to || (Date.parse(to) - Date.parse(from)) / 86400000 >= 366) {
    status('Выберите период от 1 до 366 дней: начало не позже конца.', true);
    byId('comparison-note').textContent = 'Укажите корректный период и нажмите «Показать».';
    byId('show').disabled = false;
    byId('history').setAttribute('aria-busy', 'false');
    return;
  }
  const query = { sourceId: catalog.metadata.source, regionId: a, fuelCode: fuel, from, to };
  try {
    const [summary, history, comparison, other] = await Promise.all([
      postJson('/prices/summary', query, { signal }),
      postJson('/prices/history', query, { signal }),
      b ? postJson('/prices/compare', { sourceId: catalog.metadata.source, regionA: a, regionB: b, fuelCode: fuel, from, to }, { signal }) : null,
      b ? postJson('/prices/summary', { ...query, regionId: b }, { signal }) : null
    ]);
    if (id !== serial) return;
    loaded = { a, b, fuel, summary, comparison };
    renderSummary(summary, history, other, comparison);
    const series = [{ id: a, points: history.points }, ...(comparison ? [{ id: b, points: comparison.historyB }] : [])];
    draw(series);
    source(summary.metadata, series.flatMap(item => item.points));
    const dates = comparison ? comparison.commonDates : history.points.map(point => point.date);
    byId('price-date').replaceChildren(...dates.map(date => option(date, dateText(date))));
    if (dates.length) byId('price-date').value = dates.at(-1);
    byId('calculate').disabled = !dates.length;
    byId('comparison-note').textContent = comparison
      ? (comparison.comparison ? 'На общей дате ' + dateText(comparison.comparison.date) + ' разница ' + money(comparison.comparison.difference) + ' ₽/л (основной регион минус второй).' : comparison.warnings.join(' '))
      : 'Точки — даты наблюдения. Пропуски не заменяются нулями. Для сравнения выберите второй регион.';
    const hasPoints = series.some(item => item.points.length);
    status(hasPoints ? 'Показаны сохранённые наблюдения. ' + (summary.metadata.frequency === 'MONTHLY' ? 'Месячная статистика.' : '')
      : 'За выбранный период данных нет. Выберите даты из доступного набора.');
  } catch (error) {
    if (error.name !== 'AbortError' && id === serial) {
      status('Не удалось обновить цены: ' + error.message, true);
      byId('comparison-note').textContent = 'Цены не загружены. Повторите запрос кнопкой «Показать».';
    }
  } finally {
    if (id === serial) {
      byId('show').disabled = false;
      byId('history').setAttribute('aria-busy', 'false');
    }
  }
}

function clearEstimate() {
  estimateSerial++;
  byId('estimate-result').replaceChildren();
  byId('estimate-error').textContent = '';
  byId('calculate').disabled = !loaded || !byId('price-date').value;
}
function addVehicle() {
  if (byId('vehicles').children.length >= 100) return;
  const row = element('div', undefined, 'vehicle');
  const id = ++rowId;
  const fields = [['name', 'Техника', 'Трактор', 'text'], ['tankCapacity', 'Ёмкость, л', 200, 'number'],
    ['remainingLiters', 'Остаток, л', 50, 'number'], ['count', 'Количество', 2, 'number']];
  fields.forEach(([key, title, value, type]) => {
    const label = element('label', title), input = element('input', undefined, 'form-control');
    input.type = type;
    input.name = key;
    input.value = value;
    input.id = 'vehicle-' + id + '-' + key;
    input.required = true;
    label.htmlFor = input.id;
    if (type === 'number') {
      input.min = key === 'remainingLiters' ? '0' : '1';
      input.max = key === 'count' ? '1000' : '1000000';
      input.step = key === 'count' ? '1' : 'any';
    } else input.maxLength = 100;
    label.append(input);
    row.append(label);
  });
  const remove = element('button', undefined, 'btn btn-outline-danger vehicle-remove');
  remove.type = 'button';
  remove.setAttribute('aria-label', 'Удалить строку техники ' + id);
  remove.title = 'Удалить технику';
  const icon = element('i', undefined, 'fas fa-trash-alt');
  icon.setAttribute('aria-hidden', 'true');
  remove.append(icon);
  remove.onclick = () => { row.remove(); byId('add-vehicle').disabled = false; clearEstimate(); };
  row.append(remove);
  row.addEventListener('input', clearEstimate);
  byId('vehicles').append(row);
  byId('add-vehicle').disabled = byId('vehicles').children.length >= 100;
  clearEstimate();
}
function calcMode() {
  const volume = byId('calc-mode').value === 'volume';
  byId('volume-label').hidden = !volume;
  byId('volume').required = volume;
  byId('volume').disabled = !volume;
  byId('fleet').hidden = volume;
  byId('vehicles').querySelectorAll('input').forEach(input => input.disabled = volume);
  clearEstimate();
}
async function estimate(event) {
  event.preventDefault();
  if (!loaded) return;
  const id = ++estimateSerial, selection = loaded;
  byId('calculate').disabled = true;
  byId('estimate-result').replaceChildren();
  byId('estimate-error').textContent = '';
  const body = { sourceId: catalog.metadata.source, regionId: loaded.a, fuelCode: loaded.fuel, priceDate: byId('price-date').value };
  if (loaded.b) body.comparisonRegionId = loaded.b;
  if (byId('calc-mode').value === 'volume') body.volumeLiters = Number(byId('volume').value);
  else body.vehicles = [...byId('vehicles').children].map(row =>
    Object.fromEntries([...row.querySelectorAll('input')].map(input => [input.name, input.type === 'number' ? Number(input.value) : input.value])));
  try {
    const result = await postJson('/refueling/estimate', body);
    if (id !== estimateSerial || selection !== loaded) return;
    const box = element('div', undefined, 'result');
    const volume = element('div');
    volume.append(element('span', 'Нужно топлива'), element('strong', money(result.totalLiters) + ' л'));
    const cost = element('div');
    cost.append(element('span', regionName(result.regionId)), element('strong', money(result.estimatedCost) + ' ₽'),
      element('p', money(result.pricePerLiter) + ' ₽/л · ' + dateText(result.priceDate)));
    box.append(volume, cost);
    if (result.comparison) {
      const other = element('div');
      other.append(element('span', regionName(result.comparison.regionId)), element('strong', money(result.comparison.estimatedCost) + ' ₽'),
        element('p', 'Разница бюджетов: ' + money(result.comparison.difference) + ' ₽'));
      box.append(other);
    }
    byId('estimate-result').replaceChildren(box);
  } catch (error) {
    if (id === estimateSerial) byId('estimate-error').textContent = error.message;
  } finally {
    if (id === estimateSerial) byId('calculate').disabled = !loaded || !byId('price-date').value;
  }
}

function setupNavigation() {
  const sidebar = byId('accordionSidebar');
  const mobile = window.matchMedia('(max-width: 767px)');
  function setSize() {
    document.body.classList.toggle('sidebar-toggled', mobile.matches);
    sidebar.classList.toggle('toggled', mobile.matches);
  }
  function syncButtons() {
    const expanded = !sidebar.classList.contains('toggled');
    ['sidebarToggle', 'sidebarToggleTop'].forEach(id => {
      byId(id).setAttribute('aria-expanded', String(expanded));
      byId(id).setAttribute('aria-label', expanded ? 'Свернуть боковую панель' : 'Открыть боковую панель');
    });
  }
  setSize();
  syncButtons();
  new MutationObserver(syncButtons).observe(sidebar, { attributes: true, attributeFilter: ['class'] });
  mobile.addEventListener('change', setSize);
  const links = [...sidebar.querySelectorAll('.nav-link')];
  function activate(target) {
    links.forEach(link => {
      const selected = link.getAttribute('href') === target;
      link.parentElement.classList.toggle('active', selected);
      if (selected) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    });
  }
  links.forEach(link => link.addEventListener('click', () => {
    activate(link.getAttribute('href'));
    if (mobile.matches) { document.body.classList.add('sidebar-toggled'); sidebar.classList.add('toggled'); }
  }));
  // Navigation state also follows the sections reached by scrolling.
  const observer = new IntersectionObserver(entries => {
    entries.filter(entry => entry.isIntersecting).forEach(entry => activate('#' + entry.target.id));
  }, { rootMargin: '-5% 0px -60% 0px' });
  ['overview', 'history', 'calculator', 'sources'].forEach(id => observer.observe(byId(id)));
}

byId('filters').addEventListener('submit', load);
byId('all-period').onclick = () => { availablePeriod(); load(); };
byId('source-select').onchange = async () => {
  const previousSource = catalog?.metadata.source;
  [...byId('filters').elements].forEach(control => control.disabled = true);
  byId('add-vehicle').disabled = true;
  serial++;
  active?.abort();
  resetResults();
  try {
    await loadCatalog(byId('source-select').value);
    [...byId('filters').elements].forEach(control => control.disabled = false);
    byId('add-vehicle').disabled = false;
    await load();
  } catch (error) {
    if (previousSource) byId('source-select').value = previousSource;
    status('Не удалось открыть набор: ' + error.message, true);
    [...byId('filters').elements].forEach(control => control.disabled = false);
    byId('add-vehicle').disabled = false;
    byId('show').disabled = !catalog;
  }
};
byId('region').onchange = () => { updateFuels(); availablePeriod(); invalidate(); };
byId('fuel').onchange = () => { updateRegionsB(); availablePeriod(); invalidate(); };
['region-b', 'from', 'to'].forEach(id => byId(id).onchange = invalidate);
byId('calc-mode').onchange = calcMode;
byId('price-date').onchange = clearEstimate;
byId('volume').oninput = clearEstimate;
byId('add-vehicle').onclick = addVehicle;
byId('estimate-form').addEventListener('submit', estimate);
setupNavigation();

async function init() {
  [...byId('filters').elements].forEach(control => control.disabled = true);
  byId('add-vehicle').disabled = true;
  try {
    await loadCatalog('ROSSTAT_PFO_WEEKLY');
    [...byId('filters').elements].forEach(control => control.disabled = false);
    byId('add-vehicle').disabled = false;
    addVehicle();
    calcMode();
    byId('all-period').disabled = false;
    await load();
  } catch (error) {
    status('Не удалось открыть набор: ' + error.message, true);
    byId('coverage').textContent = 'Доступный период не загружен';
    byId('data-mode').textContent = 'Данные не загружены';
    byId('comparison-note').textContent = 'Обновите страницу, чтобы повторить загрузку набора.';
    byId('show').disabled = true;
  }
}

async function loadCatalog(sourceId) {
  const nextCatalog = await postJson('/catalog', { sourceId });
  if (!nextCatalog.availability.length) throw Error('В выбранном наборе пока нет цен.');
  catalog = nextCatalog;
  byId('source-select').value = catalog.metadata.source;
  byId('region').replaceChildren(...catalog.regions.map(region => option(region.regionId, region.name)));
  updateFuels();
  availablePeriod();
  const dates = catalog.availability.flatMap(item => [item.from, item.to]).sort();
  byId('coverage').textContent = 'Архив: ' + dateText(dates[0]) + ' — ' + dateText(dates.at(-1));
  source(catalog.metadata, []);
}
init();

