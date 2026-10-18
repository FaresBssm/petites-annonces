-- "Emploi" et "emploi" désignent la même catégorie : unicité insensible à la casse.
-- (Nouvelle migration plutôt que modifier V1 : une migration appliquée ne change plus.)
ALTER TABLE categorie DROP CONSTRAINT uk_categorie_libelle;
CREATE UNIQUE INDEX uk_categorie_libelle_ci ON categorie (lower(libelle));
