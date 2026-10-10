export function timeMinutes(value) {
  if (!/^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(value ?? '')) return null;
  const [hour, minute, second = 0] = value.split(':').map(Number);
  return hour * 60 + minute + second / 60;
}
export function haltMinutes(stop) {
  const arrival = timeMinutes(stop.arrival), departure = timeMinutes(stop.departure);
  return arrival === null || departure === null || departure < arrival ? null : Math.floor(departure - arrival);
}
const integer = value => String(value).trim() !== '' && Number.isInteger(Number(value));
export function validateRoute(stops) {
  const issues = [], seen = new Set();
  if (stops.length < 2 || stops.length > 100) issues.push({ index: -1, message: 'A route needs 2 to 100 stops.' });
  let previousDeparture = null, previousDistance = null;
  stops.forEach((stop, index) => {
    const first = index === 0, last = index === stops.length - 1;
    const issue = message => issues.push({ index, message });
    const code = stop.station?.code?.trim().toUpperCase();
    if (!code) issue('Select a station from the search results.');
    else if (seen.has(code)) issue('Each station must appear only once.');
    if (code) seen.add(code);
    const day = Number(stop.dayOffset), distance = Number(stop.distanceKm);
    const validDay = integer(stop.dayOffset) && day >= 0 && day <= 14;
    const validDistance = integer(stop.distanceKm) && distance >= 0 && distance <= 30000;
    if (!validDay) issue('Journey day must be a whole number from 1 to 15.');
    if (!validDistance) issue('Enter a whole distance from 0 to 30,000 km.');
    if (first && (day !== 0 || distance !== 0)) issue('Source must start on Day 1 at 0 km.');
    if (validDistance && previousDistance !== null && distance <= previousDistance) issue('Distance must exceed the previous stop.');
    previousDistance = validDistance ? distance : null;
    const arrival = timeMinutes(stop.arrival), departure = timeMinutes(stop.departure);
    if (!first && arrival === null) issue('Enter a valid arrival time.');
    if (!last && departure === null) issue('Enter a valid departure time.');
    if (first && stop.arrival) issue('Source cannot have an arrival time.');
    if (last && stop.departure) issue('Destination cannot have a departure time.');
    const start = first ? departure : arrival, end = last ? arrival : departure;
    if (validDay && start !== null && previousDeparture !== null && day * 1440 + start <= previousDeparture) issue('Arrival must follow the previous departure. Check journey day.');
    if (!first && !last && arrival !== null && departure !== null && departure < arrival) issue('Departure must not precede arrival on this day.');
    previousDeparture = validDay && end !== null ? day * 1440 + end : null;
  });
  return issues;
}
export function moveStop(stops, from, to) {
  if (!Number.isInteger(from) || !Number.isInteger(to) || from < 1 || to < 1 || from >= stops.length - 1 || to >= stops.length - 1 || from === to) return stops;
  const result = [...stops];
  const [stop] = result.splice(from, 1);
  result.splice(to, 0, stop);
  return result;
}
export function routePayload(stops) {
  const issues = validateRoute(stops);
  if (issues.length) throw new Error(issues[0].message);
  return stops.map((stop, index) => ({
    stationCode: stop.station.code, stopSequence: index + 1,
    arrival: stop.arrival || null, departure: stop.departure || null,
    dayOffset: Number(stop.dayOffset), distanceKm: Number(stop.distanceKm)
  }));
}
