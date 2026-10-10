import test from 'node:test';
import assert from 'node:assert/strict';
import { adjustmentError } from './inventoryRules.js';
const inventory = { physicalCapacity:8, totalCapacity:8, racCapacity:2, confirmedReserved:5, racReserved:1 };
test('capacity can only reduce unused stock',()=>{assert.equal(adjustmentError(inventory,'5','1','Maintenance block'),'');assert.ok(adjustmentError(inventory,'4','1','Maintenance block'));assert.ok(adjustmentError(inventory,'5','0','Maintenance block'));});
test('physical ceiling, integers and reason are required',()=>{for(const [c,r,why] of [['9','2','Extra seats'],['','2','Testing'],['6.5','2','Testing'],['6','-1','Testing'],['6','1',' ']])assert.ok(adjustmentError(inventory,c,r,why));});
test('unchanged values rejected and zero capacity allowed when unreserved',()=>{assert.ok(adjustmentError(inventory,'8','2','No change'));assert.equal(adjustmentError({...inventory,confirmedReserved:0,racReserved:0},'0','0','Close unsold inventory'),'');});
