/**
 * Server-side link preview for shared Lunara links.
 *
 * When a link (lunara.ankitak.com.np/song/<id>, /album/…, /playlist/…, /artist/…) is pasted into
 * WhatsApp, Instagram, Telegram or iMessage, those apps fetch the URL on their servers and read the
 * Open Graph meta tags. A static page with client-side JavaScript cannot do this — the scraper
 * never runs it — so this function fetches the real cover, title and artist from YouTube Music and
 * returns a page whose meta tags describe exactly that song, album, playlist or artist. It also
 * serves the same "open in the app" hand-off a browser needs.
 *
 * Everything here is best-effort: any failure falls back to a plain "Listen on Lunara" card, so a
 * link always produces a valid, branded preview rather than an error.
 */

const WEB_REMIX = { clientName: 'WEB_REMIX', clientVersion: '1.20260213.01.00' };
const SITE = 'https://lunara.ankitak.com.np';

function fetchWithTimeout(url, options = {}, ms = 4500) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ms);
  return fetch(url, { ...options, signal: controller.signal }).finally(() => clearTimeout(timer));
}

// A song is a video id, so YouTube's public oEmbed gives title, artist and a thumbnail directly.
async function songMeta(id) {
  const res = await fetchWithTimeout(
    `https://www.youtube.com/oembed?url=${encodeURIComponent('https://music.youtube.com/watch?v=' + id)}&format=json`,
  );
  if (!res.ok) return null;
  const j = await res.json();
  if (!j || !j.title) return null;
  return { title: j.title, artist: j.author_name || '', cover: j.thumbnail_url || '' };
}

// Depth-first search for the first value at a matching key, anywhere in a messy InnerTube response.
function dig(node, key) {
  if (!node || typeof node !== 'object') return undefined;
  if (Object.prototype.hasOwnProperty.call(node, key)) return node[key];
  for (const value of Object.values(node)) {
    const found = dig(value, key);
    if (found !== undefined) return found;
  }
  return undefined;
}

function firstText(node) {
  const runs = dig(node, 'runs');
  if (Array.isArray(runs)) return runs.map((r) => r.text).join('');
  const simple = dig(node, 'simpleText');
  return typeof simple === 'string' ? simple : '';
}

function biggestThumb(node) {
  const thumbs = dig(node, 'thumbnails');
  if (Array.isArray(thumbs) && thumbs.length) {
    return thumbs.reduce((a, b) => ((b.width || 0) > (a.width || 0) ? b : a)).url;
  }
  return '';
}

// Albums, playlists and artists are browse ids; ask InnerTube for the page header.
async function browseMeta(type, id) {
  const browseId = type === 'playlist' ? 'VL' + id : id;
  const res = await fetchWithTimeout('https://music.youtube.com/youtubei/v1/browse?prettyPrint=false', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0' },
    body: JSON.stringify({ context: { client: WEB_REMIX }, browseId }),
  });
  if (!res.ok) return null;
  const j = await res.json();
  const header = dig(j, 'header') || j;
  const title = firstText(header);
  const cover = biggestThumb(header);
  return title ? { title, artist: '', cover } : null;
}


function page({ type, id, title, artist, cover }) {
  const kindLabel = type.charAt(0).toUpperCase() + type.slice(1);
  const ogTitle = title || 'Listen on Lunara';
  const ogDesc = artist ? `${artist} · Open in Lunara` : `Open this ${kindLabel.toLowerCase()} in Lunara`;
  const ogImage = cover || `${SITE}/assets/app-icon.png`;
  const canonical = `${SITE}/${type}/${encodeURIComponent(id)}`;
  const intentUri =
    `intent://${type}/${id}#Intent;scheme=lunara;package=com.lunara.app` +
    `;S.browser_fallback_url=${encodeURIComponent(canonical + '?tried=1')};end`;

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>${escapeHtml(ogTitle)} · Lunara</title>
<meta name="theme-color" content="#0a0a0a" />
<meta property="og:type" content="music.song" />
<meta property="og:site_name" content="Lunara" />
<meta property="og:title" content="${escapeHtml(ogTitle)}" />
<meta property="og:description" content="${escapeHtml(ogDesc)}" />
<meta property="og:image" content="${escapeHtml(ogImage)}" />
<meta property="og:image:secure_url" content="${escapeHtml(ogImage)}" />
<meta property="og:url" content="${canonical}" />
<meta name="twitter:card" content="summary_large_image" />
<meta name="twitter:title" content="${escapeHtml(ogTitle)}" />
<meta name="twitter:description" content="${escapeHtml(ogDesc)}" />
<meta name="twitter:image" content="${escapeHtml(ogImage)}" />
<link rel="icon" type="image/png" href="/assets/app-icon.png" />
<style>
  body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0a0a0a;color:#fff;
       font-family:Inter,system-ui,sans-serif;text-align:center;padding:24px}
  .card{max-width:340px}
  img.cover{width:260px;height:260px;object-fit:cover;border-radius:20px;box-shadow:0 24px 60px rgba(0,0,0,.6)}
  .pill{display:inline-block;margin-top:22px;padding:5px 14px;border-radius:999px;background:rgba(30,215,96,.15);
        color:#1ed760;font-size:12px;font-weight:800;letter-spacing:.12em}
  h1{font-size:26px;letter-spacing:-.02em;margin:14px 0 4px}
  p.artist{color:#b3b3b3;margin:0 0 24px;font-size:16px}
  a.btn{display:inline-block;background:#1ed760;color:#000;font-weight:800;padding:13px 30px;
        border-radius:999px;text-decoration:none;font-size:15px}
  a.site{display:block;margin-top:16px;color:#7a7a7a;font-size:13px}
</style>
</head>
<body>
  <div class="card">
    <img class="cover" src="${escapeHtml(ogImage)}" alt="" />
    <span class="pill">${escapeHtml(kindLabel.toUpperCase())}</span>
    <h1>${escapeHtml(ogTitle)}</h1>
    ${artist ? `<p class="artist">${escapeHtml(artist)}</p>` : ''}
    <a class="btn" href="${escapeHtml(intentUri)}">Open in Lunara</a>
    <a class="site" href="${canonical}">lunara.ankitak.com.np</a>
  </div>
  <script>
    // Try the app immediately; if it is not installed the fallback URL brings us back here.
    var tried = /[?&]tried=1/.test(location.search);
    if (!tried) { try { window.location.href = ${JSON.stringify(intentUri)}; } catch (e) {} }
  </script>
</body>
</html>`;
}

module.exports = async function handler(req, res) {
  const { type, id } = req.query;
  const valid = ['song', 'album', 'playlist', 'artist'].includes(type) && id;
  const safeType = valid ? type : 'song';
  const safeId = valid ? id : '';

  let meta = null;
  if (valid) meta = await metaFor(safeType, safeId);

  const html = page({
    type: safeType,
    id: safeId,
    title: meta ? meta.title : '',
    artist: meta ? meta.artist : '',
    cover: meta ? meta.cover : '',
  });

  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  // Brief cache so a burst of scrapes does not hammer YouTube, but previews stay fresh.
  res.setHeader('Cache-Control', 'public, max-age=60, s-maxage=600, stale-while-revalidate=600');
  res.status(200).send(html);
};

async function metaFor(type, id) {
  try {
    if (type === 'song') return await songMeta(id);
    return await browseMeta(type, id);
  } catch (e) {
    return null;
  }
}

function escapeHtml(s) {
  return String(s || '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}
