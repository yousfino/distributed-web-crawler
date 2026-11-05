import { useState } from 'react';
import { GraduationCap, BookOpen, Globe, FlaskConical } from 'lucide-react';
import CrawlerApp from './App';
import { MaterialsInventory } from './components/MaterialsInventory';
import { ResearchLibrary } from './components/ResearchLibrary';

export default function Workspace() {
  const [tab, setTab] = useState<'materials' | 'research' | 'crawler'>('materials');
  const [seed, setSeed] = useState<{ url: string; title: string; token: number }>();
  return <div className="min-h-screen bg-slate-950 text-slate-100">
    <header className="border-b border-slate-800 bg-slate-900/70 px-4 md:px-8">
      <div className="mx-auto flex max-w-screen-2xl flex-wrap items-center justify-between gap-5 py-4">
        <div className="flex items-center gap-3"><div className="rounded-xl bg-teal-400/10 p-2 text-teal-300"><GraduationCap size={25} /></div><div><p className="text-lg font-semibold tracking-tight">Research Atlas</p><p className="text-xs text-slate-400">Find materials. Trace evidence. Verify.</p></div></div>
        <nav aria-label="Workspace" className="flex gap-2">{([{ id: 'materials', label: 'Research Materials', icon: FlaskConical }, { id: 'research', label: 'Research Library', icon: BookOpen }, { id: 'crawler', label: 'Web Crawler', icon: Globe }] as const).map(({ id, label, icon: Icon }) =>
          <button key={id} aria-current={tab === id ? 'page' : undefined} onClick={() => setTab(id)} className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm ${tab === id ? 'bg-teal-400/10 text-teal-300' : 'text-slate-400 hover:bg-slate-800'}`}><Icon size={16} />{label}</button>)}</nav>
      </div>
    </header>
    <div hidden={tab !== 'materials'}><MaterialsInventory key={seed?.token ?? 'materials'} seed={seed} /></div>
    <div hidden={tab !== 'research'}><ResearchLibrary onInspectMaterials={(url, title) => { setSeed(previous => ({ url, title, token: (previous?.token ?? 0) + 1 })); setTab('materials'); }} /></div>
    <div hidden={tab !== 'crawler'}><CrawlerApp /></div>
  </div>;
}
