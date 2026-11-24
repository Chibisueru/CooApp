package app.coospace.client.web

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

private fun Color.hex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

/** Form buttons that get the filled M3 pill. Icon / toolbar buttons are left alone. */
private const val ACTION_BUTTONS =
    "input[type=submit], input[type=button], input[type=reset], button[type=submit]"

/**
 * Base stylesheet mapping the Material 3 scheme onto CooSpace. Selectors stay generic so
 * they survive markup changes. Dark-mode recolouring of the site's own backgrounds is done
 * by [ENGINE_JS], which looks at each element's real colour instead of blanket rules.
 */
private fun buildPageCss(c: ColorScheme, dark: Boolean): String = """
    :root { color-scheme: ${if (dark) "dark" else "light"}; }
    html, body {
        background: ${c.surface.hex()} !important;
        color: ${c.onSurface.hex()} !important;
        font-family: "Google Sans Text", "Google Sans", Roboto, system-ui, sans-serif !important;
        -webkit-tap-highlight-color: transparent;
    }
    a, a:visited { color: ${c.primary.hex()} !important; text-decoration-thickness: 1px; }
    button { border-radius: 20px !important; }
    $ACTION_BUTTONS {
        background: ${c.primary.hex()} !important; background-image: none !important;
        color: ${c.onPrimary.hex()} !important; border: none !important;
        border-radius: 20px !important; padding: 6px 16px !important; margin: 4px !important;
        font-weight: 500 !important; box-shadow: none !important;
    }
    input[type=text], input[type=password], input[type=email], input[type=search],
    input[type=number], input[type=date], input[type=datetime-local], input[type=url], textarea, select {
        background: ${c.surfaceContainerHighest.hex()} !important; color: ${c.onSurface.hex()} !important;
        border: 1px solid ${c.outline.hex()} !important; border-radius: 12px !important;
        padding: 8px 12px !important; font-size: 16px !important;
    }
    input:focus, textarea:focus, select:focus { outline: 2px solid ${c.primary.hex()} !important; border-color: transparent !important; }
    input[type=checkbox], input[type=radio] { accent-color: ${c.primary.hex()}; }
    hr { border: none !important; border-top: 1px solid ${c.outlineVariant.hex()} !important; }
    ::selection { background: ${c.primaryContainer.hex()}; color: ${c.onPrimaryContainer.hex()}; }
""".trimIndent()

/**
 * Dark-mode recolouring engine. For every element it reads the site's computed colours and:
 *  - light neutral backgrounds -> an opaque M3 surface tone (floating/shadowed elements such as
 *    sidebars, menus and dialogs get a higher container tone so they stay solid and distinct);
 *  - light tinted backgrounds (highlights, badges) -> secondaryContainer;
 *  - dark text -> onSurface (links -> primary); light borders -> outlineVariant.
 * Original inline styles are remembered and restored when the theme changes or restyling is off.
 * A MutationObserver handles content the site adds or toggles later.
 */
private val ENGINE_JS = """
(function(){
  if (window.__m3) return;
  var M = { cfg: null, queue: [], scheduled: false };
  var SKIP = {IMG:1,VIDEO:1,CANVAS:1,IFRAME:1,PICTURE:1,SCRIPT:1,STYLE:1,LINK:1,META:1,HEAD:1,TITLE:1,NOSCRIPT:1,BR:1,OPTION:1};
  var OWN = 'input, textarea, select, $ACTION_BUTTONS';
  function parse(s){ var m = s && s.match(/rgba?\(([^)]+)\)/); if(!m) return null;
    var p = m[1].split(/[\s,\/]+/).filter(Boolean).map(parseFloat);
    return {r:p[0], g:p[1], b:p[2], a: p.length > 3 ? p[3] : 1}; }
  function lum(c){ function f(v){ v/=255; return v <= 0.03928 ? v/12.92 : Math.pow((v+0.055)/1.055, 2.4); }
    return 0.2126*f(c.r) + 0.7152*f(c.g) + 0.0722*f(c.b); }
  // Keep the site's transparency: a 50% white layer becomes a 50% M3 tone, not a solid one.
  function withAlpha(hex, a){ if (a >= 0.95) return hex;
    var n = parseInt(hex.slice(1), 16);
    return 'rgba(' + (n >> 16 & 255) + ',' + (n >> 8 & 255) + ',' + (n & 255) + ',' + a + ')'; }
  function sat(c){ var mx = Math.max(c.r,c.g,c.b), mn = Math.min(c.r,c.g,c.b); return mx === 0 ? 0 : (mx-mn)/mx; }
  function set(el, p, v){ var o = el.__m3 || (el.__m3 = {});
    if (!(p in o)) o[p] = { v: el.style.getPropertyValue(p), p: el.style.getPropertyPriority(p) };
    el.style.setProperty(p, v, 'important'); }
  function clearEl(el){ var o = el.__m3; if (!o) return; el.__m3 = null;
    for (var p in o) { if (o[p].v) el.style.setProperty(p, o[p].v, o[p].p); else el.style.removeProperty(p); } }
  function skip(el){ return el.nodeType !== 1 || SKIP[el.tagName] || el.namespaceURI === 'http://www.w3.org/2000/svg'; }
  function plan(el, c){
    var cs = getComputedStyle(el), ops = [], onBg = null;
    var bg = parse(cs.backgroundColor), hasGrad = cs.backgroundImage.indexOf('gradient') >= 0;
    var raised = cs.position === 'fixed' || cs.position === 'sticky' || cs.position === 'absolute' || cs.boxShadow !== 'none';
    if (bg && bg.a > 0.1) {
      var L = lum(bg);
      if (L > 0.4) {
        var col;
        if (sat(bg) > 0.2 && L < 0.9) { col = c.secondaryContainer; onBg = c.onSecondaryContainer; }
        else if (raised) col = c.containerHigh;
        else if (L > 0.96) col = c.container;
        else col = c.containerLow;
        ops.push(['background-color', withAlpha(col, bg.a)]);
      }
      // Dark or semi-transparent dark layers (e.g. the dimming scrim behind the sidebar) are left as they are.
    } else if (hasGrad) {
      // Gradient-only backgrounds lose their gradient below, so give them a solid tone instead.
      ops.push(['background-color', raised ? c.containerHigh : c.container]);
    }
    if (hasGrad) ops.push(['background-image', 'none']);
    var fg = parse(cs.color);
    if (fg && lum(fg) < 0.3) {
      var link = el.tagName === 'A' || (el.closest && el.closest('a'));
      ops.push(['color', link ? c.primary : (onBg || c.onSurface)]);
    } else if (onBg) ops.push(['color', onBg]);
    var bw = parseFloat(cs.borderTopWidth) + parseFloat(cs.borderRightWidth) + parseFloat(cs.borderBottomWidth) + parseFloat(cs.borderLeftWidth);
    if (bw > 0) { var bc = parse(cs.borderTopColor); if (bc && lum(bc) > 0.4) ops.push(['border-color', c.outlineVariant]); }
    return ops;
  }
  function processTree(root){
    if (!M.cfg || !M.cfg.dark || !root.isConnected || skip(root)) return;
    var els = [root], all = root.getElementsByTagName('*');
    for (var i = 0; i < all.length; i++) if (!skip(all[i])) els.push(all[i]);
    els.forEach(clearEl);                         // write: restore originals
    var plans = els.map(function(el){             // read: one style pass
      return (el.matches && el.matches(OWN)) ? [] : plan(el, M.cfg); });
    els.forEach(function(el, i){                  // write: apply
      plans[i].forEach(function(op){ set(el, op[0], op[1]); }); });
  }
  function flush(){ M.scheduled = false; var q = M.queue; M.queue = [];
    q.filter(function(n){ return !q.some(function(o){ return o !== n && o.contains(n); }); }).forEach(processTree); }
  function enqueue(n){ M.queue.push(n); if (!M.scheduled) { M.scheduled = true; requestAnimationFrame(flush); } }
  var obs = new MutationObserver(function(muts){
    muts.forEach(function(m){
      if (m.type === 'childList') m.addedNodes.forEach(function(n){ if (n.nodeType === 1) enqueue(n); });
      else enqueue(m.target);
    });
  });
  function clearAll(){ var all = document.getElementsByTagName('*'); for (var i = 0; i < all.length; i++) clearEl(all[i]); }
  function styleTag(){ var s = document.getElementById('coospace-m3');
    if (!s) { s = document.createElement('style'); s.id = 'coospace-m3'; (document.head || document.documentElement).appendChild(s); }
    return s; }
  M.apply = function(cfg){
    if (M.cfg && M.cfg.key === cfg.key && document.getElementById('coospace-m3')) return;
    obs.disconnect(); clearAll(); M.cfg = cfg; styleTag().textContent = cfg.css;
    if (cfg.dark) {
      obs.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
      processTree(document.documentElement);
    }
  };
  M.remove = function(){ obs.disconnect(); clearAll(); M.cfg = null;
    var s = document.getElementById('coospace-m3'); if (s) s.remove(); };
  window.__m3 = M;
})();
""".trimIndent()

/** Script that applies the Material styling for [c] to the current page, or removes it when [enabled] is false. */
fun pageStyleJs(c: ColorScheme, dark: Boolean, enabled: Boolean): String {
    if (!enabled) return "window.__m3 && window.__m3.remove();"
    val css = buildPageCss(c, dark)
    val cfg = JSONObject()
        .put("dark", dark)
        .put("css", css)
        .put("key", css.hashCode().toString())
        .put("surface", c.surface.hex())
        .put("onSurface", c.onSurface.hex())
        .put("primary", c.primary.hex())
        .put("containerLow", c.surfaceContainerLow.hex())
        .put("container", c.surfaceContainer.hex())
        .put("containerHigh", c.surfaceContainerHigh.hex())
        .put("secondaryContainer", c.secondaryContainer.hex())
        .put("onSecondaryContainer", c.onSecondaryContainer.hex())
        .put("outlineVariant", c.outlineVariant.hex())
    return "$ENGINE_JS\nwindow.__m3.apply($cfg);"
}
