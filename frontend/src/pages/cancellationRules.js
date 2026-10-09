export function activePassengerIds(passengers) {
  return passengers.filter(passenger => !passenger.cancelled).map(passenger => passenger.id);
}
export function togglePassenger(selection, passenger, checked) {
  if (passenger.cancelled) return selection;
  return checked ? [...new Set([...selection, passenger.id])] : selection.filter(id => id !== passenger.id);
}
export function quoteExpired(quote, now) {
  return !!quote && now >= new Date(quote.expiresAt).getTime();
}
