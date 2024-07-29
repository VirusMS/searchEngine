package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteShort;
import searchengine.config.SitesList;
import searchengine.entity.Site;
import searchengine.entity.SiteStatus;
import searchengine.exception.ApiServiceException;
import searchengine.model.request.SearchRequest;
import searchengine.model.response.IndexingResponse;
import searchengine.model.response.SearchResponse;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApiService {

    private static final Pattern HttpPattern = Pattern.compile("^https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
    //Pattern src: https://stackoverflow.com/questions/3809401/what-is-a-good-regular-expression-to-match-a-url

    private final SitesList siteList;

    private final SiteRepository siteRepository;
    private final IndexingService indexingService;

    public IndexingResponse startIndexing() {
        if (isIndexingInProgress(siteRepository.findAll())) {
            throw new ApiServiceException("Индексация уже запущена");
        }
        indexingService.startIndexing();
        return new IndexingResponse(true);
    }

    public IndexingResponse stopIndexing() {
        if (!isIndexingInProgress(siteRepository.findAll())) {
            throw new ApiServiceException("Индексация не запущена");
        }
        indexingService.stopIndexing();
        return new IndexingResponse(true);
    }

    public IndexingResponse indexPage(String url) {
        return new IndexingResponse(true);
    }

    public SearchResponse search(SearchRequest request) {

        validateRequest(request);

        return new SearchResponse();
    }

    private boolean isIndexingInProgress(List<Site> sites) {
        List<SiteShort> sitesToIndex = siteList.getSites();

        for (SiteShort siteShort : sitesToIndex) {
            for (Site site : sites) {
                if (site.getUrl().equals(siteShort.getUrl()) && site.getStatus() == SiteStatus.INDEXING) {
                    return true;
                }
            }
        }

        return false;
    }

    /*private List<Site> getAllSitesByNames(List<Site> sites) {
        List<String> urls = siteList.getUrlList();

        List<Site> result = new ArrayList<>();
        for (Site site : sites) {
            if (urls.contains(site.getUrl())) {
                result.add(site);
            }
        }

        return result;
    }*/

    private void validateRequest(SearchRequest request) {
        if (!HttpPattern.matcher(request.getSite()).matches()) {
            throw new ApiServiceException("Указанный в запросе URL неверен!");
        }
        else if (request.getLimit() < 0) {
            throw new ApiServiceException("Параметр Limit не может быть меньше 0!");
        }
        else if (request.getOffset() < 0) {
            throw new ApiServiceException("Параметр Offset не может быть меньше 0!");
        }
        else if (request.getQuery().isEmpty()) {
            throw new ApiServiceException("Задан пустой поисковый запрос (параметр 'query')");
        }
    }

}
