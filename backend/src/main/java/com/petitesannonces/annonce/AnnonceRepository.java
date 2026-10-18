package com.petitesannonces.annonce;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnnonceRepository extends JpaRepository<Annonce, UUID> {

    Page<Annonce> findByStatut(Statut statut, Pageable pageable);

    Page<Annonce> findByStatutAndCategorieId(Statut statut, Long categorieId, Pageable pageable);

    List<Annonce> findByAuteurIdOrderByDateCreationDesc(String auteurId);

    boolean existsByIdAndAuteurId(UUID id, String auteurId);
}
