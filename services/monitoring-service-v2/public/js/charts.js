const Charts = (() => {
  function sparkline(data, width = 200, height = 40) {
    if (!data || data.length === 0) return '';
    const max = Math.max(...data, 1);
    const min = Math.min(...data, 0);
    const range = max - min || 1;
    const step = width / Math.max(data.length - 1, 1);

    const points = data.map((val, i) => {
      const x = i * step;
      const y = height - ((val - min) / range) * height;
      return `${x},${y}`;
    }).join(' ');

    return `<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" xmlns="http://www.w3.org/2000/svg">
      <polyline fill="none" stroke="var(--accent)" stroke-width="1.5" points="${points}"/>
    </svg>`;
  }

  function uptimeBar(pct) {
    const color = pct >= 99 ? 'var(--success)' : pct >= 95 ? 'var(--warning)' : 'var(--danger)';
    return `<div style="background:var(--border);border-radius:4px;height:6px;width:100%;margin-top:4px;">
      <div style="background:${color};border-radius:4px;height:100%;width:${Math.min(pct, 100)}%;"></div>
    </div>`;
  }

  return { sparkline, uptimeBar };
})();
