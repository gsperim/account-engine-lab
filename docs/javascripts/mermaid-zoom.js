(function () {
  function openOverlay(svg) {
    if (document.getElementById('mz-overlay')) return;

    const overlay = document.createElement('div');
    overlay.id = 'mz-overlay';
    Object.assign(overlay.style, {
      position: 'fixed',
      inset: '0',
      zIndex: '9999',
      background: 'rgba(0,0,0,0.85)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px',
      cursor: 'zoom-out',
    });

    const clone = svg.cloneNode(true);
    Object.assign(clone.style, {
      maxWidth: '90vw',
      maxHeight: '90vh',
      width: 'auto',
      height: 'auto',
      background: '#fff',
      borderRadius: '6px',
      padding: '24px',
      boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
      cursor: 'default',
    });
    clone.removeAttribute('width');
    clone.removeAttribute('height');

    overlay.appendChild(clone);
    document.body.appendChild(overlay);

    function close(e) {
      if (e.type === 'keydown' && e.key !== 'Escape') return;
      if (e.type === 'click' && clone.contains(e.target)) return;
      overlay.remove();
      document.removeEventListener('keydown', close);
    }

    overlay.addEventListener('click', close);
    document.addEventListener('keydown', close);
  }

  function attachZoom(svg) {
    if (svg.dataset.mzInit) return;
    svg.dataset.mzInit = '1';
    svg.style.cursor = 'zoom-in';
    svg.title = 'Clique para ampliar';
    svg.addEventListener('click', function () { openOverlay(svg); });
  }

  // Observes Mermaid SVGs being added to the DOM after rendering
  const observer = new MutationObserver(function (mutations) {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node.nodeType !== 1) continue;
        if (node.nodeName === 'svg' && node.closest && node.closest('.mermaid')) {
          attachZoom(node);
        }
        if (node.querySelectorAll) {
          node.querySelectorAll('.mermaid svg').forEach(attachZoom);
        }
      }
    }
  });

  observer.observe(document.body, { childList: true, subtree: true });

  // Fallback for SVGs already present on load
  document.querySelectorAll('.mermaid svg').forEach(attachZoom);
})();
