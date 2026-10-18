package com.petitesannonces.categorie;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    boolean existsByLibelleIgnoreCase(String libelle);
}
