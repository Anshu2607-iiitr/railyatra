import { test } from 'node:test';
import assert from 'node:assert/strict';
import { connectAdminDashboard } from './adminDashboardStream.js';

test('admin connection, changes and reconnect invalidate database snapshots; heartbeat does not', async () => {
  const original = globalThis.fetch;
  let requests = 0; let refreshes = 0; let close;
  try {
    await new Promise((resolve, reject) => {
      globalThis.fetch = async (url, options) => {
        assert.equal(url, 'http://example.test/api/admin/dashboard/events');
        assert.equal(options.headers.Authorization, 'Bearer admin-token');
        requests++;
        const frames = 'event: connected\ndata: {}\n\n: heartbeat\n\n' +
          (requests === 1 ? 'event: dashboard-change\ndata: {"refreshRequired":true}\n\n' : '');
        return new Response(new ReadableStream({ start(controller) {
          controller.enqueue(new TextEncoder().encode(frames)); controller.close();
        } }), { headers: { 'Content-Type': 'text/event-stream' } });
      };
      close = connectAdminDashboard('http://example.test/api/admin/dashboard/events', 'admin-token', () => {
        if (++refreshes === 3) { close(); resolve(); }
      }, () => {});
      setTimeout(() => reject(new Error('No reconnect snapshot refresh')), 3000).unref();
    });
    assert.equal(requests, 2); assert.equal(refreshes, 3);
  } finally { close?.(); globalThis.fetch = original; }
});

test('non-admin rejection stops live refresh without delivering statistics', async () => {
  const original = globalThis.fetch; let requests = 0; let close;
  try {
    await new Promise(resolve => {
      globalThis.fetch = async () => { requests++; return new Response('', { status: 403 }); };
      close = connectAdminDashboard('http://example.test/api/admin/dashboard/events', 'user-token',
        () => assert.fail('Rejected user must not receive dashboard refreshes'),
        state => { if (state === 'unavailable') resolve(); });
    });
    assert.equal(requests, 1);
  } finally { close?.(); globalThis.fetch = original; }
});
