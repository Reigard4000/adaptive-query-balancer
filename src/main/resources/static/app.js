const STRATEGY_CLASS = {
    EXACT: 'exact',
    UNIFORM: 'uniform',
    STRATIFIED: 'stratified'
};

const PHASE_CLASS = {
    LIGHT_TAIL: 'light',
    WARNING: 'warning',
    HEAVY_TAIL: 'heavy',
    UNKNOWN: 'unknown'
};

const PHASE_LABEL = {
    LIGHT_TAIL: 'ЛЁГКИЙ ХВОСТ',
    WARNING: 'ПРЕДУПРЕЖДЕНИЕ',
    HEAVY_TAIL: 'ТЯЖЁЛЫЙ ХВОСТ',
    UNKNOWN: 'НЕТ ДАННЫХ'
};

const PHASE_TEXT_CLASS = {
    LIGHT_TAIL: 'text-green',
    WARNING: 'text-yellow',
    HEAVY_TAIL: 'text-red',
    UNKNOWN: 'text-muted'
};

const LOAD_LABEL = {
    NORMAL: 'НОРМА',
    WARNING: 'ВНИМАНИЕ',
    OVERLOAD: 'ПЕРЕГРУЗКА'
};

const LOAD_TAG_CLASS = {
    NORMAL: 'tag-ok',
    WARNING: 'tag-warning',
    OVERLOAD: 'tag-fail'
};

const REQUEST_STATUS_LABEL = {
    OK: 'OK',
    SHED: 'Отсечён',
    FAILED: 'Ошибка'
};

const REQUEST_STATUS_CLASS = {
    OK: 'tag-ok',
    SHED: 'tag-warning',
    FAILED: 'tag-fail'
};

const COLORS = {
    grid: '#2b313b',
    text: '#9aa4b2',
    accent: '#4f8cff',
    green: '#3fb950',
    yellow: '#d29922',
    red: '#f85149',
    purple: '#a371f7'
};

const METRIC_IDS = [
    'm-offered', 'm-accepted', 'm-completed', 'm-success', 'm-failed', 'm-shed',
    'm-inflight', 'm-rps', 'm-active-servers', 'm-capacity', 'm-utilization',
    'm-average', 'm-p50', 'm-p95', 'm-p99', 'm-min', 'm-max', 'm-window', 'm-elapsed',
    'm-alpha-est', 'm-alpha-smooth', 'm-phase', 'm-ews', 'm-cusum'
];

const STREAM_HISTORY_LIMIT = 120;
const CHART_MARGIN = {left: 48, right: 12, top: 12, bottom: 28};
const AXIS_TICKS = 4;

const state = {
    page: 'monitor',
    resultTab: 'overview',
    source: null,
    pendingSnapshot: null,
    renderScheduled: false,
    lastSnapshot: null,
    streamHistory: [],
    rateSample: null,
    servers: [],
    editingServerId: null
};

const item = (id) => document.getElementById(id);

document.querySelectorAll('#page-tabs .tab').forEach((tab) => {
    tab.addEventListener('click', () => setPage(tab.dataset.page));
});

document.querySelectorAll('#result-tabs .tab').forEach((tab) => {
    tab.addEventListener('click', () => setResultTab(tab.dataset.result));
});

item('reset-monitor').addEventListener('click', resetMetrics);
item('add-server').addEventListener('click', () => openServerForm(null));
item('refresh-servers').addEventListener('click', () => loadServers());
item('discover-servers').addEventListener('click', discoverServers);
item('server-cancel').addEventListener('click', closeServerForm);
item('server-form').addEventListener('submit', saveServer);

item('servers-table').addEventListener('click', (event) => {
    const button = event.target.closest('button[data-action]');
    if (!button) {
        return;
    }
    const action = button.dataset.action;
    const id = button.dataset.id;

    if (action === 'edit') {
        const server = state.servers.find((candidate) => candidate.id === id);
        if (server) {
            openServerForm(server);
        }
    } else if (action === 'check') {
        checkServer(id);
    } else if (action === 'delete') {
        deleteServer(id);
    }
});

window.addEventListener('beforeunload', () => {
    if (state.source) {
        state.source.close();
    }
});

let resizeTimer = null;
window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(redrawCurrentChart, 100);
});

clearMonitor();
connectMonitor();

function connectMonitor() {
    if (state.source) {
        state.source.close();
    }
    setStatus('Подключение к монитору…', 'loading');
    const source = new EventSource('/api/monitor/stream?intervalMs=1000');
    state.source = source;

    source.onopen = () => setStatus('Мониторинг активен: данные обновляются в реальном времени.', 'streaming');
    source.addEventListener('snapshot', (event) => {
        let snapshot;
        try {
            snapshot = JSON.parse(event.data);
        } catch (error) {
            return;
        }
        scheduleSnapshotRender(snapshot);
    });
    source.onerror = () => {
        setStatus('Соединение с монитором потеряно; браузер переподключается…', 'error');
    };
}

function scheduleSnapshotRender(snapshot) {
    state.pendingSnapshot = snapshot;
    if (state.renderScheduled) {
        return;
    }
    state.renderScheduled = true;
    setTimeout(() => {
        state.renderScheduled = false;
        const pending = state.pendingSnapshot;
        state.pendingSnapshot = null;
        if (pending) {
            renderSnapshot(pending);
        }
    }, 120);
}

function renderSnapshot(snapshot) {
    const rps = computeRps(snapshot);
    renderMetrics(snapshot, rps);
    renderLoadLevel(snapshot);

    const completed = Number(snapshot.successCount || 0) + Number(snapshot.failedCount || 0);
    renderDistribution(item('distribution-strategies'), snapshot.strategyDistribution, completed, STRATEGY_CLASS, {});
    renderDistribution(item('distribution-phases'), snapshot.phaseDistribution, completed, PHASE_CLASS, PHASE_LABEL);
    renderDistribution(item('distribution-query-types'), snapshot.queryTypeDistribution, completed, {}, {});

    const serverNames = Object.fromEntries((snapshot.servers || []).map((server) => [server.id, server.name]));
    renderMonitoredServers(snapshot.servers);
    renderRecentRequests(snapshot.recentRequests, serverNames);

    setText('chart-meta', `снимок ${formatNumber(snapshot.snapshot)} · окно ${formatNumber(snapshot.windowSize)}`);
    state.lastSnapshot = snapshot;
    appendStreamHistory(snapshot);
    drawStreamChart();
}

function computeRps(snapshot) {
    const sample = {
        elapsedMs: Number(snapshot.elapsedMs) || 0,
        offered: Number(snapshot.offeredCount) || 0
    };
    const previous = state.rateSample;
    state.rateSample = sample;
    if (!previous) {
        return sample.elapsedMs > 0 ? sample.offered / (sample.elapsedMs / 1000) : 0;
    }
    const seconds = (sample.elapsedMs - previous.elapsedMs) / 1000;
    if (seconds <= 0) {
        return 0;
    }
    return Math.max(0, (sample.offered - previous.offered) / seconds);
}

function renderMetrics(snapshot, rps) {
    setText('m-offered', formatNumber(snapshot.offeredCount));
    setText('m-accepted', formatNumber(snapshot.acceptedCount));
    setText('m-completed', formatNumber(snapshot.completedCount));
    setText('m-success', formatNumber(snapshot.successCount));
    setText('m-failed', formatNumber(snapshot.failedCount));
    setText('m-shed', formatNumber(snapshot.shedCount));
    setText('m-inflight', formatNumber(snapshot.inFlight));
    setText('m-rps', `${formatNumber(rps, 1)} зап/с`);
    setText('m-active-servers', `${formatNumber(snapshot.activeServers)} из ${formatNumber(snapshot.totalServers)}`);
    setText('m-capacity', formatNumber(snapshot.totalCapacity));
    setText('m-utilization', `${formatNumber(Number(snapshot.utilization) * 100, 1)}%`);
    setText('m-average', formatLatency(snapshot.averageLatency));
    setText('m-p50', formatLatency(snapshot.latencyP50));
    setText('m-p95', formatLatency(snapshot.latencyP95));
    setText('m-p99', formatLatency(snapshot.latencyP99));
    setText('m-min', formatLatency(snapshot.minLatency));
    setText('m-max', formatLatency(snapshot.maxLatency));
    setText('m-window', formatNumber(snapshot.windowSize));
    setText('m-elapsed', formatElapsed(snapshot.elapsedMs));
    setText('m-alpha-est', formatAlpha(snapshot.estimatedAlpha));
    setText('m-alpha-smooth', formatAlpha(snapshot.smoothedAlpha));
    setPhaseText('m-phase', snapshot.phase);
    setText('m-ews', formatNumber(snapshot.ews, 3));
    setText('m-cusum', formatNumber(snapshot.cusum, 3));
}

function renderLoadLevel(snapshot) {
    const level = snapshot.loadLevel || 'NORMAL';
    const badge = item('load-level');
    badge.textContent = LOAD_LABEL[level] || level;
    badge.className = `tag ${LOAD_TAG_CLASS[level] || 'tag-unknown'}`;

    const banner = item('load-banner');
    banner.className = `load-banner ${level === 'OVERLOAD' ? 'overload' : level === 'WARNING' ? 'warning' : 'normal'}`;
    banner.textContent = snapshot.warning
        || (level === 'NORMAL'
            ? 'Кластер работает в штатном режиме.'
            : 'Нагрузка приближается к пределу кластера.');
}

function appendStreamHistory(snapshot) {
    const last = state.streamHistory[state.streamHistory.length - 1];
    if (last && last.snapshot === snapshot.snapshot) {
        return;
    }
    state.streamHistory.push({
        snapshot: snapshot.snapshot,
        elapsedMs: Number(snapshot.elapsedMs) || 0,
        average: Number(snapshot.averageLatency) || 0,
        p95: Number(snapshot.latencyP95) || 0,
        p99: Number(snapshot.latencyP99) || 0
    });
    if (state.streamHistory.length > STREAM_HISTORY_LIMIT) {
        state.streamHistory.shift();
    }
}

function clearMonitor() {
    METRIC_IDS.forEach((id) => setText(id, '—'));
    item('m-phase').className = '';
    state.lastSnapshot = null;
    state.streamHistory = [];
    state.rateSample = null;
    setText('chart-meta', '—');
    item('load-level').textContent = '—';
    item('load-level').className = 'tag tag-unknown';
    item('load-banner').textContent = 'Ожидание данных…';
    item('load-banner').className = 'load-banner';
    placeholder(item('distribution-strategies'), 4, 'Нет данных');
    placeholder(item('distribution-phases'), 4, 'Нет данных');
    placeholder(item('distribution-query-types'), 4, 'Нет данных');
    placeholder(item('monitored-servers'), 12, 'Нет данных');
    placeholder(item('recent-requests'), 10, 'Нет данных');
    drawEmptyChart(item('latency-chart'), 'Ожидание данных');
}

async function resetMetrics() {
    if (!window.confirm('Сбросить метрики и состояние адаптивного контура?')) {
        return;
    }
    try {
        const response = await fetch('/api/monitor/reset', {method: 'POST'});
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        clearMonitor();
        setStatus('Метрики сброшены.', 'success');
    } catch (error) {
        setStatus(`Ошибка сброса: ${error.message}`, 'error');
    }
}

function renderDistribution(tbody, distribution, total, classMap, labelMap) {
    const entries = distribution ? Object.entries(distribution) : [];
    if (entries.length === 0) {
        placeholder(tbody, 4, 'Нет данных');
        return;
    }
    tbody.innerHTML = entries.map(([name, count]) => {
        const percent = total > 0 ? (count * 100) / total : 0;
        const cssClass = classMap[name] || '';
        const label = labelMap[name] || name;
        const tagClass = cssClass ? `tag-${cssClass}` : '';
        const width = count > 0 ? Math.max(percent, 1) : 0;
        return `<tr>
            <td><span class="tag ${tagClass}">${escapeHtml(label)}</span></td>
            <td class="num">${formatNumber(count)}</td>
            <td class="num">${formatNumber(percent, 1)}%</td>
            <td class="bar-col"><div class="meter"><span class="${cssClass}" style="width:${Math.min(100, width)}%"></span></div></td>
        </tr>`;
    }).join('');
}

function renderMonitoredServers(servers) {
    const tbody = item('monitored-servers');
    if (!servers || servers.length === 0) {
        placeholder(tbody, 12, 'Нет данных');
        return;
    }

    tbody.innerHTML = servers.map((server) => {
        const status = serverStatus(server);
        const poolClass = server.active ? 'tag-ok' : 'tag-unknown';
        const poolLabel = server.active ? 'ACTIVE' : 'STANDBY';
        return `<tr>
            <td><div>${escapeHtml(server.name || server.id)}</div><div class="muted mono">${escapeHtml(server.id)}</div></td>
            <td><span class="tag ${poolClass}">${poolLabel}</span></td>
            <td class="num">${formatNumber(server.totalRequests)}</td>
            <td class="num">${formatNumber(server.sharePercent, 1)}%</td>
            <td class="num"><span class="tag tag-ok">${formatNumber(server.successCount)}</span> / <span class="tag tag-fail">${formatNumber(server.failureCount)}</span></td>
            <td class="num">${formatNumber(server.capacity)}</td>
            <td class="num">${formatNumber(Number(server.utilization) * 100, 1)}%</td>
            <td class="num">${formatNumber(server.activeRequests)} / ${formatNumber(server.queuedRequests)}</td>
            <td class="num">${formatNumber(server.rejectedRequests)}</td>
            <td class="num">${formatNumber(server.averageLatencyMs, 2)}</td>
            <td class="num">${formatNumber(server.lastLatencyMs, 2)}</td>
            <td><span class="tag ${status.cssClass}" title="${escapeHtml(status.title)}">${escapeHtml(status.text)}</span></td>
        </tr>`;
    }).join('');
}

function renderRecentRequests(requests, serverNames) {
    const tbody = item('recent-requests');
    if (!requests || requests.length === 0) {
        placeholder(tbody, 10, 'Нет данных');
        return;
    }

    tbody.innerHTML = requests.map((request) => {
        const strategyClass = STRATEGY_CLASS[request.strategy] || '';
        const phaseClass = PHASE_CLASS[request.phase] || '';
        const requestStatus = request.status || (request.success ? 'OK' : 'FAILED');
        const statusClass = REQUEST_STATUS_CLASS[requestStatus] || 'tag-unknown';
        const serverLabel = request.serverId ? (serverNames[request.serverId] || request.serverId) : '—';

        return `<tr>
            <td class="mono">${escapeHtml(request.id)}</td>
            <td>${escapeHtml(serverLabel)}</td>
            <td><span class="tag">${escapeHtml(request.type)}</span></td>
            <td class="num">${formatNumber(request.estimatedRows)}</td>
            <td class="num">${formatNumber(request.latencyMs, 2)}</td>
            <td class="num">${formatNumber(request.result, 4)}</td>
            <td class="num">${request.relativeError === null || request.relativeError === undefined ? '—' : formatNumber(request.relativeError * 100, 2) + '%'}</td>
            <td><span class="tag tag-${strategyClass}">${escapeHtml(request.strategy)}</span></td>
            <td><span class="tag tag-${phaseClass}">${escapeHtml(PHASE_LABEL[request.phase] || request.phase)}</span></td>
            <td><span class="tag ${statusClass}">${escapeHtml(REQUEST_STATUS_LABEL[requestStatus] || requestStatus)}</span></td>
        </tr>`;
    }).join('');
}

function placeholder(tbody, columns, message) {
    tbody.innerHTML = `<tr><td colspan="${columns}" class="muted">${message}</td></tr>`;
}

function setPage(page) {
    if (page === state.page) {
        return;
    }
    state.page = page;
    document.querySelectorAll('#page-tabs .tab').forEach((tab) => {
        tab.classList.toggle('active', tab.dataset.page === page);
    });
    item('monitor-page').classList.toggle('hidden', page !== 'monitor');
    item('servers-page').classList.toggle('hidden', page !== 'servers');

    if (page === 'servers') {
        closeServerForm();
        loadServers();
    } else {
        redrawCurrentChart();
    }
}

function setResultTab(tab) {
    state.resultTab = tab;
    document.querySelectorAll('#result-tabs .tab').forEach((element) => {
        element.classList.toggle('active', element.dataset.result === tab);
    });
    const panels = {
        overview: item('result-overview'),
        distribution: item('result-distribution'),
        servers: item('result-servers'),
        requests: item('result-requests')
    };
    Object.entries(panels).forEach(([name, panel]) => {
        panel.classList.toggle('hidden', name !== tab);
    });
    if (tab === 'overview') {
        redrawCurrentChart();
    }
}

async function loadServers() {
    setServersStatus('Загрузка…', 'loading');
    try {
        state.servers = await fetchServers();
        renderServerTable();
        setServersStatus(`Серверов: ${state.servers.length}`, '');
    } catch (error) {
        setServersStatus(`Ошибка: ${error.message}`, 'error');
    }
}

async function fetchServers() {
    const response = await fetch('/api/servers');
    if (!response.ok) {
        throw new Error(await readError(response));
    }
    return response.json();
}

function renderServerTable() {
    const tbody = item('servers-table');
    if (state.servers.length === 0) {
        placeholder(tbody, 9, 'Серверы не добавлены');
        return;
    }

    tbody.innerHTML = state.servers.map((server) => {
        const status = serverStatus(server);
        const poolClass = server.active ? 'tag-ok' : 'tag-unknown';
        const poolLabel = server.active ? 'ACTIVE' : 'STANDBY';
        return `<tr>
            <td><div>${escapeHtml(server.name)}</div><div class="muted mono">${escapeHtml(server.id)}</div></td>
            <td class="muted">${escapeHtml(server.url || '—')}</td>
            <td class="num">${formatNumber(server.capacity)}</td>
            <td class="num">${formatNumber(server.totalRequests)}</td>
            <td class="num"><span class="tag tag-ok">${formatNumber(server.successCount)}</span> / <span class="tag tag-fail">${formatNumber(server.failureCount)}</span></td>
            <td class="num">${formatNumber(Number(server.utilization) * 100, 1)}%</td>
            <td><span class="tag ${poolClass}">${poolLabel}</span></td>
            <td><span class="tag ${status.cssClass}" title="${escapeHtml(status.title)}">${escapeHtml(status.text)}</span></td>
            <td>
                <div class="row-actions">
                    <button type="button" data-action="edit" data-id="${escapeHtml(server.id)}">Изменить</button>
                    <button type="button" data-action="check" data-id="${escapeHtml(server.id)}">Проверить</button>
                    <button type="button" class="danger" data-action="delete" data-id="${escapeHtml(server.id)}">Удалить</button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function serverStatus(server) {
    if (!server.enabled) {
        return {text: 'Выключен', cssClass: 'tag-unknown', title: ''};
    }
    if (server.healthCheckDone && !server.healthy) {
        return {
            text: 'Недоступен',
            cssClass: 'tag-fail',
            title: server.lastError || 'Health-check не прошёл'
        };
    }
    if (server.lastError) {
        return {
            text: server.lastStatusCode > 0 ? `HTTP ${server.lastStatusCode}` : 'Ошибка',
            cssClass: 'tag-fail',
            title: server.lastError
        };
    }
    if (server.healthCheckDone && server.healthy) {
        return {text: 'UP', cssClass: 'tag-ok', title: 'Health-check пройден'};
    }
    if (server.lastStatusCode > 0) {
        return {text: `HTTP ${server.lastStatusCode}`, cssClass: 'tag-ok', title: ''};
    }
    return {text: 'Не проверен', cssClass: 'tag-unknown', title: ''};
}

async function discoverServers() {
    const button = item('discover-servers');
    button.disabled = true;
    setServersStatus('Поиск серверов по настроенным адресам…', 'loading');
    try {
        const response = await fetch('/api/servers/discover', {method: 'POST'});
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        const result = await response.json();
        state.servers = await fetchServers();
        renderServerTable();
        const added = result.added ? result.added.length : 0;
        const existing = result.existing ? result.existing.length : 0;
        const unavailable = result.unreachable ? result.unreachable.length : 0;
        let message = `Добавлено: ${added}, уже было: ${existing}.`;
        if (added + existing === 0) {
            message = `Серверы не найдены (проверено адресов: ${unavailable}). Запустите серверы и повторите поиск.`;
        }
        setServersStatus(message, added > 0 || existing > 0 ? 'success' : (unavailable > 0 ? 'error' : ''));
    } catch (error) {
        setServersStatus(`Ошибка поиска: ${error.message}`, 'error');
    } finally {
        button.disabled = false;
    }
}

function openServerForm(server) {
    state.editingServerId = server ? server.id : null;
    setText('server-form-title', server ? `Настройка: ${server.name}` : 'Новый сервер');
    setServerFormStatus('', '');
    item('serverUrl').value = server ? server.url : 'http://127.0.0.1:9001/execute';
    item('serverCapacity').value = server ? server.capacity : 32;
    item('serverTimeout').value = server ? server.timeoutMs : 10000;
    item('serverEnabled').checked = server ? server.enabled : true;
    item('server-form-panel').classList.remove('hidden');
    item('server-form-panel').scrollIntoView({block: 'nearest'});
    item('serverUrl').focus();
}

function closeServerForm() {
    state.editingServerId = null;
    item('server-form-panel').classList.add('hidden');
    item('server-form').reset();
    setServerFormStatus('', '');
}

async function saveServer(event) {
    event.preventDefault();
    const url = item('serverUrl').value.trim();
    if (!url) {
        setServerFormStatus('Укажите URL сервера.', 'error');
        return;
    }

    const payload = {
        name: null,
        type: 'HTTP',
        url,
        weight: 10,
        capacity: intValue('serverCapacity', 32),
        enabled: item('serverEnabled').checked,
        timeoutMs: intValue('serverTimeout', 10000)
    };

    const editing = Boolean(state.editingServerId);
    setServerFormStatus(editing ? 'Сохранение…' : 'Добавление…', 'loading');
    try {
        const response = await fetch(
            editing ? `/api/servers/${encodeURIComponent(state.editingServerId)}` : '/api/servers',
            {
                method: editing ? 'PUT' : 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(payload)
            }
        );
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        state.servers = await fetchServers();
        renderServerTable();
        closeServerForm();
        setServersStatus(editing ? 'Сервер обновлён.' : 'Сервер добавлен.', 'success');
    } catch (error) {
        setServerFormStatus(`Ошибка: ${error.message}`, 'error');
    }
}

async function checkServer(id) {
    setServersStatus('Проверка сервера…', 'loading');
    try {
        const response = await fetch(`/api/servers/${encodeURIComponent(id)}/check`, {method: 'POST'});
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        const result = await response.json();
        state.servers = await fetchServers();
        renderServerTable();
        if (result.reachable) {
            setServersStatus(`Сервер доступен: ${result.message}, ${formatNumber(result.latencyMs, 1)} мс.`, 'success');
        } else {
            setServersStatus(`Сервер недоступен: ${result.message}`, 'error');
        }
    } catch (error) {
        setServersStatus(`Ошибка проверки: ${error.message}`, 'error');
    }
}

async function deleteServer(id) {
    const server = state.servers.find((candidate) => candidate.id === id);
    const label = server ? server.name : id;
    if (!window.confirm(`Удалить ${label}?`)) {
        return;
    }
    try {
        const response = await fetch(`/api/servers/${encodeURIComponent(id)}`, {method: 'DELETE'});
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        if (state.editingServerId === id) {
            closeServerForm();
        }
        state.servers = await fetchServers();
        renderServerTable();
        setServersStatus('Сервер удалён.', 'success');
    } catch (error) {
        setServersStatus(`Ошибка удаления: ${error.message}`, 'error');
    }
}

function setServersStatus(message, kind) {
    const element = item('servers-status');
    element.textContent = message;
    element.className = `status ${kind || ''}`.trim();
}

function setServerFormStatus(message, kind) {
    const element = item('server-form-status');
    element.textContent = message;
    element.className = `status ${kind || ''}`.trim();
}

function setPhaseText(id, phase) {
    const element = item(id);
    element.textContent = PHASE_LABEL[phase] || phase || '—';
    element.className = PHASE_TEXT_CLASS[phase] || '';
}

function setStatus(message, kind) {
    const statusBox = item('status');
    statusBox.textContent = message;
    statusBox.className = `status ${kind || ''}`.trim();
}

function setText(id, value) {
    const element = item(id);
    if (element) {
        element.textContent = value;
    }
}

function numberValue(id, fallback) {
    const value = Number(item(id).value);
    return Number.isFinite(value) ? value : fallback;
}

function intValue(id, fallback) {
    const value = Math.trunc(numberValue(id, fallback));
    return Number.isFinite(value) ? value : fallback;
}

async function readError(response) {
    const body = await response.json().catch(() => ({}));
    return body.error || `HTTP ${response.status}`;
}

function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, (character) => {
        switch (character) {
            case '&':
                return '&amp;';
            case '<':
                return '&lt;';
            case '>':
                return '&gt;';
            case '"':
                return '&quot;';
            case "'":
                return '&#39;';
            default:
                return character;
        }
    });
}

function formatAlpha(value) {
    return formatNumber(value, 3);
}

function formatLatency(value) {
    return `${formatNumber(value, 2)} мс`;
}

function formatElapsed(value) {
    const milliseconds = Number(value);
    if (!Number.isFinite(milliseconds) || milliseconds < 0) {
        return '—';
    }
    if (milliseconds < 1000) {
        return `${formatNumber(milliseconds)} мс`;
    }
    const totalSeconds = milliseconds / 1000;
    if (totalSeconds < 60) {
        return `${formatNumber(totalSeconds, 1)} с`;
    }
    const totalMinutes = Math.floor(totalSeconds / 60);
    const seconds = Math.floor(totalSeconds % 60);
    if (totalMinutes < 60) {
        return `${totalMinutes} мин ${String(seconds).padStart(2, '0')} с`;
    }
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${hours} ч ${String(minutes).padStart(2, '0')} мин`;
}

function formatNumber(value, digits = 0) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return '0';
    }
    return number.toLocaleString('ru-RU', {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits
    });
}

function redrawCurrentChart() {
    if (state.resultTab !== 'overview' || state.page !== 'monitor') {
        return;
    }
    drawStreamChart();
}

function chartSurface(canvas) {
    if (!canvas || canvas.offsetParent === null) {
        return null;
    }
    const rect = canvas.getBoundingClientRect();
    const width = Math.floor(rect.width);
    const height = Math.floor(rect.height);
    if (width < 20 || height < 20) {
        return null;
    }
    const ratio = window.devicePixelRatio || 1;
    const targetWidth = Math.floor(width * ratio);
    const targetHeight = Math.floor(height * ratio);
    if (canvas.width !== targetWidth || canvas.height !== targetHeight) {
        canvas.width = targetWidth;
        canvas.height = targetHeight;
    }
    const ctx = canvas.getContext('2d');
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, width, height);
    ctx.font = '10px "JetBrains Mono", "Cascadia Mono", Consolas, monospace';
    ctx.textBaseline = 'middle';
    return {ctx, width, height};
}

function drawEmptyChart(canvas, message) {
    const surface = chartSurface(canvas);
    if (!surface) {
        return;
    }
    surface.ctx.fillStyle = COLORS.text;
    surface.ctx.textAlign = 'center';
    surface.ctx.textBaseline = 'middle';
    surface.ctx.fillText(message, surface.width / 2, surface.height / 2);
}

function drawStreamChart() {
    const canvas = item('latency-chart');
    const surface = chartSurface(canvas);
    if (!surface) {
        return;
    }
    const history = state.streamHistory;
    if (history.length === 0) {
        drawEmptyChart(canvas, 'Ожидание данных');
        return;
    }

    const {ctx} = surface;
    const plot = plotArea(surface.width, surface.height);
    const maxValue = Math.max(...history.map((point) => Math.max(point.average, point.p95, point.p99)));
    const yMax = niceMax(maxValue * 1.1);
    drawGrid(ctx, plot, yMax);

    const xFor = (index) => history.length === 1
        ? plot.left + plot.width / 2
        : plot.left + (index / (history.length - 1)) * plot.width;
    const yFor = (value) => plot.top + plot.height - (Math.min(value, yMax) / yMax) * plot.height;

    const series = [
        {key: 'average', color: COLORS.accent, label: 'avg'},
        {key: 'p95', color: COLORS.yellow, label: 'p95'},
        {key: 'p99', color: COLORS.red, label: 'p99'}
    ];

    series.forEach((line) => {
        ctx.beginPath();
        history.forEach((point, index) => {
            const x = xFor(index);
            const y = yFor(point[line.key]);
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.strokeStyle = line.color;
        ctx.lineWidth = 1.5;
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';
        ctx.stroke();

        const last = history[history.length - 1];
        ctx.beginPath();
        ctx.arc(xFor(history.length - 1), yFor(last[line.key]), 2, 0, Math.PI * 2);
        ctx.fillStyle = line.color;
        ctx.fill();
    });

    drawChartLegend(ctx, plot, series);
    drawTimeLabels(ctx, plot, history);
}

function plotArea(width, height) {
    return {
        left: CHART_MARGIN.left,
        top: CHART_MARGIN.top,
        width: Math.max(1, width - CHART_MARGIN.left - CHART_MARGIN.right),
        height: Math.max(1, height - CHART_MARGIN.top - CHART_MARGIN.bottom)
    };
}

function drawGrid(ctx, plot, yMax) {
    ctx.strokeStyle = COLORS.grid;
    ctx.lineWidth = 1;
    ctx.fillStyle = COLORS.text;
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    for (let index = 0; index <= AXIS_TICKS; index++) {
        const ratio = index / AXIS_TICKS;
        const y = plot.top + plot.height - ratio * plot.height;
        ctx.beginPath();
        ctx.moveTo(plot.left, y);
        ctx.lineTo(plot.left + plot.width, y);
        ctx.stroke();
        ctx.fillText(formatAxisValue(yMax * ratio), plot.left - 6, y);
    }
}

function drawChartLegend(ctx, plot, series) {
    if (plot.width < 220) {
        return;
    }
    ctx.font = '10px "JetBrains Mono", "Cascadia Mono", Consolas, monospace';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    const gap = 12;
    const items = series.map((line) => ({
        ...line,
        width: 20 + ctx.measureText(line.label).width
    }));
    const totalWidth = items.reduce((sum, line) => sum + line.width, 0) + gap * (items.length - 1);
    let x = plot.left + plot.width - totalWidth;
    const y = plot.top + 6;
    items.forEach((line) => {
        ctx.strokeStyle = line.color;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(x, y);
        ctx.lineTo(x + 14, y);
        ctx.stroke();
        ctx.fillStyle = COLORS.text;
        ctx.fillText(line.label, x + 18, y);
        x += line.width + gap;
    });
}

function drawTimeLabels(ctx, plot, history) {
    const first = history[0];
    const last = history[history.length - 1];
    ctx.fillStyle = COLORS.text;
    ctx.textBaseline = 'top';
    ctx.textAlign = 'left';
    ctx.fillText(formatChartSeconds(first.elapsedMs), plot.left, plot.top + plot.height + 8);
    ctx.textAlign = 'right';
    ctx.fillText(formatChartSeconds(last.elapsedMs), plot.left + plot.width, plot.top + plot.height + 8);
}

function niceMax(value) {
    if (!Number.isFinite(value) || value <= 0) {
        return 1;
    }
    const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
    const normalized = value / magnitude;
    let step = 10;
    if (normalized <= 1) {
        step = 1;
    } else if (normalized <= 2) {
        step = 2;
    } else if (normalized <= 2.5) {
        step = 2.5;
    } else if (normalized <= 5) {
        step = 5;
    }
    return step * magnitude;
}

function formatAxisValue(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return '0';
    }
    if (Math.abs(number) >= 10) {
        return formatNumber(number, 0);
    }
    if (Math.abs(number) >= 1) {
        return formatNumber(number, 1);
    }
    return formatNumber(number, 2);
}

function formatChartSeconds(value) {
    const seconds = Number(value) / 1000;
    if (!Number.isFinite(seconds) || seconds < 0) {
        return '0 с';
    }
    if (seconds < 60) {
        return `${formatNumber(seconds, 1)} с`;
    }
    const minutes = Math.floor(seconds / 60);
    const rest = Math.floor(seconds % 60);
    return `${minutes}:${String(rest).padStart(2, '0')}`;
}
