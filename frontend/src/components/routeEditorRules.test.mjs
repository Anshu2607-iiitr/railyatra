import test from 'node:test';
import assert from 'node:assert/strict';
import { haltMinutes, moveStop, routePayload, validateRoute } from './routeEditorRules.js';
const stop = (code, arrival, departure, dayOffset, distanceKm) => ({ key: code, station: { code, name: code, city: 'City', state: null }, arrival, departure, dayOffset: String(dayOffset), distanceKm: String(distanceKm) });
const route = () => [
  stop('SRC', '', '22:00', 0, 0), stop('MID', '23:00', '23:05', 0, 100),
  stop('NEXT', '00:30', '00:35', 1, 200), stop('END', '03:00', '', 1, 400)
];
test('overnight route serializes ordered stops and zero-based day offsets', () => {
  assert.deepEqual(validateRoute(route()), []);
  const body = routePayload(route());
  assert.deepEqual(body.map(s => s.stopSequence), [1, 2, 3, 4]);
  assert.equal(body[0].arrival, null); assert.equal(body[3].departure, null);
  assert.equal(body[2].dayOffset, 1); assert.equal(haltMinutes(route()[1]), 5);
});
test('reorder keeps row identity, does not mutate state, and pins endpoints', () => {
  const original = route(), reordered = moveStop(original, 1, 2);
  assert.deepEqual(reordered.map(s => s.key), ['SRC', 'NEXT', 'MID', 'END']);
  assert.deepEqual(original.map(s => s.key), ['SRC', 'MID', 'NEXT', 'END']);
  for (const [from, to] of [[0, 1], [1, 0], [3, 1], [1, 3], [-1, 2]]) assert.equal(moveStop(original, from, to), original);
});
test('reordered stale timings and distances block save until corrected', () => {
  const reordered = moveStop(route(), 2, 1);
  assert.ok(validateRoute(reordered).some(i => i.message.includes('Distance')));
  assert.ok(validateRoute(reordered).some(i => i.message.includes('Arrival must follow')));
  assert.throws(() => routePayload(reordered));
  Object.assign(reordered[1], { arrival: '23:00', departure: '23:05', dayOffset: '0', distanceKm: '100' });
  Object.assign(reordered[2], { arrival: '00:30', departure: '00:35', dayOffset: '1', distanceKm: '200' });
  const body = routePayload(reordered);
  assert.deepEqual(body.map(s => [s.stationCode, s.stopSequence]), [['SRC', 1], ['NEXT', 2], ['MID', 3], ['END', 4]]);
});
test('removing an intermediate stop regenerates contiguous sequences', () => {
  const body = routePayload(route().filter((_, i) => i !== 1));
  assert.deepEqual(body.map(s => s.stopSequence), [1, 2, 3]);
});
test('missing and duplicate stations, blank distances and fractional days are invalid', () => {
  for (const patch of [{ station: null }, { station: route()[0].station }, { distanceKm: '' }, { distanceKm: '100.5' }, { dayOffset: '0.5' }, { dayOffset: '15' }]) {
    const rows = route(); Object.assign(rows[1], patch); assert.ok(validateRoute(rows).length);
  }
});
test('invalid time, backward halt, equal arrival and backward day are rejected', () => {
  for (const patch of [{ arrival: '24:00' }, { departure: '22:50' }, { arrival: '22:00' }, { arrival: '' }]) {
    const rows = route(); Object.assign(rows[1], patch); assert.ok(validateRoute(rows).length);
  }
  const rows = route(); rows[2].dayOffset = '0'; assert.ok(validateRoute(rows).length);
  assert.equal(haltMinutes({ arrival: '23:59', departure: '00:01' }), null);
  assert.equal(haltMinutes({ arrival: '10:00', departure: '10:00' }), 0);
});
test('source and destination semantics and route length are validated', () => {
  for (const [index, patch] of [[0, { arrival: '21:00' }], [0, { dayOffset: '1' }], [0, { distanceKm: '1' }], [3, { departure: '04:00' }]]) {
    const rows = route(); Object.assign(rows[index], patch); assert.ok(validateRoute(rows).length);
  }
  assert.ok(validateRoute([]).length); assert.ok(validateRoute([route()[0]]).length);
  assert.ok(validateRoute(Array.from({ length: 101 }, () => route()[1])).some(i => i.index === -1));
});
