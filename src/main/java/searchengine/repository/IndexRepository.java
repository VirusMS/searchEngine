package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.entity.Index;
import searchengine.entity.Lemma;
import searchengine.entity.Page;

import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer> {

    @Query("SELECT i FROM Index i WHERE i.page = ?1")
    public List<Index> findByPage(Page page);

    @Query("SELECT i FROM Index i WHERE i.page IN ?1 AND i.lemma IN ?2")
    public List<Index> findByPageAndLemmaLists(List<Page> pages, List<Lemma> lemmas);

}
