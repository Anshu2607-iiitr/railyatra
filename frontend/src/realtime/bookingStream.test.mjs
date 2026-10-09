import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createSseParser, connectBookingStream } from './bookingStream.js';

test('SSE parser handles split CRLF frames, heartbeats and multiple event types', () => {
  const events = []; const parse = createSseParser((event, data) => events.push([event, data]));
  parse('event: connected\r\ndata: {}\r'); parse('\n\r\n: heartbeat\n\n');
  parse('event: booking-status-change\ndata: {"refreshRequired":true}\n\nevent: refund-status-change\ndata: {}\n\n');
  assert.deepEqual(events.map(item => item[0]), ['connected', 'booking-status-change', 'refund-status-change']);
});

test('reconnect triggers connected again and keeps credentials out of URLs', async () => {
  const original = globalThis.fetch; let calls = 0; let opens = 0; let close;
  try {
    const ready = new Promise((resolve, reject) => {
      globalThis.fetch = async (url, options) => {
        assert.equal(url.includes('secret'), false); assert.equal(options.headers.Authorization, 'Bearer secret'); calls++;
        return new Response(new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode('event: connected\ndata: {}\n\n')); controller.close(); } }), { headers: { 'Content-Type': 'text/event-stream' } });
      };
      close = connectBookingStream('http://example.test/events', 'secret', event => { if (event === 'connected' && ++opens === 2) { close(); resolve(); } }, () => {});
      setTimeout(() => reject(new Error('No reconnect')), 3000).unref();
    });
    await ready; assert.equal(calls, 2);
  } finally { close?.(); globalThis.fetch = original; }
});

test('access rejection stops reconnecting instead of retrying another users stream', async () => {
  const original = globalThis.fetch; let calls = 0; let close;
  try {
    await new Promise(resolve => {
      globalThis.fetch = async () => { calls++; return new Response('', { status: 404 }); };
      close = connectBookingStream('http://example.test/events', 'secret', () => assert.fail('No events should be delivered'), state => { if (state === 'unavailable') resolve(); });
    });
    assert.equal(calls, 1);
  } finally { close?.(); globalThis.fetch = original; }
});
