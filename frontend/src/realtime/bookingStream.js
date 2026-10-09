export function createSseParser(onEvent) {
  let pending = '';
  return chunk => {
    pending += chunk;
    let match;
    while ((match = /\r?\n\r?\n/.exec(pending))) {
      const frame = pending.slice(0, match.index); pending = pending.slice(match.index + match[0].length);
      let event = 'message'; const data = [];
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
      }
      if (data.length) onEvent(event, data.join('\n'));
    }
    if (pending.length > 65536) throw new Error('Invalid event stream frame');
  };
}

// Bearer credentials stay in headers. Reconnect always receives a fresh connected invalidation.
export function connectBookingStream(url, token, onEvent, onState) {
  const stop = new AbortController();
  const delay = ms => new Promise(resolve => {
    if (stop.signal.aborted) { resolve(); return; }
    const done = () => { clearTimeout(timer); stop.signal.removeEventListener('abort', done); resolve(); };
    const timer = setTimeout(done, ms); stop.signal.addEventListener('abort', done, { once: true });
  });
  void (async () => {
    let retry = 1000;
    while (!stop.signal.aborted) {
      const attempt = new AbortController(); const abort = () => attempt.abort(); stop.signal.addEventListener('abort', abort, { once: true });
      let silence; let reader;
      try {
        onState('connecting');
        silence = setTimeout(() => attempt.abort(), 45000);
        const response = await fetch(url, { headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' }, signal: attempt.signal, cache: 'no-store' });
        if ([401, 403, 404].includes(response.status)) { onState('unavailable'); return; }
        if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('Stream unavailable');
        const parser = createSseParser((event, data) => {
          if (event === 'connected') { retry = 1000; onState('live'); }
          onEvent(event, data);
        });
        const decoder = new TextDecoder(); reader = response.body.getReader();
        while (!stop.signal.aborted) {
          clearTimeout(silence); silence = setTimeout(() => attempt.abort(), 45000);
          const result = await reader.read(); if (result.done) break;
          parser(decoder.decode(result.value, { stream: true }));
        }
      } catch (error) { if (!stop.signal.aborted) onState('reconnecting'); }
      finally { clearTimeout(silence); attempt.abort(); if (reader) await reader.cancel().catch(() => {}); stop.signal.removeEventListener('abort', abort); }
      if (!stop.signal.aborted) { onState('reconnecting'); await delay(retry); retry = Math.min(retry * 2, 30000); }
    }
  })();
  return () => stop.abort();
}
