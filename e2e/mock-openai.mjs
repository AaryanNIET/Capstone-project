// Minimal OpenAI-compatible chat server for end-to-end testing. Streams like the real API:
// tool calls arrive fragmented (id + name first, then argument pieces that carry only the index),
// and usage comes in a final chunk with empty choices.
import http from 'node:http';

const PORT = Number(process.env.PORT ?? 11500);
const log = (...a) => console.log(new Date().toISOString(), ...a);

function lastUserText(messages) {
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].role === 'user') {
      const c = messages[i].content;
      return typeof c === 'string' ? c : (c ?? []).map((p) => p.text ?? '').join('');
    }
  }
  return '';
}

function plan(body) {
  const messages = body.messages ?? [];
  const last = messages[messages.length - 1];
  const tools = (body.tools ?? []).map((t) => t.function?.name);
  const user = lastUserText(messages).toLowerCase();
  const toolChoiceNone = body.tool_choice === 'none';

  if (last?.role === 'tool') {
    const data = String(last.content ?? '');
    if (data.includes('"citation":1') || data.includes('"citation": 1')) {
      return { text: 'According to the handbook, the café is open **Monday to Friday 07:30–18:00** and Saturday 09:00–15:00 [1]. '
        + 'Allergy questions go to the shift lead, then the kitchen manager Priya Raman [1]. A made-up source [9] should disappear.' };
    }
    return { text: 'I searched, but the tools returned nothing relevant.' };
  }
  if (!toolChoiceNone && tools.includes('searchDocuments') && /hours|allerg|handbook|document/.test(user)) {
    return { preamble: 'Let me look that up. ', tool: { name: 'searchDocuments', args: '{"query": "opening hours and allergy contact", "topK": 4}' } };
  }
  if (!toolChoiceNone && tools.includes('searchLiveWeb') && /news|latest|today/.test(user)) {
    return { tool: { name: 'searchLiveWeb', args: '{"query": "latest news"}' } };
  }
  const system = String(messages[0]?.content ?? '');
  const name = system.match(/User's name is ([^\n.]+)/)?.[1];
  return {
    text: name
      ? `Hello ${name}! Here is a short answer with a list:\n\n- point one\n- point two\n\n\`\`\`java\nint[] arr = {1, 2};\nSystem.out.println(arr[1]);\n\`\`\``
      : `You said: "${lastUserText(messages)}". This is a mock model.`,
  };
}

function chunk(res, model, delta, finish = null) {
  res.write(`data: ${JSON.stringify({ id: 'chatcmpl-mock', object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000),
    model, choices: [{ index: 0, delta, finish_reason: finish }] })}\n\n`);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function stream(res, body) {
  const p = plan(body);
  const model = body.model ?? 'mock';
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
  chunk(res, model, { role: 'assistant', content: '' });
  if (p.preamble) {
    chunk(res, model, { content: p.preamble });
  }
  if (p.tool) {
    const id = 'call_' + Math.random().toString(36).slice(2, 10);
    chunk(res, model, { tool_calls: [{ index: 0, id, type: 'function', function: { name: p.tool.name, arguments: '' } }] });
    for (let i = 0; i < p.tool.args.length; i += 7) {
      await sleep(15);
      chunk(res, model, { tool_calls: [{ index: 0, function: { arguments: p.tool.args.slice(i, i + 7) } }] });
    }
    chunk(res, model, {}, 'tool_calls');
  } else {
    for (const word of p.text.match(/\S+\s*|\s+/g) ?? []) {
      await sleep(25);
      chunk(res, model, { content: word });
    }
    chunk(res, model, {}, 'stop');
  }
  res.write(`data: ${JSON.stringify({ id: 'chatcmpl-mock', object: 'chat.completion.chunk', model, choices: [],
    usage: { prompt_tokens: 321, completion_tokens: 45, total_tokens: 366 } })}\n\n`);
  res.write('data: [DONE]\n\n');
  res.end();
  log('stream', p.tool ? `tool=${p.tool.name}` : 'text', 'tools offered:', (body.tools ?? []).length, 'tool_choice:', body.tool_choice ?? '-');
}

function complete(res, body) {
  const text = plan({ ...body, tools: [] }).text ?? 'Summary.';
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ id: 'chatcmpl-mock', object: 'chat.completion', model: body.model, created: Math.floor(Date.now() / 1000),
    choices: [{ index: 0, message: { role: 'assistant', content: text }, finish_reason: 'stop' }],
    usage: { prompt_tokens: 100, completion_tokens: 20, total_tokens: 120 } }));
  log('complete');
}

http.createServer((req, res) => {
  let raw = '';
  req.on('data', (d) => (raw += d));
  req.on('end', () => {
    if (req.method === 'POST' && req.url.endsWith('/chat/completions')) {
      const body = JSON.parse(raw || '{}');
      if (body.stream) stream(res, body).catch((e) => { log('error', e); res.end(); });
      else complete(res, body);
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'not found: ' + req.url } }));
    }
  });
}).listen(PORT, () => log('mock OpenAI listening on', PORT));
