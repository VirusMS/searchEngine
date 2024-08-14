package searchengine.repository;

import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.entity.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    @Query("SELECT s FROM Site s WHERE s.url IN ?1")
    List<Site> findAllInList(List<String> urls);

    default Optional<Site> findByUrl(String url) {
        return findOne(Example.of(new Site(null, null, null, null, url, null)));
    }

}
