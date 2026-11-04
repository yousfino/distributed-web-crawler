import { useState } from 'react';
import { GraduationCap, BookOpen, Globe } from 'lucide-react';
import CrawlerApp from './App';
import { ResearchLibrary } from './components/ResearchLibrary';

export default function Workspace() {
  const [tab, setTab] = useState<'research' | 'crawler'>('research');
  return <div className="min-h-screen bg-slate-950 text-slate-100">
    <header className="border-b border-slate-800 bg-slate-900/70 px-4 md:px-8">
      <div className="mx-auto flex max-w-screen-2xl flex-wrap items-center justify-between gap-5 py-4">
        <div className="flex items-center gap-3"><div className="rounded-xl bg-teal-400/10 p-2 text-teal-300"><GraduationCap size={25} /></div><div><p className="text-lg font-semibold tracking-tight">Research Atlas</p><p className="text-xs text-slate-400">Discover. Screen. Cite.</p></div></div>
        <nav aria-label="Workspace" className="flex gap-2">{([{ id: 'research', label: 'Research Library', icon: BookOpen }, { id: 'crawler', label: 'Web Crawler', icon: Globe }] as const).map(({ id, label, icon: Icon }) =>
          <button key={id} aria-current={tab === id ? 'page' : undefined} onClick={() => setTab(id)} className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm ${tab === id ? 'bg-teal-400/10 text-teal-300' : 'text-slate-400 hover:bg-slate-800'}`}><Icon size={16} />{label}</button>)}</nav>
      </div>
    </header>
    <div hidden={tab !== 'research'}><ResearchLibrary /></div>
    <div hidden={tab !== 'crawler'}><CrawlerApp /></div>
  </div>;
}
