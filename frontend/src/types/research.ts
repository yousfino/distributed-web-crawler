export type Decision = 'UNDECIDED' | 'INCLUDED' | 'EXCLUDED';
export interface Provenance {
  source: string; sourceId: string; sourceUrl: string;
  searchId: string; query: string; retrievedAt: string;
}
export interface Paper {
  id: string; doi: string; title: string; authors: string[]; year: number | null;
  venue: string; publicationType: string; abstractText: string; url: string;
  openAccessUrl: string; provenance: Provenance[]; decision: Decision; reason: string; notes: string;
}
export interface SearchRequest {
  query: string; fromYear: number | null; toYear: number | null;
  publicationType: string | null; sources: string[];
}
export interface SearchRun {
  id: string; request: SearchRequest; searchedAt: string;
  found: number; added: number; warnings: string[];
}
export interface ResearchProject {
  id: string; name: string; question: string; createdAt: string; papers: Paper[]; searches: SearchRun[];
}
export interface ProjectSummary {
  id: string; name: string; question: string; createdAt: string;
  papers: number; included: number; excluded: number;
}
