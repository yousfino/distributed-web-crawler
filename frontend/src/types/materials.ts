export type MaterialKind = 'DATASET' | 'CODE' | 'SUPPLEMENT' | 'PROTOCOL' | 'PROJECT' | 'REPOSITORY';
export type MaterialReview = 'UNREVIEWED' | 'VERIFIED' | 'REJECTED' | 'FOLLOW_UP';
export type MaterialAccess = 'RESPONDED' | 'NOT_FOUND' | 'RESTRICTED' | 'RATE_LIMITED' | 'ERROR' | 'UNKNOWN' | 'UNCHECKED';
export type ScanPhase = 'QUEUED' | 'SCANNING' | 'COMPLETE' | 'FAILED' | 'INTERRUPTED';
export interface MaterialEvidence { paperUrl: string; pageUrl: string; anchorText: string; context: string; reason: string; discoveredAt: string }
export interface MaterialCheck { access: MaterialAccess; httpStatus: number | null; finalUrl: string; details: string; checkedAt: string | null }
export interface MaterialFinding { id: string; kind: MaterialKind; label: string; url: string; evidence: MaterialEvidence[]; check: MaterialCheck; review: MaterialReview; notes: string }
export interface MaterialPage { requestedUrl: string; finalUrl: string; title: string; httpStatus: number | null; outcome: string; candidates: number }
export interface MaterialInventory { id: string; name: string; paperUrls: string[]; createdAt: string; finishedAt: string | null; phase: ScanPhase; pages: MaterialPage[]; findings: MaterialFinding[]; warnings: string[] }
export interface MaterialSummary { id: string; name: string; createdAt: string; phase: ScanPhase; papers: number; findings: number; verified: number }
