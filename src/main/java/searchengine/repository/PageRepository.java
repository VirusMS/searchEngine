package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.entity.Page;
import searchengine.entity.Site;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    @Query("SELECT p FROM Page p WHERE p.site = ?1")
    List<Page> findAllBySite(Site site);

    /*default List<Page> findAllBySite(Site site) {
        return findAll(Example.of(new Page(null, site, null, null, null)));
    }*/

}
