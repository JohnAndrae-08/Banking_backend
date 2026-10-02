/* Live Backend Data Flow visualizer — subscribes to SSE /api/monitor/events */
(function () {
  const $ = (id) => document.getElementById(id);
  const eventsEl = $('events'), activityBody = $('activity-body'), sqlBox = $('sql-box');
  const nodes = {};
  document.querySelectorAll('#pipeline .node').forEach((n) => { nodes[n.dataset.layer] = n; });

  let paused = false, lastReq = null, idleTimer = null;
  const seenResponses = new Set();

  const READ_OPS = new Set(['BALANCE', 'HISTORY', 'LIST_ACCOUNTS', 'ACCOUNT_DETAILS', 'TRANSACTION_LOOKUP', 'CUSTOMER', 'MONITOR']);

  function fmtTime(ts) {
    try { const d = new Date(ts); return d.toLocaleTimeString('en-GB', { hour12: false }) + '.' + String(d.getMilliseconds()).padStart(3, '0'); }
    catch { return ''; }
  }
  function setNode(layer, state, sub) {
    const n = nodes[layer]; if (!n) return;
    n.dataset.state = state;
    n.querySelector('[data-state]').textContent = state;
    if (sub !== undefined) n.querySelector('[data-sub]').textContent = sub;
  }
  function lightChainForSql() { // SQL event implies repo+sql+db work
    setNode('REPOSITORY', 'SUCCESS', 'JdbcTemplate query');
    setNode('SQL', 'RUNNING', sqlBox.textContent.slice(0, 80) || 'executing');
    setNode('DATABASE', 'RUNNING', 'engine executing');
  }
  function scheduleIdle() {
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      Object.keys(nodes).forEach((k) => setNode(k, 'IDLE', k === 'TRANSACTION' ? 'no active transaction' : 'waiting'));
      hideTxBanner();
      document.querySelectorAll('.table').forEach((t) => t.classList.remove('hot'));
    }, 6000);
  }
  function showTxBanner(kind, text) {
    const b = $('tx-banner'); b.className = 'tx-banner ' + kind; b.textContent = text; b.classList.remove('hidden');
  }
  function hideTxBanner() { $('tx-banner').classList.add('hidden'); }

  function passFilter(ev) {
    const f = $('sel-filter').value;
    if (f !== 'ALL' && ev.type !== f && ev.layer !== f) return false;
    const q = $('search').value.trim().toLowerCase();
    if (q && !((ev.endpoint || '').toLowerCase().includes(q) || (ev.requestId || '').toLowerCase().includes(q))) return false;
    return true;
  }

  function appendEvent(ev) {
    if (!passFilter(ev)) return;
    const li = document.createElement('li');
    li.className = ev.status || ev.type;
    const auth = ev.authenticated === true ? 'auth✓' : ev.authenticated === false ? 'auth✗' : '';
    li.innerHTML = '<span class="t">[' + fmtTime(ev.timestamp) + ']</span> <b>' + esc(ev.type) + '/' + esc(ev.layer) + '</b> '
      + esc(ev.message || '') + ' <span class="t">' + esc(ev.requestId || '') + ' ' + esc(ev.method || '') + ' ' + esc(ev.endpoint || '') + ' ' + auth + '</span>';
    eventsEl.prepend(li);
    while (eventsEl.children.length > 200) eventsEl.lastChild.remove();
  }

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }

  function updateInspector(ev) {
    lastReq = ev.requestId || lastReq;
    if (ev.requestId && lastReq && ev.requestId !== lastReq) { /* new request started */ }
    if (ev.requestId) $('in-req').textContent = ev.requestId;
    if (ev.method) $('in-method').textContent = ev.method;
    if (ev.endpoint) $('in-endpoint').textContent = ev.endpoint;
    if (ev.operation) $('in-op').textContent = ev.operation;
    if (ev.authenticated !== undefined && ev.authenticated !== null) $('in-auth').textContent = ev.authenticated ? 'YES' : 'NO';
    if (ev.httpStatus) $('in-status').textContent = ev.httpStatus;
    if (ev.durationMs !== undefined && ev.durationMs !== null) $('in-dur').textContent = ev.durationMs + ' ms';
    else if (ev.type === 'REQUEST') { $('in-status').textContent = '…running'; $('in-dur').textContent = '…'; }
  }

  function updateActivity(ev) {
    if (ev.type !== 'RESPONSE' || !ev.httpStatus) return;
    const key = ev.requestId + ev.httpStatus;
    if (seenResponses.has(key)) return; seenResponses.add(key);
    if (activityBody.querySelector('.muted')) activityBody.innerHTML = '';
    const tr = document.createElement('tr');
    const mode = READ_OPS.has(ev.operation) ? 'READ' : 'WRITE';
    tr.innerHTML = '<td>' + fmtTime(ev.timestamp).slice(0, 8) + '</td><td>' + esc(ev.method) + '</td><td>' + esc(ev.endpoint)
      + '</td><td>' + ev.httpStatus + '</td><td>' + (ev.durationMs || 0) + ' ms</td><td>' + mode + ' · ' + esc(ev.operation || '') + '</td>';
    activityBody.prepend(tr);
    while (activityBody.children.length > 30) activityBody.lastChild.remove();
  }

  function handle(ev) {
    if (!ev || ev.operation === 'MONITOR') { if (ev && ev.type === 'RESPONSE') return; }
    if (!paused) { appendEvent(ev); }
    updateInspector(ev); updateActivity(ev); scheduleIdle();
    switch (ev.type) {
      case 'REQUEST':
        setNode('CLIENT', 'RUNNING', (ev.method || '') + ' ' + (ev.endpoint || ''));
        setNode('AUTH', ev.authenticated ? 'SUCCESS' : 'RUNNING', ev.authenticated ? 'authenticated' : 'checking credentials');
        break;
      case 'CONTROLLER':
        setNode('CLIENT', 'SUCCESS', 'sent');
        setNode('CONTROLLER', 'RUNNING', ev.message || 'handling');
        break;
      case 'AUTH':
        setNode('AUTH', ev.status === 'SUCCESS' ? 'SUCCESS' : 'RUNNING', ev.message || '');
        if (ev.user) FlowUser = ev.user;
        break;
      case 'SERVICE':
        setNode('CONTROLLER', 'SUCCESS', 'dispatched');
        setNode('SERVICE', 'RUNNING', ev.message || 'business logic');
        if (/BEGIN/i.test(ev.message || '')) setNode('TRANSACTION', 'RUNNING', 'BEGIN');
        break;
      case 'REPOSITORY':
        setNode('SERVICE', 'SUCCESS', 'delegated');
        setNode('REPOSITORY', 'RUNNING', ev.message || 'JdbcTemplate');
        break;
      case 'SQL':
        lightChainForSql();
        setNode('SQL', 'RUNNING', (ev.sql || '').slice(0, 90));
        if (ev.sql) sqlBox.textContent = ev.sql + (ev.tables ? '\n-- tables: ' + ev.tables.join(', ') : '');
        (ev.tables || []).forEach((t) => {
          const el = document.querySelector('.table[data-table="' + t + '"]');
          if (el) el.classList.add('hot');
        });
        break;
      case 'DATABASE':
        setNode('SQL', 'SUCCESS', 'executed');
        setNode('DATABASE', ev.status === 'ROLLED_BACK' ? 'ROLLED_BACK' : 'SUCCESS', ev.message || '');
        if (ev.sql) sqlBox.textContent += '\n' + ev.sql;
        break;
      case 'TRANSACTION':
        if (ev.status === 'ROLLED_BACK') {
          setNode('TRANSACTION', 'ROLLED_BACK', ev.message || 'ROLLED BACK');
          setNode('DATABASE', 'ROLLED_BACK', 'restored');
          setNode('SERVICE', 'FAILED', 'rolled back');
          showTxBanner('rollback', 'TRANSACTION STATUS: ROLLED BACK — ' + (ev.error || ev.message || 'see event log'));
        } else if (/COMMIT/i.test(ev.message || '')) {
          setNode('TRANSACTION', 'SUCCESS', 'COMMIT');
          showTxBanner('commit', 'TRANSACTION STATUS: COMMITTED — ' + (ev.requestId || ''));
        } else {
          setNode('TRANSACTION', 'RUNNING', ev.message || 'BEGIN');
        }
        break;
      case 'ERROR':
        setNode('SERVICE', 'FAILED', ev.message || 'failed');
        setNode('TRANSACTION', /rollback/i.test(ev.message || '') ? 'ROLLED_BACK' : 'IDLE', ev.error || ev.message || '');
        break;
      case 'RESPONSE':
        if (ev.operation === 'MONITOR') break;
        ['CLIENT', 'CONTROLLER', 'SERVICE', 'REPOSITORY', 'SQL', 'DATABASE'].forEach((l) => {
          const n = nodes[l];
          if (n && n.dataset.state === 'RUNNING') setNode(l, ev.status === 'SUCCESS' ? 'SUCCESS' : 'FAILED', l === 'CLIENT' ? (ev.method + ' ' + ev.endpoint) : n.querySelector('[data-sub]').textContent);
        });
        if (nodes.TRANSACTION.dataset.state === 'RUNNING' && ev.status === 'SUCCESS') setNode('TRANSACTION', 'SUCCESS', 'COMMIT (implicit)');
        break;
    }
  }

  let FlowUser = null;

  async function refreshStatus() {
    try {
      const r = await fetch('/api/monitor/status');
      const j = await r.json();
      const be = $('chip-backend'), db = $('chip-db'), api = $('chip-api');
      be.className = 'chip ok'; $('chip-backend-text').textContent = 'BACKEND RUNNING :' + (j.backend.port || '8080');
      const conn = j.database && j.database.connected;
      db.className = 'chip ' + (conn ? 'ok' : 'bad');
      $('chip-db-text').textContent = 'DB ' + (j.database.status || '') + ' · ' + (j.database.database || '');
      api.className = 'chip ok'; $('chip-api-text').textContent = 'API AVAILABLE';
      $('db-meta').textContent = 'Engine: ' + j.database.engine + ' · Database: ' + j.database.database + ' · Connection: ' + (conn ? 'ACTIVE' : 'FAILED');
    } catch (e) {
      $('chip-backend-text').textContent = 'BACKEND UNREACHABLE';
      $('chip-backend').className = 'chip bad';
    }
  }

  function connect() {
    const es = new EventSource('/api/monitor/events');
    es.addEventListener('flow', (msg) => { try { handle(JSON.parse(msg.data)); } catch (e) { /* ignore */ } });
    es.onerror = () => { $('live-badge').textContent = 'RECONNECTING'; setTimeout(() => { try { es.close(); } catch {} connect(); }, 3000); };
    es.onopen = () => { $('live-badge').textContent = 'LIVE'; };
  }

  async function loadHistory() {
    try {
      const r = await fetch('/api/monitor/history?limit=100');
      const j = await r.json();
      (j.data || []).forEach(handle);
    } catch (e) { /* backend may still be starting */ }
  }

  // controls
  $('btn-pause').onclick = (e) => {
    paused = !paused;
    e.target.textContent = paused ? 'Resume' : 'Pause';
    $('live-badge').textContent = paused ? 'PAUSED' : 'LIVE';
    $('live-badge').classList.toggle('paused', paused);
  };
  $('btn-clear').onclick = () => { eventsEl.innerHTML = ''; fetch('/api/monitor/clear'); };
  $('btn-refresh').onclick = refreshStatus;
  $('sel-filter').onchange = () => {};
  $('search').oninput = () => {};

  // demo panel — calls the REAL existing REST API
  async function demo(kind) {
    const out = $('demo-out');
    const token = $('demo-token').value.trim();
    const auth = token ? { 'Authorization': 'Bearer ' + token } : {};
    const call = async (method, url, body) => {
      const r = await fetch(url, { method, headers: Object.assign({ 'Content-Type': 'application/json' }, auth), body: body ? JSON.stringify(body) : undefined });
      const txt = await r.text();
      let pretty = txt; try { pretty = JSON.stringify(JSON.parse(txt), null, 2); } catch {}
      out.textContent = method + ' ' + url + ' → ' + r.status + '\n' + pretty;
      return { status: r.status, body: txt };
    };
    try {
      if (kind === 'login') {
        const res = await call('POST', '/api/auth/login', { usernameOrEmail: $('demo-user').value, password: $('demo-pass').value });
        try {
          const j = JSON.parse(res.body);
          if (j.data && j.data.token) { $('demo-token').value = j.data.token; out.textContent += '\n\nToken saved to JWT field (masked in events).'; }
        } catch {}
      } else if (kind === 'deposit') {
        await call('POST', '/api/accounts/' + $('demo-acct').value + '/deposit', { amount: Number($('demo-amt').value), description: 'Demo deposit' });
      } else if (kind === 'withdraw') {
        await call('POST', '/api/accounts/' + $('demo-acct').value + '/withdraw', { amount: 200, description: 'Demo withdraw' });
      } else if (kind === 'transfer') {
        if (!$('demo-src').value || !$('demo-dst').value) { out.textContent = 'Enter source + destination account NUMBERS first (see GET /api/accounts).'; return; }
        await call('POST', '/api/transfers', { sourceAccountNumber: $('demo-src').value.trim(), destinationAccountNumber: $('demo-dst').value.trim(), amount: 100, description: 'Demo transfer' });
      } else if (kind === 'balance') {
        await call('GET', '/api/accounts/' + $('demo-acct').value + '/balance');
      }
    } catch (e) { out.textContent = 'Request failed: ' + e.message; }
  }
  document.querySelectorAll('[data-demo]').forEach((b) => { b.onclick = () => demo(b.dataset.demo); });

  refreshStatus(); setInterval(refreshStatus, 15000);
  loadHistory().then(connect);
})();
