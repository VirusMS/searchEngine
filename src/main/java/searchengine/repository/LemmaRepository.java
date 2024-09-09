package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.entity.Lemma;
import searchengine.entity.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Query("SELECT l FROM Lemma l WHERE l.site = ?1")
    List<Lemma> findAllBySite(Site site);

    @Query("SELECT l FROM Lemma l WHERE l.site = ?1 AND l.lemma IN ?2")
    List<Lemma> findAllBySiteAndLemmaList(Site site, List<String> lemmas);

    @Query("SELECT COUNT(ALL l) FROM Lemma l WHERE l.site = ?1")
    int countBySite(Site site);

}
