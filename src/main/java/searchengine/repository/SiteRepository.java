package searchengine.repository;

import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.entity.Site;

import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    default Optional<Site> findByUrl(String url) {
        return findOne(Example.of(new Site(null, null, null, null, url, null)));
    }

}
