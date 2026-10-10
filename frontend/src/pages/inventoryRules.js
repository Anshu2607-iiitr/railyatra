export function adjustmentError(data, capacity, rac, reason) {
 if (!data) return 'Load inventory first.';
 const integer = value => String(value).trim() !== '' && Number.isInteger(Number(value));
 if (!integer(capacity) || !integer(rac) || Number(capacity) < 0 || Number(rac) < 0 || Number(rac) > 10000) return 'Enter non-negative whole capacities (RAC maximum 10,000).';
 if (Number(capacity) > data.physicalCapacity) return 'Capacity exceeds configured physical seats.';
 if (Number(capacity) < data.confirmedReserved || Number(rac) < data.racReserved) return 'Capacity cannot remove confirmed reservations or held inventory.';
 if (reason.trim().length < 5 || reason.trim().length > 500) return 'Enter a reason of 5–500 characters.';
 if (Number(capacity) === data.totalCapacity && Number(rac) === data.racCapacity) return 'Change a capacity before submitting.';
 return '';
}
