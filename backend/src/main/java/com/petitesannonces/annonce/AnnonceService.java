package com.petitesannonces.annonce;

import com.petitesannonces.annonce.AnnonceDtos.AnnonceRequest;
import com.petitesannonces.categorie.CategorieRepository;
import com.petitesannonces.erreur.ChampInvalideException;
import com.petitesannonces.erreur.RessourceIntrouvableException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Logique métier des annonces. Aucune règle d'accès ici : elles sont toutes portées par
 * les annotations du contrôleur (BK-04).
 */
@Service
@Transactional(readOnly = true)
public class AnnonceService {

    private final AnnonceRepository annonces;
    private final CategorieRepository categories;

    public AnnonceService(AnnonceRepository annonces, CategorieRepository categories) {
        this.annonces = annonces;
        this.categories = categories;
    }

    public Page<Annonce> listerPubliees(Long categorieId, Pageable pageable) {
        return categorieId == null
                ? annonces.findByStatut(Statut.PUBLIEE, pageable)
                : annonces.findByStatutAndCategorieId(Statut.PUBLIEE, categorieId, pageable);
    }

    public Annonce trouver(UUID id) {
        return annonces.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Annonce " + id + " introuvable."));
    }

    public List<Annonce> listerParAuteur(String auteurId) {
        return annonces.findByAuteurIdOrderByDateCreationDesc(auteurId);
    }

    @Transactional
    public Annonce creer(AnnonceRequest requete, String auteurId) {
        verifierCategorie(requete.categorieId());
        return annonces.save(new Annonce(requete.titre(), requete.description(), requete.prix(),
                requete.categorieId(), auteurId));
    }

    @Transactional
    public Annonce modifier(UUID id, AnnonceRequest requete) {
        Annonce annonce = trouver(id);
        verifierCategorie(requete.categorieId());
        annonce.modifier(requete.titre(), requete.description(), requete.prix(), requete.categorieId());
        return annonce;
    }

    @Transactional
    public void supprimer(UUID id) {
        annonces.delete(trouver(id));
    }

    private void verifierCategorie(Long categorieId) {
        if (!categories.existsById(categorieId)) {
            throw new ChampInvalideException("categorieId", "ne correspond à aucune catégorie");
        }
    }
}
