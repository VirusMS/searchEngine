package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.assets.SiteShort;
import searchengine.entity.*;
import searchengine.exception.ApiServiceException;
import searchengine.mapper.LemmaMapper;
import searchengine.mapper.SnippetMapper;
import searchengine.model.response.IndexingResponse;
import searchengine.model.response.SearchItem;
import searchengine.model.response.SearchResponse;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.assets.IndexingTask;
import searchengine.utils.DebugUtils;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApiService {

    private static final Pattern PATTERN_HTTP = Pattern.compile("^https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
    private static final String ERR_INDEXING_ON = "Индексация уже запущена";
    private static final String ERR_INDEXING_OFF = "Индексация не запущена";
    private static final String ERR_INDEXING_NOT_DONE = "Сайт не был проиндексирован!";
    private static final String ERR_SITE_NOT_IN_CONFIG = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
    private static final String ERR_VALIDATION_WRONG_URL = "Указанный в запросе URL неверен!";
    private static final String ERR_VALIDATION_WRONG_LIMIT = "Параметр Limit не может быть меньше 0!";
    private static final String ERR_VALIDATION_WRONG_OFFSET = "Параметр Offset не может быть меньше 0!";
    private static final String ERR_VALIDATION_EMPTY_QUERY = "Задан пустой поисковый запрос (параметр 'query')";

    private final DebugUtils debugUtils;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingService indexingService;
    private final AppConfig appConfig;

    private IndexingTask indexingTask;

    public IndexingResponse startIndexing() {

        if (isIndexingInProgress(siteRepository.findAll())) {
            throw new ApiServiceException(ERR_INDEXING_ON);
        }

        indexingTask = new IndexingTask(indexingService::startIndexing);
        indexingTask.start();

        return new IndexingResponse(true);
    }

    public IndexingResponse stopIndexing() {
        if (!isIndexingInProgress(siteRepository.findAll())) {
            throw new ApiServiceException(ERR_INDEXING_OFF);
        }

        indexingTask.stop();
        indexingService.stopIndexing();

        return new IndexingResponse(true);
    }

    public IndexingResponse indexPage(String url) {

        for (SiteShort site : appConfig.getSites()) {
            if (url.contains(site.getUrl())) {
                indexingService.indexSinglePage(site, url);
                return new IndexingResponse(true);
            }
        }
        throw new ApiServiceException(ERR_SITE_NOT_IN_CONFIG);
    }

    public SearchResponse search(String query, String siteToQuery, int offset, int limit) {

        validateRequest(query, siteToQuery, offset, limit);

        List<String> lemmasRequest = LemmaMapper.getLemmasListFromText(query);

        List<Site> sitesToQuery;
        if (siteToQuery == null) {
            sitesToQuery = siteRepository.findAll();
        } else {
            Optional<Site> siteOptional = siteRepository.findByUrl(siteToQuery);
            if (siteOptional.isPresent()) {
                sitesToQuery = List.of(siteOptional.get());
            } else {
                throw new ApiServiceException(ERR_INDEXING_NOT_DONE);
            }
        }

        SearchResponse response = new SearchResponse();

        for (Site site : sitesToQuery) {
            List<SearchItem> items = getSearchItemsForSite(site, lemmasRequest);
            response.setCount(response.getCount() + items.size());
            if (!items.isEmpty()) {
                response.addData(items);
            }
        }

        response.sortResultsByDescendingRelevancy();
        response.setResultsByLimitAndOffset(limit, offset);

        System.out.println("COUNT = " + response.getCount());
        return response;
    }

    private List<SearchItem> getSearchItemsForSite(Site site, List<String> lemmasRequest) {
        List<SearchItem> items = new ArrayList<>();

        List<Page> pages = pageRepository.findAllBySite(site);
        List<Lemma> lemmas = getAscendingFrequencyLemmaList(
                removeLemmasOfHighFrequency(site, lemmaRepository.findAllBySiteAndLemmaList(site, lemmasRequest), appConfig.getMaxRelativeFrequency())
        );
        if (lemmas.isEmpty()) {
            return items;
        }
        List<Index> index = indexRepository.findByPageAndLemmaLists(pages, lemmas);
        pages = getPagesWithRequiredLemmasInIndex(pages, lemmas, index);

        for (Page page : pages) {
            List<Float> absRelevance = new ArrayList<>();

            for (Lemma lemma : lemmas) {
                Index entry = findIndexEntryByPageAndLemma(index, page.getId(), lemma.getId());
                absRelevance.add(entry.getRank());
            }

            float maxAbsRelevance = (float) absRelevance.stream().mapToDouble(r -> r).max().getAsDouble();
            float relativeRelevance = (float) absRelevance.stream().mapToDouble(r -> r).sum() / maxAbsRelevance;

            String snippets = SnippetMapper.getSnippets(page, lemmas, appConfig.getSnippetRadius());
            items.add(new SearchItem(site.getUrl(), site.getName(), page.getPath(), page.getTitle(), snippets, Float.toString(round(relativeRelevance, 2))));
        }

        return items;
    }

    private float round(float number, int scale) {
        int pow = 10;
        for (int i = 1; i < scale; i++)
            pow *= 10;
        float tmp = number * pow;
        return ( (float) ( (int) ((tmp - (int) tmp) >= 0.5f ? tmp + 1 : tmp) ) ) / pow;
    }

    private Index findIndexEntryByPageAndLemma(List<Index> index, int pageId, int lemmaId) {
        for (Index entry : index) {
            if (entry.getPage().getId() == pageId && entry.getLemma().getId() == lemmaId) {
                return entry;
            }
        }

        return null;
    }

    private boolean isIndexingInProgress(List<Site> sites) {

        if (indexingTask == null) {
            return false;
        }
        if (indexingTask.isRunning()) {
            return true;
        }

        List<SiteShort> sitesToIndex = appConfig.getSites();

        for (SiteShort siteShort : sitesToIndex) {
            for (Site site : sites) {
                if (site.getUrl().equals(siteShort.getUrl()) && site.getStatus() == SiteStatus.INDEXING) {
                    return true;
                }
            }
        }

        return false;
    }

    private void validateRequest(String query, String site, int offset, int limit) {
        if (site != null) {
            if (!PATTERN_HTTP.matcher(site).matches()) {
                throw new ApiServiceException(ERR_VALIDATION_WRONG_URL);
            }
        }
        if (limit < 0) {
            throw new ApiServiceException(ERR_VALIDATION_WRONG_LIMIT);
        }
        else if (offset < 0) {
            throw new ApiServiceException(ERR_VALIDATION_WRONG_OFFSET);
        }
        else if (query.isEmpty()) {
            throw new ApiServiceException(ERR_VALIDATION_EMPTY_QUERY);
        }
    }

    private List<Lemma> getAscendingFrequencyLemmaList(List<Lemma> lemmas) {
        List<Lemma> result = new ArrayList<>(lemmas);

        for (int i = 0; i < result.size() - 1; i++) {
            for (int j = 0; j < result.size() - i - 1; j++) {
                Lemma lj = result.get(j);
                Lemma ljp = result.get(j + 1);
                if (ljp.getFrequency() < lj.getFrequency()) {
                    result.set(j, ljp);
                    result.set(j + 1, lj);
                }
            }
        }

        return result;
    }

    private List<Lemma> removeLemmasOfHighFrequency(Site site, List<Lemma> lemmas, double maxRelativeFrequency) {
        int pageCount = pageRepository.countBySite(site);
        List<Lemma> newLemmas = new ArrayList<>(lemmas);

        Iterator i = newLemmas.iterator();
        while (i.hasNext()) {
            int relativeFrequency = ((Lemma) i.next()).getFrequency() / pageCount;
            if (relativeFrequency > maxRelativeFrequency) {
                debugUtils.println("DEBUG (ApiService.removeLemmasByHighFrequency): relative frequency is beyond acceptable for lemma '" + ((Lemma) i.next()).getLemma() + "', removing that lemma");
                i.remove();
            }
        }

        return newLemmas;
    }

    private List<Page> getPagesWithRequiredLemmasInIndex(List<Page> pages, List<Lemma> lemmas, List<Index> index) {
        List<Page> foundPages = new ArrayList<>(pages);

        Iterator i = foundPages.iterator();

        while (i.hasNext()) {
            Page page = (Page) i.next();

            List<Integer> lemmasIdsInIndex = index.stream()
                    .filter(e -> e.getPage().getId().equals(page.getId()))
                    .map(e -> e.getLemma().getId())
                    .toList();

            if (lemmasIdsInIndex.isEmpty()) {
                debugUtils.println("DEBUG (ApiService.getPagesWithRequiredLemmasInIndex): none of lemmas are in index for page '" + page.getPath() + "'. Removing that page.");
                i.remove();
            } else {
                for (Lemma lemma : lemmas) {
                    if (!lemmasIdsInIndex.contains(lemma.getId())) {
                        debugUtils.println("DEBUG (ApiService.getPagesWithRequiredLemmasInIndex): lemma '" + lemma.getLemma() + "' not found for page '" + page.getPath() + "'. Removing that page.");
                        i.remove();
                        break;
                    }
                }
            }
        }

        return foundPages;
    }

}
