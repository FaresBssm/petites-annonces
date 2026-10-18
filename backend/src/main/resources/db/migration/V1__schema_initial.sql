-- Schéma de la base "annonces" (géré par Flyway, jamais par Hibernate)

CREATE TABLE categorie (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    libelle  VARCHAR(50) NOT NULL,
    CONSTRAINT uk_categorie_libelle UNIQUE (libelle),
    CONSTRAINT ck_categorie_libelle_longueur CHECK (char_length(libelle) BETWEEN 2 AND 50)
);

CREATE TABLE annonce (
    id                 UUID           PRIMARY KEY,
    titre              VARCHAR(100)   NOT NULL,
    description        VARCHAR(2000),
    prix               NUMERIC(12, 2) NOT NULL,
    -- RESTRICT : une catégorie utilisée ne peut pas être supprimée (-> 409, MT-09)
    categorie_id       BIGINT         NOT NULL REFERENCES categorie (id) ON DELETE RESTRICT,
    statut             VARCHAR(20)    NOT NULL,
    -- claim "sub" du token, transmis par la gateway dans X_USER_ID (jamais par le client)
    auteur_id          VARCHAR(64)    NOT NULL,
    motif_rejet        VARCHAR(500),
    date_creation      TIMESTAMPTZ    NOT NULL,
    date_modification  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_annonce_titre_longueur CHECK (char_length(titre) BETWEEN 5 AND 100),
    CONSTRAINT ck_annonce_prix_positif CHECK (prix >= 0),
    CONSTRAINT ck_annonce_statut CHECK (statut IN ('EN_ATTENTE', 'PUBLIEE', 'REJETEE'))
);

-- Liste publique filtrée par statut/catégorie, "mes annonces", file de modération
CREATE INDEX idx_annonce_statut_date ON annonce (statut, date_creation);
CREATE INDEX idx_annonce_categorie ON annonce (categorie_id);
CREATE INDEX idx_annonce_auteur ON annonce (auteur_id);
