import { KeyboardEvent, useEffect, useId, useState } from 'react';
import axios from 'axios';

export type Station = { code: string; name: string; city: string; state: string | null };
type Props = { label: string; apiBaseUrl: string; onSelect: (station: Station | null) => void; selectedStation?: Station | null };

export function StationAutocomplete({ label, apiBaseUrl, onSelect, selectedStation }: Props) {
  const listId = useId(); const [query, setQuery] = useState(''); const [items, setItems] = useState<Station[]>([]); const [status, setStatus] = useState<'idle'|'loading'|'error'>('idle'); const [active, setActive] = useState(-1); const [selected, setSelected] = useState<Station | null>(null);
  useEffect(() => { const clean=query.trim(); if(clean.length<2){setItems([]);setStatus('idle');return;} const controller=new AbortController(); const timer=window.setTimeout(()=>{setStatus('loading');axios.get<Station[]>(`${apiBaseUrl}/api/stations/search`,{params:{q:clean},signal:controller.signal}).then(response=>{setItems(response.data);setActive(-1);setStatus('idle');}).catch(error=>{if(!axios.isCancel(error)){setItems([]);setStatus('error');}});},300); return ()=>{window.clearTimeout(timer);controller.abort();};},[query,apiBaseUrl]);
  useEffect(() => { if (selectedStation) { setSelected(selectedStation); setQuery(`${selectedStation.name} (${selectedStation.code})`); } }, [selectedStation]);
  function choose(station: Station){setSelected(station);setQuery(`${station.name} (${station.code})`);setItems([]);setActive(-1);onSelect(station);}
  function changed(value:string){setSelected(null);onSelect(null);setQuery(value);}
  function keyDown(event:KeyboardEvent<HTMLInputElement>){if(!items.length)return;if(event.key==='ArrowDown'){event.preventDefault();setActive(index=>(index+1)%items.length);}else if(event.key==='ArrowUp'){event.preventDefault();setActive(index=>(index-1+items.length)%items.length);}else if(event.key==='Enter'&&active>=0){event.preventDefault();choose(items[active]);}else if(event.key==='Escape'){setItems([]);}}
  const open=!selected && query.trim().length>=2;
  return <div className="station-autocomplete"><label>{label}<input value={query} onChange={event=>changed(event.target.value)} onKeyDown={keyDown} role="combobox" aria-expanded={open} aria-controls={listId} aria-activedescendant={active>=0?`${listId}-${active}`:undefined} placeholder="Station code, name or city" autoComplete="off" /></label>{open&&<div id={listId} className="station-menu" role="listbox">{status==='loading'&&<p>Searching stations…</p>}{status==='error'&&<p role="alert">Station search is unavailable. Try again.</p>}{status==='idle'&&items.length===0&&<p>No matching station found.</p>}{status==='idle'&&items.map((station,index)=><button type="button" id={`${listId}-${index}`} role="option" aria-selected={index===active} className={index===active?'active':''} key={station.code} onMouseDown={event=>event.preventDefault()} onClick={()=>choose(station)}><strong>{station.code}</strong><span>{station.name} · {station.city}{station.state?`, ${station.state}`:''}</span></button>)}</div>}</div>;
}
