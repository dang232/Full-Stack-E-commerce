const Health = (() => {
  let services = [];
  let selectedService = null;

  async function init() {
    await fetchServices();
    render();
  }

  async function fetchServices() {
    try {
      const res = await fetch('/monitoring/services', {
        headers: { Authorization: `Bearer ${Auth.getToken()}` },
      });
      if (res.ok) services = await res.json();
    } catch (e) { console.error('Failed to fetch services:', e); }
  }

  function updateService(data) {
    const idx = services.findIndex((s) => s.serviceId === data.serviceId);
    if (idx >= 0) {
      services[idx] = { ...services[idx], ...data, lastChecked: new Date() };
    } else {
      services.push({ ...data, name: data.serviceId, lastChecked: new Date() });
    }
    render();
  }

  function render() {
    const container = document.getElementById('view-health');
    container.innerHTML = `
      <div class="services-grid">${services.map(renderCard).join('')}</div>
      ${selectedService ? renderDetail() : ''}
    `;

    container.querySelectorAll('.service-card').forEach((card) => {
      card.addEventListener('click', () => {
        selectedService = card.dataset.id;
        render();
        loadHistory(card.dataset.id);
      });
    });
  }

  function renderCard(svc) {
    return `<div class="service-card" data-id="${svc.serviceId}">
      <div><span class="status-dot ${svc.status}"></span><span class="name">${svc.name || svc.serviceId}</span></div>
      <div class="meta">
        ${svc.responseMs != null ? `${svc.responseMs}ms` : '—'}
        &nbsp;·&nbsp;
        ${svc.uptimePct != null ? `${svc.uptimePct.toFixed(1)}% uptime` : '—'}
      </div>
      ${svc.uptimePct != null ? Charts.uptimeBar(svc.uptimePct) : ''}
    </div>`;
  }

  function renderDetail() {
    const svc = services.find((s) => s.serviceId === selectedService);
    if (!svc) return '';

    const deps = svc.dependencies
      ? Object.entries(svc.dependencies).map(([name, d]) =>
          `<li><span class="status-dot ${d.status === 'UP' ? 'up' : 'down'}"></span>${name}: ${d.status}</li>`
        ).join('')
      : '<li>No dependency info</li>';

    return `<div class="detail-panel">
      <h3>${svc.name || svc.serviceId}</h3>
      <p>Status: <strong>${svc.status}</strong> · Response: ${svc.responseMs ?? '—'}ms</p>
      <h4>Dependencies</h4>
      <ul>${deps}</ul>
      <h4>Response Time (last 24h)</h4>
      <div id="history-chart">Loading...</div>
      <button onclick="Health.closeDetail()" style="margin-top:1rem;" class="tab">Close</button>
    </div>`;
  }

  async function loadHistory(serviceId) {
    try {
      const res = await fetch(`/monitoring/services/${serviceId}/history?period=24h`, {
        headers: { Authorization: `Bearer ${Auth.getToken()}` },
      });
      if (res.ok) {
        const data = await res.json();
        const times = data.map((m) => m.responseMs ?? 0);
        const chartEl = document.getElementById('history-chart');
        if (chartEl) chartEl.innerHTML = Charts.sparkline(times, 600, 80);
      }
    } catch { /* ignore */ }
  }

  function closeDetail() {
    selectedService = null;
    render();
  }

  return { init, updateService, closeDetail };
})();
