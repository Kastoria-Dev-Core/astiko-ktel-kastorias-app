(function () {
  var root = document.documentElement;
  try { var t = localStorage.getItem('doc-theme'); if (t) root.setAttribute('data-theme', t); } catch (e) {}
  window.toggleTheme = function () {
    var dark = root.getAttribute('data-theme') === 'dark' ||
      (!root.getAttribute('data-theme') && window.matchMedia('(prefers-color-scheme: dark)').matches);
    var next = dark ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    try { localStorage.setItem('doc-theme', next); } catch (e) {}
  };
  document.addEventListener('DOMContentLoaded', function () {
    buildTree();
    var nt = document.getElementById('nav-toggle');
    if (nt) nt.onclick = function () { document.body.classList.toggle('nav-open'); };
    setupSearch();
  });

  function here() {
    var p = location.pathname.split('/').pop() || 'index.html';
    return p;
  }

  function buildTree() {
    var nav = document.getElementById('side-nav');
    if (!nav || !window.NAVTREE) return;
    var current = here();
    var opened = {};
    try { opened = JSON.parse(sessionStorage.getItem('doc-nav') || '{}'); } catch (e) {}
    function contains(node) {
      if (node[1] === current) return true;
      return (node[2] || []).some(contains);
    }
    function make(node, depth, key) {
      var li = document.createElement('li');
      var row = document.createElement('div');
      var kids = node[2] || [];
      var arrow = document.createElement('span');
      arrow.className = 'arrow';
      row.appendChild(arrow);
      var a = document.createElement(node[1] ? 'a' : 'span');
      if (node[1]) a.href = node[1];
      a.textContent = node[0];
      a.title = node[0];
      row.appendChild(a);
      li.appendChild(row);
      if (node[1] === current) row.className = 'selected';
      if (kids.length) {
        var ul = document.createElement('ul');
        var open = opened[key] !== undefined ? opened[key] : (depth < 1 || contains(node));
        if (contains(node)) open = true;
        li.className = open ? '' : 'closed';
        arrow.textContent = open ? '▼' : '▶';
        arrow.onclick = function () {
          var closed = li.classList.toggle('closed');
          arrow.textContent = closed ? '▶' : '▼';
          opened[key] = !closed;
          try { sessionStorage.setItem('doc-nav', JSON.stringify(opened)); } catch (e) {}
        };
        kids.forEach(function (k, i) { ul.appendChild(make(k, depth + 1, key + '/' + i)); });
        li.appendChild(ul);
      }
      return li;
    }
    var ul = document.createElement('ul');
    window.NAVTREE.forEach(function (n, i) { ul.appendChild(make(n, 0, '' + i)); });
    nav.appendChild(ul);
    var sel = nav.querySelector('.selected');
    // Scroll the tree only, not the page
    if (sel) nav.scrollTop = sel.offsetTop - nav.clientHeight / 2;
  }

  function fold(s) {
    return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  function setupSearch() {
    var box = document.getElementById('searchbox');
    var res = document.getElementById('searchresults');
    if (!box || !window.SEARCHDATA) return;
    var data = window.SEARCHDATA.map(function (d) { return { d: d, f: fold(d[0]) }; });
    var sel = -1;
    function show() {
      var q = fold(box.value.trim());
      res.innerHTML = '';
      sel = -1;
      if (!q) { res.style.display = 'none'; return; }
      var hits = data.filter(function (x) { return x.f.indexOf(q) >= 0; });
      hits.sort(function (a, b) {
        var ra = a.f === q ? 0 : a.f.indexOf(q) === 0 ? 1 : 2;
        var rb = b.f === q ? 0 : b.f.indexOf(q) === 0 ? 1 : 2;
        return ra - rb || a.f.length - b.f.length || (a.f < b.f ? -1 : 1);
      });
      hits.slice(0, 60).forEach(function (x) {
        var a = document.createElement('a');
        a.href = x.d[3];
        a.textContent = x.d[0];
        var s = document.createElement('span');
        s.className = 'sk';
        s.textContent = x.d[1] + (x.d[2] ? ' · ' + x.d[2] : '');
        a.appendChild(s);
        res.appendChild(a);
      });
      if (!hits.length) {
        var n = document.createElement('div');
        n.className = 'none';
        n.textContent = 'No matches';
        res.appendChild(n);
      }
      res.style.display = 'block';
    }
    box.addEventListener('input', show);
    box.addEventListener('focus', show);
    box.addEventListener('keydown', function (e) {
      var links = res.querySelectorAll('a');
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!links.length) return;
        if (sel >= 0) links[sel].classList.remove('sel');
        sel = (sel + (e.key === 'ArrowDown' ? 1 : -1) + links.length) % links.length;
        links[sel].classList.add('sel');
        links[sel].scrollIntoView({ block: 'nearest' });
      } else if (e.key === 'Enter') {
        var a = links[sel >= 0 ? sel : 0];
        if (a) location.href = a.href;
      } else if (e.key === 'Escape') {
        res.style.display = 'none';
        box.blur();
      }
    });
    document.addEventListener('click', function (e) {
      if (e.target !== box && !res.contains(e.target)) res.style.display = 'none';
    });
  }
})();
