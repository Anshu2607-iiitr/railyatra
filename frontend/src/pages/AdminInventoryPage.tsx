import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../auth/AuthProvider';
import { adjustmentError } from './inventoryRules';
import './admin-inventory.css';

type Inventory = { id:number; trainNumber:string; trainName:string; journeyDate:string; classCode:string; quota:string; version:number; physicalCapacity:number; totalCapacity:number; available:number; confirmed:number; held:number; confirmedHeld:number; racHeld:number; waitlistHolds:number; racCapacity:number; racAvailable:number; rac:number; waitingList:number; confirmedReserved:number; racReserved:number };
type Selection = { trainNumber:string; date:string; class:string; quota:string };
type Audit = { id:number; actorId:number; actorName:string; reason:string; createdAt:string; before:Inventory; after:Inventory };
type AuditPage = { content:Audit[]; page:number; totalPages:number; totalElements:number };
type Review = { key:string; id:number; totalCapacity:number; racCapacity:number; expectedVersion:number; reason:string; before:Inventory };
const today = () => new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
function errorText(error:unknown) {
 if(axios.isAxiosError(error)) {
  if([401,403].includes(error.response?.status ?? 0)) return 'Administrator access is required. Sign in again.';
  return error.response?.data?.message ?? 'Request failed. Check your connection and retry.';
 }
 return 'The request could not be completed.';
}
export function AdminInventoryPage({ apiBaseUrl }:{apiBaseUrl:string}) {
 const { accessToken }=useAuth();
 const [train,setTrain]=useState(''),[date,setDate]=useState(today),[cls,setCls]=useState('SL'),[quota,setQuota]=useState('GENERAL');
 const [selection,setSelection]=useState<Selection|null>(null),[data,setData]=useState<Inventory|null>(null);
 const [capacity,setCapacity]=useState(''),[rac,setRac]=useState(''),[reason,setReason]=useState('');
 const [review,setReview]=useState<Review|null>(null),[busy,setBusy]=useState(false),[loading,setLoading]=useState(false);
 const [error,setError]=useState(''),[notice,setNotice]=useState(''),[live,setLive]=useState(false),[revision,setRevision]=useState(0);
 const [audit,setAudit]=useState<AuditPage|null>(null),[page,setPage]=useState(0),[auditError,setAuditError]=useState('');
 const initialized=useRef(false);
 const headers={Authorization:`Bearer ${accessToken}`};
 useEffect(()=>{
  if(!selection||!accessToken)return;
  const controller=new AbortController();setLoading(true);
  axios.get<Inventory>(`${apiBaseUrl}/api/admin/inventory`,{headers:{Authorization:`Bearer ${accessToken}`},params:selection,signal:controller.signal})
   .then(r=>{if(controller.signal.aborted)return;setData(r.data);setError('');if(!initialized.current){setCapacity(String(r.data.totalCapacity));setRac(String(r.data.racCapacity));initialized.current=true;}})
   .catch(e=>{if(!controller.signal.aborted)setError(errorText(e));}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return ()=>controller.abort();
 },[selection,accessToken,apiBaseUrl,revision]);
 useEffect(()=>{
  if(!selection)return;
  const source=new EventSource(`${apiBaseUrl}/api/availability/stream?${new URLSearchParams(selection)}`);
  const refresh=()=>setRevision(n=>n+1);
  const connected=()=>{setLive(true);refresh();};
  source.addEventListener('connected',connected);source.addEventListener('availability-change',refresh);
  source.onerror=()=>setLive(false);
  const timer=window.setInterval(refresh,30000);
  return ()=>{source.close();window.clearInterval(timer);setLive(false);};
 },[selection,apiBaseUrl]);
 useEffect(()=>{
  if(!data||!accessToken)return;
  const controller=new AbortController();
  axios.get<AuditPage>(`${apiBaseUrl}/api/admin/inventory/${data.id}/adjustments`,{headers:{Authorization:`Bearer ${accessToken}`},params:{page,size:10},signal:controller.signal})
   .then(r=>{if(!controller.signal.aborted){setAudit(r.data);setAuditError('');}})
   .catch(e=>{if(!controller.signal.aborted)setAuditError(errorText(e));});
  return ()=>controller.abort();
 },[data?.id,data?.version,page,apiBaseUrl,accessToken,revision]);
 function inspect(event:FormEvent){event.preventDefault();if(busy)return;initialized.current=false;setData(null);setAudit(null);setReview(null);setPage(0);setNotice('');setError('');setSelection({trainNumber:train.trim().toUpperCase(),date,class:cls,quota});}
 function prepare(event:FormEvent){event.preventDefault();const invalid=adjustmentError(data,capacity,rac,reason);if(invalid){setError(invalid);return;}if(!data)return;setError('');setReview({id:data.id,key:crypto.randomUUID(),totalCapacity:Number(capacity),racCapacity:Number(rac),expectedVersion:data.version,reason:reason.trim(),before:data});}
 async function apply(){
  if(!review)return;setBusy(true);setError('');
  try{
   const r=await axios.post<{inventory:Inventory;auditId:number;replayed:boolean}>(`${apiBaseUrl}/api/admin/inventory/${review.id}/adjustments`,{totalCapacity:review.totalCapacity,racCapacity:review.racCapacity,expectedVersion:review.expectedVersion,reason:review.reason},{headers:{...headers,'Idempotency-Key':review.key}});
   setData(r.data.inventory);setCapacity(String(r.data.inventory.totalCapacity));setRac(String(r.data.inventory.racCapacity));setReason('');setReview(null);setPage(0);setNotice(`Adjustment recorded · Audit #${r.data.auditId}${r.data.replayed?' (retry already applied)':''}`);setRevision(n=>n+1);
  }catch(e){setError(errorText(e));if(axios.isAxiosError(e)&&e.response){setReview(null);setRevision(n=>n+1);}}
  finally{setBusy(false);}
 }
 return <section className="admin-dashboard inventory-admin">
  <Link to="/admin" className="train-back">← Operations dashboard</Link>
  <h1>Seat inventory</h1><p>Inspect a journey and adjust unreserved capacity. Counts represent passengers.</p>
  <form className="inventory-selector" onSubmit={inspect}><fieldset disabled={busy}>
   <label>Train number<input required maxLength={10} pattern="[A-Za-z0-9]{1,10}" value={train} onChange={e=>setTrain(e.target.value)} placeholder="e.g. 18625"/></label>
   <label>Journey date<input type="date" required value={date} onChange={e=>setDate(e.target.value)}/></label>
   <label>Class<select value={cls} onChange={e=>setCls(e.target.value)}>{['1A','2A','3A','3E','SL','CC','EC','2S'].map(c=><option key={c}>{c}</option>)}</select></label>
   <label>Quota<select value={quota} onChange={e=>setQuota(e.target.value)}>{['GENERAL','TATKAL','LADIES','SENIOR_CITIZEN'].map(q=><option key={q} value={q}>{q.replaceAll('_',' ')}</option>)}</select></label>
   <button className="train-primary">Inspect inventory</button>
  </fieldset></form>
  {error&&<p role="alert" className="admin-error">{error}</p>}{notice&&<p role="status" className="train-success">{notice}</p>}
  {selection&&<div className="inventory-live"><span>{live?'Live updates connected':'Connecting / reconnecting · automatic refresh enabled'}</span><button className="train-secondary" disabled={busy||loading} onClick={()=>setRevision(n=>n+1)}>{loading?'Refreshing…':'Refresh'}</button></div>}
  {!data&&loading&&<p role="status">Loading authoritative inventory…</p>}
  {data&&<>
   <h2>{data.trainNumber} · {data.trainName}</h2><p>{data.journeyDate} · {data.classCode} · {data.quota} · Revision {data.version}</p>
   <div className="inventory-counts">{[['Total capacity',data.totalCapacity],['Held',data.held],['Confirmed',data.confirmed],['RAC',data.rac],['WL',data.waitingList],['Available',data.available]].map(([title,value])=><article key={title}><span>{title}</span><strong>{value}</strong></article>)}</div>
   <p className="train-note">Held: {data.confirmedHeld} confirmed + {data.racHeld} RAC. WL checkouts: {data.waitlistHolds} (no seat reserved). RAC remaining: {data.racAvailable}/{data.racCapacity}. Physical seats: {data.physicalCapacity}. Expired holds stay reserved until the expiry transaction releases them.</p>
   <form className="inventory-adjust" onSubmit={prepare}><h2>Adjust capacity</h2>
    <p>Existing passengers and seat assignments are preserved. Increasing capacity may promote RAC/WL bookings in queue order.</p>
    <fieldset disabled={busy||!!review||loading}>
     <label>Total confirmed capacity<input type="number" required step="1" min={data.confirmedReserved} max={data.physicalCapacity} value={capacity} onChange={e=>setCapacity(e.target.value)}/></label>
     <label>RAC capacity<input type="number" required step="1" min={data.racReserved} max="10000" value={rac} onChange={e=>setRac(e.target.value)}/></label>
     <label className="inventory-reason">Reason<textarea required minLength={5} maxLength={500} value={reason} onChange={e=>setReason(e.target.value)} placeholder="Explain why this journey's capacity is changing"/></label>
     <button className="train-primary">Review adjustment</button>
    </fieldset>
   </form>
   {review&&<section className="inventory-review" aria-label="Confirm inventory adjustment"><h2>Review before applying</h2>
    <p>Confirmed capacity: {review.before.totalCapacity} → {review.totalCapacity}. RAC capacity: {review.before.racCapacity} → {review.racCapacity}.</p><p>{review.reason}</p>
    <p>Current holds and bookings will be checked again under a database lock.</p>
    <button className="train-primary" disabled={busy} onClick={apply}>{busy?'Applying…':'Confirm adjustment'}</button>{' '}
    <button className="train-secondary" disabled={busy} onClick={()=>setReview(null)}>Back to edit</button>
   </section>}
   <section className="inventory-history"><h2>Adjustment audit</h2>{auditError&&<p role="alert" className="admin-error">{auditError} <button onClick={()=>setRevision(n=>n+1)}>Retry</button></p>}
    {audit?.content.length===0&&<p>No admin adjustments recorded.</p>}
    {audit?.content.map(entry=><article key={entry.id}><strong>#{entry.id} · {entry.actorName} (admin {entry.actorId})</strong><time>{new Date(entry.createdAt).toLocaleString('en-IN')}</time><p>{entry.reason}</p><p>Capacity {entry.before.totalCapacity} → {entry.after.totalCapacity} · RAC capacity {entry.before.racCapacity} → {entry.after.racCapacity}</p><p>Available {entry.before.available} → {entry.after.available} · CNF {entry.before.confirmed} → {entry.after.confirmed} · RAC {entry.before.rac} → {entry.after.rac} · WL {entry.before.waitingList} → {entry.after.waitingList} · Held {entry.before.held} → {entry.after.held}</p></article>)}
    {audit&&<div className="train-pagination"><button className="train-secondary" disabled={page===0} onClick={()=>setPage(p=>p-1)}>Previous</button><span>Page {audit.totalPages?page+1:0} of {audit.totalPages}</span><button className="train-secondary" disabled={page+1>=audit.totalPages} onClick={()=>setPage(p=>p+1)}>Next</button></div>}
   </section>
  </>}
 </section>;
}
