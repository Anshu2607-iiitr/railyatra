import { test } from 'node:test';
import assert from 'node:assert/strict';
import { activePassengerIds, togglePassenger, quoteExpired } from './cancellationRules.js';
test('full cancellation selects only active passengers', () => {
  assert.deepEqual(activePassengerIds([{ id: 1, cancelled: false }, { id: 2, cancelled: true }, { id: 3, cancelled: false }]), [1, 3]);
});
test('individual selection prevents duplicates and already cancelled passengers', () => {
  assert.deepEqual(togglePassenger([1], { id: 1, cancelled: false }, true), [1]);
  assert.deepEqual(togglePassenger([1], { id: 2, cancelled: true }, true), [1]);
  assert.deepEqual(togglePassenger([1, 3], { id: 1, cancelled: false }, false), [3]);
});
test('confirmation expires at the server supplied deadline', () => {
  const quote = { expiresAt: '2026-10-09T10:00:00Z' };
  const deadline = Date.parse(quote.expiresAt);
  assert.equal(quoteExpired(quote, deadline - 1), false);
  assert.equal(quoteExpired(quote, deadline), true);
  assert.equal(quoteExpired(null, deadline), false);
});
