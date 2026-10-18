package com.petitesannonces.categorie;

import com.petitesannonces.erreur.ConflitException;
import com.petitesannonces.erreur.RessourceIntrouvableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategorieService {

    private final CategorieRepository repository;

    public CategorieService(CategorieRepository repository) {
        this.repository = repository;
    }

    public List<Categorie> lister() {
        return repository.findAll(Sort.by("libelle"));
    }

    @Transactional
    public Categorie creer(String libelle) {
        if (repository.existsByLibelleIgnoreCase(libelle)) {
            throw new ConflitException("La catégorie « " + libelle + " » existe déjà.");
        }
        try {
            return repository.saveAndFlush(new Categorie(libelle));
        } catch (DataIntegrityViolationException e) {
            // Création concurrente du même libellé : l'index unique de la base tranche
            throw new ConflitException("La catégorie « " + libelle + " » existe déjà.");
        }
    }

    @Transactional
    public void supprimer(Long id) {
        Categorie categorie = repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Catégorie " + id + " introuvable."));
        try {
            repository.delete(categorie);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            // MT-09 : la clé étrangère annonce.categorie_id (ON DELETE RESTRICT) refuse la suppression
            throw new ConflitException("La catégorie « " + categorie.getLibelle()
                    + " » est utilisée par au moins une annonce : suppression impossible.");
        }
    }
}
