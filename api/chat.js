// SanChat — proxy para a NVIDIA NIM (roda na Vercel Edge, plano Hobby gratis).
//
// Por que existe: a chave da NVIDIA NIM (NVIDIA_API_KEY) fica SO no servidor.
// O app nunca ve a chave; ele fala com este proxy, que repassa para a NIM.
//
// Configuracao na Vercel (Settings -> Environment Variables):
//   NVIDIA_API_KEY  = nvapi-...   (obrigatorio — pegue em build.nvidia.com)
//   ACCESS_TOKEN    = qualquer string secreta (opcional — se definido, o app
//                     precisa enviar o mesmo valor no header x-sanchat-token)
//
// Endpoint: POST /api/chat  (padrao OpenAI, stream SSE)

export const config = { runtime: 'edge' };

const NIM_URL = 'https://integrate.api.nvidia.com/v1/chat/completions';

// Allowlist: so estes modelos podem ser pedidos ao proxy.
// Catalogo 2026 da NIM — validados com chamada real em 13/09/2026.
const ALLOWED_MODELS = new Set([
  'nvidia/nemotron-3-ultra-550b-a55b',
  'deepseek-ai/deepseek-v4-flash-0731',
  'nvidia/nemotron-3.5-lightning-30b-a3b',
  'z-ai/glm-5.3-flash',
  'openai/gpt-oss-20b',
  'google/gemma-4-31b-it',
]);

const MAX_TOKENS_CAP = 2048;
const MAX_MESSAGES = 24;
const MAX_CONTENT_CHARS = 16000;

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, x-sanchat-token',
};

export default async function handler(req) {
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: CORS });
  }
  if (req.method !== 'POST') {
    return json({ error: 'Method not allowed' }, 405);
  }

  // Token opcional compartilhado app <-> proxy (anti-abuso basico)
  const access = process.env.ACCESS_TOKEN;
  if (access && req.headers.get('x-sanchat-token') !== access) {
    return json({ error: 'Unauthorized' }, 401);
  }

  const key = process.env.NVIDIA_API_KEY;
  if (!key) {
    return json(
      { error: 'Server missing NVIDIA_API_KEY (set it in Vercel > Settings > Environment Variables)' },
      500
    );
  }

  let body;
  try {
    body = await req.json();
  } catch (e) {
    return json({ error: 'Invalid JSON' }, 400);
  }

  const model = body.model;
  if (!ALLOWED_MODELS.has(model)) {
    return json({ error: 'Model not allowed: ' + model }, 400);
  }

  let messages = Array.isArray(body.messages) ? body.messages : [];
  messages = messages
    .slice(-MAX_MESSAGES)
    .filter((m) => m && typeof m.content === 'string' && m.content.trim().length > 0)
    .map((m) => ({
      role: ['system', 'user', 'assistant'].includes(m.role) ? m.role : 'user',
      content: m.content.slice(0, MAX_CONTENT_CHARS),
    }));
  if (messages.length === 0) {
    return json({ error: 'No messages' }, 400);
  }

  const maxTokens = Math.min(Number(body.max_tokens) || 1024, MAX_TOKENS_CAP);
  const temperature =
    typeof body.temperature === 'number' ? Math.min(Math.max(body.temperature, 0), 1) : 0.7;

  let upstream;
  try {
    upstream = await fetch(NIM_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify({
        model,
        messages,
        stream: true,
        max_tokens: maxTokens,
        temperature,
        top_p: 0.95,
      }),
    });
  } catch (e) {
    return json({ error: 'Failed to reach NVIDIA NIM: ' + e.message }, 502);
  }

  if (!upstream.ok) {
    const txt = await upstream.text().catch(() => '');
    return json(
      { error: `NIM error ${upstream.status}: ${txt.slice(0, 400)}` },
      upstream.status
    );
  }

  // Repassa o stream SSE direto pro app
  return new Response(upstream.body, {
    status: 200,
    headers: {
      ...CORS,
      'Content-Type': upstream.headers.get('content-type') || 'text/event-stream',
      'Cache-Control': 'no-store',
    },
  });
}

function json(obj, status) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...CORS, 'Content-Type': 'application/json' },
  });
}
