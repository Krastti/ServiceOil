'use strict';

// The page uses the existing JSON API; all financial calculations stay on the server.
const byId = id => document.getElementById(id);
const money = value => Number(value).toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateText = date => new Date(date + 'T12:00:00').toLocaleDateString('ru-RU');
const chartColors = ['var(--series-a)', 'var(--series-b)'];
let catalog, loaded, active;
let serial = 0, estimateSerial = 0, rowId = 0;
let vehicleTemplates = [], priceDatesSerial = 0;

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

function availablePeriod(full = false) {
  const period = catalog.availability.find(item => item.regionId === byId('region').value && item.fuelCode === byId('fuel').value);
  if (!period) return;
  if (full) {
    byId('from').value = period.from;
    byId('to').value = period.to;
    return;
  }
  const today = new Date();
  // Ограничиваем число дней при переходе, например, с 31 марта на февраль.
  const previousMonthDays = new Date(today.getFullYear(), today.getMonth(), 0).getDate();
  const start = new Date(today.getFullYear(), today.getMonth() - 1, Math.min(today.getDate(), previousMonthDays), 12);
  const weekday = (start.getDay() + 6) % 7;
  start.setDate(start.getDate() + (weekday <= 3 ? -weekday : 7 - weekday));
  const isoDate = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  const monthStart = isoDate(start), todayText = isoDate(today);
  byId('from').value = period.from > monthStart && period.from <= todayText ? period.from : monthStart;
  // Если свежих данных нет, оставляем последний месяц, чтобы показать отсутствие наблюдений.
  byId('to').value = period.to >= byId('from').value && period.to < todayText ? period.to : todayText;
}
function updateFuels() {
  const previous = byId('fuel').value;
  const supported = catalog.availability.filter(item => item.regionId === byId('region').value).map(item => item.fuelCode);
  const fuels = catalog.fuels.filter(fuel => supported.includes(fuel.code));
  const selected = supported.includes(previous) ? previous : (supported.includes('DIESEL') ? 'DIESEL' : supported[0]);
  ['fuel', 'estimate-fuel'].forEach(id => {
    byId(id).replaceChildren(...fuels.map(fuel => option(fuel.code, fuel.name)));
    byId(id).value = selected;
  });
  byId('estimate-fuel').disabled = byId('calc-mode').value !== 'volume';
  byId('vehicles').querySelectorAll('.vehicle').forEach(updateVehicleFuels);
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
  priceDatesSerial++;
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
  const frequency = 'недельные';
  byId('data-mode').textContent = (modes[metadata.dataMode] || metadata.dataMode) + (frequency ? ' · ' + frequency : '');
  byId('data-mode').className = 'badge mb-3 ' + (metadata.dataMode === 'DEMO' ? 'badge-warning' : 'badge-primary');
  byId('source-name').textContent = metadata.sourceName;
  byId('source-method').textContent = metadata.methodology;
  byId('import-date').textContent = 'Загружено: ' + (metadata.lastImportedAt ? new Date(metadata.lastImportedAt).toLocaleString('ru-RU') : 'неизвестно');
  byId('warnings').replaceChildren(...metadata.warnings.map(warning => element('li', warning)));
  const publications = new Map();
  points.forEach(point => {
    const url = point.sourceUrl;
    if (publications.has(url)) return;
    let publishedDate;
    try {
      const sourceUri = new URL(url);
      const rosstatHost = sourceUri.hostname === 'rosstat.gov.ru' || sourceUri.hostname.endsWith('.rosstat.gov.ru');
      if (sourceUri.protocol !== 'https:' && !(sourceUri.protocol === 'http:' && rosstatHost)) return;
      // Росстат указывает дату выхода в имени страницы; дата наблюдения может быть раньше.
      const match = rosstatHost && sourceUri.pathname.match(/_(\d{2})-(\d{2})-(\d{4})\.html$/);
      if (match) publishedDate = `${match[3]}-${match[2]}-${match[1]}`;
    } catch { return; }
    publications.set(url, { url, date: publishedDate || point.date, publishedDate });
  });
  const groups = new Map();
  [...publications.values()].sort((a, b) => a.date.localeCompare(b.date) || a.url.localeCompare(b.url)).forEach(publication => {
    const month = publication.date.slice(0, 7);
    if (!groups.has(month)) groups.set(month, []);
    groups.get(month).push(publication);
  });
  byId('source-links').replaceChildren(...[...groups].map(([month, items]) => {
    const group = element('details', undefined, 'source-month');
    const monthName = new Date(month + '-01T12:00:00').toLocaleDateString('ru-RU', { month: 'long' });
    const heading = monthName[0].toUpperCase() + monthName.slice(1) + ' ' + month.slice(0, 4);
    const list = element('ul', undefined, 'source-publications');
    list.append(...items.map(publication => {
      const item = element('li');
      const label = publication.publishedDate ? 'Публикация от ' : 'Публикация · наблюдение от ';
      const link = element('a', label + dateText(publication.date) + ' ↗');
      link.href = publication.url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      item.append(link);
      return item;
    }));
    group.append(element('summary', heading, 'source-month-title text-gray-800'), list);
    return group;
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
  // Измеряем подписи в координатах SVG и заранее оставляем место для последней даты.
  const dateLabels = dates.map((date, index) => {
    const anchor = index === 0 ? 'start' : index === dates.length - 1 ? 'end' : 'middle';
    const label = svg('text', { x: x(date), y: 266, 'text-anchor': anchor }, dateText(date));
    chart.append(label);
    const width = label.getComputedTextLength();
    const left = x(date) - (anchor === 'end' ? width : anchor === 'middle' ? width / 2 : 0);
    return { label, left, right: left + width };
  });
  const labelGap = 20;
  const lastLabel = dateLabels.at(-1);
  let previousRight = -Infinity;
  dateLabels.forEach((item, index) => {
    const endpoint = index === 0 || index === dateLabels.length - 1;
    if (endpoint || (item.left >= previousRight + labelGap && item.right + labelGap <= lastLabel.left)) {
      previousRight = item.right;
    } else {
      item.label.remove();
    }
  });
  series.forEach((item, index) => {
    const color = chartColors[index % chartColors.length];
    const legend = element('span', regionName(item.id));
    legend.style.setProperty('--color', color);
    byId('legend').append(legend);
    const points = [...item.points].sort((a, b) => a.date.localeCompare(b.date));
    // Сначала соединения, затем точки: линия не перекрывает маркеры наблюдений.
    points.slice(1).forEach((point, pointIndex) => {
      const previous = points[pointIndex];
      const days = (Date.parse(point.date) - Date.parse(previous.date)) / 86400000;
      const line = svg('line', { x1: x(previous.date), y1: y(previous.price), x2: x(point.date), y2: y(point.price), stroke: color, 'stroke-width': 3 });
      // Сдвиг недельной даты на один день не считаем пропуском недели.
      if (days > 8) {
        line.setAttribute('stroke-dasharray', '6 5');
        line.append(svg('title', {}, 'Пропуск наблюдений: между точками ' + days + ' дней.'));
      }
      chart.append(line);
    });
    points.forEach(point => {
      const label = regionName(item.id) + ' · ' + dateText(point.date) + ' · ' + money(point.price) + ' ₽/л';
      const dot = svg('circle', { cx: x(point.date), cy: y(point.price), r: 5, fill: color, stroke: 'var(--surface)', 'stroke-width': 2, tabindex: 0, 'aria-label': label });
      dot.append(svg('title', {}, label));
      chart.append(dot);
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
    loaded = { a, b, fuel, summary, comparison, datesByFuel: new Map(), from, to };
    renderSummary(summary, history, other, comparison);
    const series = [{ id: a, points: history.points }, ...(comparison ? [{ id: b, points: comparison.historyB }] : [])];
    draw(series);
    source(summary.metadata, series.flatMap(item => item.points));
    const dates = comparison ? comparison.commonDates : history.points.map(point => point.date);
    loaded.datesByFuel.set(fuel, dates);
    await updatePriceDates();
    if (id !== serial) return;
    byId('comparison-note').textContent = comparison
      ? (comparison.comparison ? 'На общей дате ' + dateText(comparison.comparison.date) + ' разница ' + money(comparison.comparison.difference) + ' ₽/л (основной регион минус второй).' : comparison.warnings.join(' '))
      : 'Точки — даты наблюдения. Пропуски не заменяются нулями. Для сравнения выберите второй регион.';
    byId('comparison-note').textContent += ' Пунктир обозначает пропуски недельных наблюдений.';
    const hasPoints = series.some(item => item.points.length);
    status(hasPoints ? 'Показаны сохранённые недельные наблюдения.'
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
  byId('calculate').disabled = !loaded || !byId('price-date').value || byId('price-date').disabled
    || (byId('calc-mode').value === 'fleet' && !byId('vehicles').children.length);
}
async function loadVehicleTemplates() {
  const response = await fetch('/api/v1/vehicle-templates', { cache: 'no-store' });
  if (!response.ok) throw Error('Не удалось загрузить шаблоны техники. Обновите страницу.');
  applyVehicleTemplates(await response.json());
}
function applyVehicleTemplates(templates) {
  vehicleTemplates = templates;
  byId('template-note').textContent = vehicleTemplates.length
    ? 'Выберите шаблон и топливо для каждой строки. Ёмкость бака берётся из шаблона.'
    : 'Шаблонов техники пока нет. Администратор может добавить их в своей консоли. Доступен расчёт по объёму закупки.';
  let removed = 0;
  byId('vehicles').querySelectorAll('.vehicle').forEach(row => {
    const select = row.querySelector('[name="templateId"]');
    const selected = select.value;
    const template = vehicleTemplates.find(item => String(item.id) === selected);
    if (!template) { row.remove(); removed++; return; }
    select.replaceChildren(...vehicleTemplates.map(template => option(String(template.id), template.name)));
    select.value = selected;
    row.querySelector('[name="tankCapacity"]').value = template.tankCapacity;
    row.querySelector('[name="remainingLiters"]').max = template.tankCapacity;
  });
  byId('add-vehicle').disabled = !vehicleTemplates.length || byId('vehicles').children.length >= 100;
  if (removed) byId('template-note').textContent += ' Строки с удалёнными шаблонами убраны из расчёта: ' + removed + '.';
  calcMode();
}
function updateVehicleFuels(row) {
  if (!catalog) return;
  const select = row.querySelector('[name="fuelCode"]');
  const selected = select.value;
  const supported = catalog.availability.filter(item => item.regionId === byId('region').value).map(item => item.fuelCode);
  select.replaceChildren(...catalog.fuels.filter(fuel => supported.includes(fuel.code)).map(fuel => option(fuel.code, fuel.name)));
  if (selected && !supported.includes(selected)) select.append(option(selected, (selected === 'DIESEL' ? 'Дизель' : selected) + ' — нет данных'));
  select.value = selected || byId('fuel').value;
  select.setCustomValidity(supported.includes(select.value) ? '' : 'Для выбранного топлива нет данных в основном регионе.');
}
async function updatePriceDates() {
  const id = ++priceDatesSerial, selection = loaded;
  if (!selection) { clearEstimate(); return; }
  const previous = byId('price-date').value;
  byId('price-date').disabled = true;
  clearEstimate();
  const fuels = byId('calc-mode').value === 'volume' ? [selection.fuel]
    : [...new Set([...byId('vehicles').children].filter(row =>
      Number(row.querySelector('[name="remainingLiters"]').value) < Number(row.querySelector('[name="tankCapacity"]').value))
      .map(row => row.querySelector('[name="fuelCode"]').value))];
  try {
    const lists = await Promise.all(fuels.map(async fuelCode => {
      if (selection.datesByFuel.has(fuelCode)) return selection.datesByFuel.get(fuelCode);
      const query = { sourceId: catalog.metadata.source, regionId: selection.a, fuelCode, from: selection.from, to: selection.to };
      const [a, b] = await Promise.all([
        postJson('/prices/history', query),
        selection.b ? postJson('/prices/history', { ...query, regionId: selection.b }) : null
      ]);
      const otherDates = b ? new Set(b.points.map(point => point.date)) : null;
      const dates = a.points.map(point => point.date).filter(date => !otherDates || otherDates.has(date));
      selection.datesByFuel.set(fuelCode, dates);
      return dates;
    }));
    if (id !== priceDatesSerial || selection !== loaded) return;
    const dates = lists.length ? lists[0].filter(date => lists.every(list => list.includes(date))).sort() : [];
    byId('price-date').replaceChildren(...dates.map(date => option(date, dateText(date))));
    if (dates.length) byId('price-date').value = dates.includes(previous) ? previous : dates.at(-1);
    byId('price-date').disabled = !dates.length;
    clearEstimate();
    if (fuels.length && !dates.length) byId('estimate-error').textContent = 'Нет общей даты цены для выбранного топлива и регионов в этом периоде.';
    if (!fuels.length && byId('vehicles').children.length) byId('estimate-error').textContent = 'Для выбранной техники заправка не требуется. Проверьте остаток в баках.';
  } catch (error) {
    if (id !== priceDatesSerial || selection !== loaded) return;
    byId('price-date').replaceChildren();
    byId('estimate-error').textContent = error.message;
  }
}
function addVehicle(templateId) {
  if (!vehicleTemplates.length || byId('vehicles').children.length >= 100) return;
  const row = element('div', undefined, 'vehicle');
  const id = ++rowId;
  const fields = [['templateId', 'Техника', 'select'], ['fuelCode', 'Топливо', 'select'],
    ['tankCapacity', 'Ёмкость, л', 'number'], ['remainingLiters', 'Остаток, л', 'number'], ['count', 'Количество', 'number']];
  fields.forEach(([key, title, type]) => {
    const label = element('label', title), input = element(type === 'select' ? 'select' : 'input', undefined, type === 'select' ? 'custom-select' : 'form-control');
    if (type !== 'select') input.type = type;
    input.name = key;
    input.id = 'vehicle-' + id + '-' + key;
    input.required = true;
    label.htmlFor = input.id;
    if (type !== 'select') {
      input.min = key === 'remainingLiters' ? '0' : '1';
      input.max = key === 'count' ? '1000' : '1000000';
      input.step = key === 'count' ? '1' : 'any';
      input.value = key === 'count' ? '1' : '0';
      if (key === 'tankCapacity') { input.readOnly = true; input.min = '0.01'; }
    }
    label.append(input);
    row.append(label);
  });
  const templateSelect = row.querySelector('[name="templateId"]');
  templateSelect.append(...vehicleTemplates.map(template => option(String(template.id), template.name)));
  if (typeof templateId === 'number') templateSelect.value = String(templateId);
  const applyTemplate = () => {
    const template = vehicleTemplates.find(item => String(item.id) === templateSelect.value);
    row.querySelector('[name="tankCapacity"]').value = template.tankCapacity;
    const remaining = row.querySelector('[name="remainingLiters"]');
    remaining.max = template.tankCapacity;
    remaining.value = '0';
    const fuel = row.querySelector('[name="fuelCode"]');
    fuel.replaceChildren(option(template.fuelCode, template.fuelCode));
    updateVehicleFuels(row);
  };
  applyTemplate();
  templateSelect.onchange = () => { applyTemplate(); updatePriceDates(); };
  row.querySelector('[name="fuelCode"]').onchange = () => { updateVehicleFuels(row); updatePriceDates(); };
  const remove = element('button', undefined, 'btn btn-outline-danger vehicle-remove');
  remove.type = 'button';
  remove.setAttribute('aria-label', 'Удалить строку техники ' + id);
  remove.title = 'Удалить технику';
  const icon = element('i', undefined, 'fas fa-trash-alt');
  icon.setAttribute('aria-hidden', 'true');
  remove.append(icon);
  remove.onclick = () => { row.remove(); byId('add-vehicle').disabled = false; updatePriceDates(); };
  row.append(remove);
  row.addEventListener('input', () => updatePriceDates());
  byId('vehicles').append(row);
  byId('add-vehicle').disabled = byId('vehicles').children.length >= 100;
  calcMode();
}
function calcMode() {
  const volume = byId('calc-mode').value === 'volume';
  byId('volume-label').hidden = !volume;
  byId('volume').required = volume;
  byId('volume').disabled = !volume;
  byId('fleet').hidden = volume;
  byId('volume-fuel-label').hidden = !volume;
  byId('estimate-fuel').disabled = !volume || !catalog;
  byId('vehicles').querySelectorAll('input, select').forEach(input => input.disabled = volume);
  updatePriceDates();
}
async function estimate(event) {
  event.preventDefault();
  if (!loaded) return;
  const id = ++estimateSerial, selection = loaded;
  byId('calculate').disabled = true;
  byId('estimate-result').replaceChildren();
  byId('estimate-error').textContent = '';
  const fleet = byId('calc-mode').value === 'fleet';
  const body = { sourceId: catalog.metadata.source, regionId: loaded.a, priceDate: byId('price-date').value };
  if (loaded.b) body.comparisonRegionId = loaded.b;
  if (!fleet) { body.fuelCode = loaded.fuel; body.volumeLiters = Number(byId('volume').value); }
  else body.vehicles = [...byId('vehicles').children].map(row => ({
    templateId: Number(row.querySelector('[name="templateId"]').value),
    fuelCode: row.querySelector('[name="fuelCode"]').value,
    remainingLiters: Number(row.querySelector('[name="remainingLiters"]').value),
    count: Number(row.querySelector('[name="count"]').value)
  }));
  try {
    const result = await postJson(fleet ? '/refueling/fleet-estimate' : '/refueling/estimate', body);
    if (id !== estimateSerial || selection !== loaded) return;
    const box = element('div', undefined, 'result');
    const volume = element('div');
    volume.append(element('span', 'Нужно топлива'), element('strong', money(result.totalLiters) + ' л'));
    const cost = element('div');
    cost.append(element('span', regionName(result.regionId)), element('strong', money(result.estimatedCost) + ' ₽'),
      element('p', 'На ' + dateText(result.priceDate)));
    const budgets = fleet ? result.fuelBudgets : [result];
    budgets.forEach(budget => {
      const name = catalog.fuels.find(fuel => fuel.code === budget.fuelCode)?.name || budget.fuelCode;
      cost.append(element('p', name + ': ' + money(budget.totalLiters) + ' л × ' + money(budget.pricePerLiter) + ' ₽/л = ' + money(budget.estimatedCost) + ' ₽'));
    });
    box.append(volume, cost);
    const comparison = fleet ? (result.comparisonRegionId ? { regionId: result.comparisonRegionId,
      estimatedCost: result.comparisonEstimatedCost, difference: result.difference } : null) : result.comparison;
    if (comparison) {
      const other = element('div');
      other.append(element('span', regionName(comparison.regionId)), element('strong', money(comparison.estimatedCost) + ' ₽'),
        element('p', 'Разница бюджетов: ' + money(comparison.difference) + ' ₽'));
      box.append(other);
    }
    byId('estimate-result').replaceChildren(box);
  } catch (error) {
    if (id === estimateSerial) byId('estimate-error').textContent = error.message;
  } finally {
    if (id === estimateSerial) byId('calculate').disabled = !loaded || !byId('price-date').value || byId('price-date').disabled;
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
  const links = [...sidebar.querySelectorAll('.nav-link[href^="#"]')];
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
byId('all-period').onclick = () => { availablePeriod(true); load(); };
byId('source-select').onchange = async () => {
  const previousSource = catalog?.metadata.source;
  byId('estimate-fuel').disabled = true;
  [...byId('filters').elements].forEach(control => control.disabled = true);
  byId('add-vehicle').disabled = true;
  serial++;
  active?.abort();
  resetResults();
  try {
    await loadCatalog(byId('source-select').value);
    [...byId('filters').elements].forEach(control => control.disabled = false);
    byId('add-vehicle').disabled = !vehicleTemplates.length || byId('vehicles').children.length >= 100;
    await load();
  } catch (error) {
    if (previousSource) byId('source-select').value = previousSource;
    byId('estimate-fuel').disabled = !catalog || byId('calc-mode').value !== 'volume';
    status('Не удалось открыть набор: ' + error.message, true);
    [...byId('filters').elements].forEach(control => control.disabled = false);
    byId('add-vehicle').disabled = !vehicleTemplates.length || byId('vehicles').children.length >= 100;
    byId('show').disabled = !catalog;
  }
};
byId('region').onchange = () => { updateFuels(); availablePeriod(); load(); };
['fuel', 'estimate-fuel'].forEach(id => byId(id).onchange = event => {
  byId('fuel').value = event.target.value;
  byId('estimate-fuel').value = byId('fuel').value;
  updateRegionsB();
  availablePeriod();
  load();
});
byId('region-b').onchange = () => load();
['from', 'to'].forEach(id => byId(id).onchange = invalidate);
byId('calc-mode').onchange = calcMode;
byId('price-date').onchange = clearEstimate;
byId('volume').oninput = clearEstimate;
byId('add-vehicle').onclick = () => addVehicle();
document.addEventListener('vehicle-template-created', async event => {
  try {
    await loadVehicleTemplates();
    if (!byId('vehicles').children.length) addVehicle(event.detail.id);
    else clearEstimate();
  } catch (error) { byId('template-note').textContent = error.message; }
});
document.addEventListener('vehicle-templates-changed', event => applyVehicleTemplates(event.detail));
byId('estimate-form').addEventListener('submit', estimate);
setupNavigation();

async function init() {
  [...byId('filters').elements].forEach(control => control.disabled = true);
  byId('add-vehicle').disabled = true;
  try {
    await loadCatalog('ROSSTAT_PFO_WEEKLY');
    try { await loadVehicleTemplates(); }
    catch (error) { byId('template-note').textContent = error.message; }
    [...byId('filters').elements].forEach(control => control.disabled = false);
    byId('add-vehicle').disabled = !vehicleTemplates.length;
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

