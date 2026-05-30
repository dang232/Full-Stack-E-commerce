const Playground = (() => {
  let endpoints = [];
  let selectedEndpoint = null;
  let history = JSON.parse(localStorage.getItem('playground_history') || '[]');

  async function init() {
    await fetchEndpoints();
    render();
  }

  async function fetchEndpoints() {
    try {
      const res = await fetch('/monitoring/endpoints', {
        headers: { Authorization: `Bearer ${Auth.getToken()}` },
      });
      if (res.ok) endpoints = await res.json();
    } catch (e) { console.error('Failed to fetch endpoints:', e); }
  }

  function render() {
    const container = document.getElementById('view-playground');
    container.innerHTML = `<div class="playground-layout">
      <div class="endpoint-list">${renderSidebar()}</div>
      <div class="request-panel">${renderRequestPanel()}</div>
    </div>`;

    bindEvents(container);
  }

  function renderSidebar() {
    if (endpoints.length === 0) return '<p style="color:var(--text-muted)">No endpoints discovered</p>';

    return endpoints.map((group) => `
      <div class="endpoint-group">
        <h4 style="color:var(--accent);margin:0.5rem 0;font-size:0.8rem;">${group.service.name}</h4>
        ${group.endpoints.map((ep) => `
          <div class="endpoint-item" data-id="${ep.id}">
            <span class="method-badge ${ep.method.toLowerCase()}">${ep.method}</span>
            <span>${ep.path}</span>
          </div>
        `).join('')}
      </div>
    `).join('');
  }

  function renderRequestPanel() {
    if (!selectedEndpoint) {
      return '<p style="color:var(--text-muted)">Select an endpoint from the sidebar</p>';
    }

    const ep = findEndpoint(selectedEndpoint);
    const schemaHint = ep?.schema ? JSON.stringify(ep.schema, null, 2) : '{}';

    return `
      <h3><span class="method-badge ${ep.method.toLowerCase()}">${ep.method}</span> ${ep.path}</h3>
      ${ep.summary ? `<p style="color:var(--text-muted);margin:0.5rem 0;">${ep.summary}</p>` : ''}
      <label style="display:block;margin-top:1rem;font-size:0.85rem;color:var(--text-muted);">Request Body (JSON)</label>
      <textarea id="req-body" placeholder='${schemaHint}'></textarea>
      <label style="display:block;margin-top:0.5rem;font-size:0.85rem;color:var(--text-muted);">Query Params (key=value, one per line)</label>
      <textarea id="req-params" style="min-height:60px;" placeholder="page=1&#10;limit=10"></textarea>
      <button class="btn-send" id="btn-send">Send Request</button>
      <div id="response-area"></div>
      ${renderHistory()}
    `;
  }

  function renderHistory() {
    if (history.length === 0) return '';
    return `<h4 style="margin-top:1.5rem;color:var(--text-muted);">Recent Requests</h4>
      <div>${history.slice(0, 10).map((h, i) => `
        <div class="endpoint-item" data-history="${i}">
          <span class="method-badge ${h.method.toLowerCase()}">${h.method}</span>
          <span>${h.path}</span>
          <span style="margin-left:auto;color:var(--text-muted);font-size:0.75rem;">${h.status}</span>
        </div>
      `).join('')}</div>`;
  }

  function findEndpoint(id) {
    for (const group of endpoints) {
      const ep = group.endpoints.find((e) => e.id === id);
      if (ep) return ep;
    }
    return null;
  }

  function parseQueryParams(text) {
    const params = {};
    text.split('\n').filter(Boolean).forEach((line) => {
      const [key, ...rest] = line.split('=');
      if (key) params[key.trim()] = rest.join('=').trim();
    });
    return params;
  }

  async function sendRequest() {
    const ep = findEndpoint(selectedEndpoint);
    if (!ep) return;

    const bodyText = document.getElementById('req-body').value.trim();
    const paramsText = document.getElementById('req-params').value.trim();

    let body = undefined;
    if (bodyText) {
      try { body = JSON.parse(bodyText); }
      catch { alert('Invalid JSON in request body'); return; }
    }

    const queryParams = paramsText ? parseQueryParams(paramsText) : undefined;

    const responseArea = document.getElementById('response-area');
    responseArea.innerHTML = '<p style="color:var(--text-muted)">Sending...</p>';

    try {
      const res = await fetch(`/monitoring/endpoints/${encodeURIComponent(ep.id)}/test`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${Auth.getToken()}`,
        },
        body: JSON.stringify({ method: ep.method, path: ep.path, body, queryParams }),
      });

      const data = await res.json();
      responseArea.innerHTML = `<div class="response-panel">
        <p><strong>${data.status}</strong> ${data.statusText} · ${data.timeMs}ms</p>
        <pre>${JSON.stringify(data.body, null, 2)}</pre>
      </div>`;

      // Save to history
      history.unshift({ method: ep.method, path: ep.path, status: data.status, time: Date.now() });
      history = history.slice(0, 20);
      localStorage.setItem('playground_history', JSON.stringify(history));
    } catch (e) {
      responseArea.innerHTML = `<div class="response-panel"><p style="color:var(--danger)">Error: ${e.message}</p></div>`;
    }
  }

  function bindEvents(container) {
    container.querySelectorAll('.endpoint-item[data-id]').forEach((el) => {
      el.addEventListener('click', () => {
        selectedEndpoint = el.dataset.id;
        render();
      });
    });

    const sendBtn = container.querySelector('#btn-send');
    if (sendBtn) sendBtn.addEventListener('click', sendRequest);
  }

  return { init };
})();
