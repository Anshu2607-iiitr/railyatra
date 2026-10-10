export type InventoryLimits = { physicalCapacity:number; confirmedReserved:number; racReserved:number; totalCapacity:number; racCapacity:number };
export function adjustmentError(data:InventoryLimits|null,capacity:string,rac:string,reason:string):string;
