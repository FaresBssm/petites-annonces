export type Statut = 'EN_ATTENTE' | 'PUBLIEE' | 'REJETEE';

export interface Annonce {
  id: string;
  titre: string;
  description: string | null;
  prix: number;
  categorieId: number;
  statut: Statut;
  auteurId: string;
  motifRejet: string | null;
  dateCreation: string;
  dateModification: string;
}

/** Corps de POST / PUT : jamais d'auteurId ni de statut, ils sont décidés par le serveur. */
export interface AnnonceSaisie {
  titre: string;
  description: string | null;
  prix: number;
  categorieId: number;
}

export interface Categorie {
  id: number;
  libelle: string;
}

export interface Page<T> {
  contenu: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const LIBELLES_STATUT: Record<Statut, string> = {
  EN_ATTENTE: 'En attente de modération',
  PUBLIEE: 'Publiée',
  REJETEE: 'Rejetée',
};
