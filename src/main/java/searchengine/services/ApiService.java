package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteShort;
import searchengine.config.SitesList;
import searchengine.entity.*;
import searchengine.exception.ApiServiceException;
import searchengine.mapper.LemmaMapper;
import searchengine.model.response.IndexingResponse;
import searchengine.model.response.SearchResponse;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiService {

    private static final Pattern HttpPattern = Pattern.compile("^https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
    //Pattern src: https://stackoverflow.com/questions/3809401/what-is-a-good-regular-expression-to-match-a-url
    private static final String ERR_INDEXING_ON = "Индексация уже запущена";
    private static final String ERR_INDEXING_OFF = "Индексация не запущена";
    private static final String ERR_SITE_NOT_IN_CONFIG = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
    private static final String ERR_VALIDATION_WRONG_URL = "Указанный в запросе URL неверен!";
    private static final String ERR_VALIDATION_WRONG_LIMIT = "Параметр Limit не может быть меньше 0!";
    private static final String ERR_VALIDATION_WRONG_OFFSET = "Параметр Offset не может быть меньше 0!";
    private static final String ERR_VALIDATION_EMPTY_QUERY = "Задан пустой поисковый запрос (параметр 'query')";

    private final SitesList siteList;

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingService indexingService;

    //  TODO: make sure startIndexing starts indexing in new thread, so one can stop it later
    public IndexingResponse startIndexing() {
        if (isIndexingInProgress(siteRepository.findAll())) {
            throw new ApiServiceException(ERR_INDEXING_ON);
        }
        indexingService.startIndexing();
        return new IndexingResponse(true);
    }

    //  TODO: make sure it stops indexing properly - comes after startIndexing is done
    public IndexingResponse stopIndexing() {
        if (!isIndexingInProgress(siteRepository.findAll())) {
            throw new ApiServiceException(ERR_INDEXING_OFF);
        }
        indexingService.stopIndexing();
        return new IndexingResponse(true);
    }

    public IndexingResponse indexPage(String url) {

        for (SiteShort site : siteList.getSites()) {
            if (url.contains(site.getUrl())) {
                indexingService.indexSinglePage(site, url);
                return new IndexingResponse(true);
            }
        }
        throw new ApiServiceException(ERR_SITE_NOT_IN_CONFIG);
    }

    //public SearchResponse search(SearchRequest request) {
    public SearchResponse search(String query, String siteToQuery, int offset, int limit) {

        validateRequest(query, siteToQuery, offset, limit);

        //System.out.println("DEBUG (search) : request = " + request);

//        1. Разбивать поисковый запрос на отдельные слова и формировать из этих слов список уникальных лемм, исключая междометия, союзы, предлоги и частицы.
        List<String> lemmasRequest = LemmaMapper.getLemmasListFromText(query);

        Optional<Site> siteOptional = siteRepository.findByUrl(siteToQuery);  //I guess there's nothing if all sites must be included.
        List<Site> sitesToQuery = siteOptional.isPresent() ? List.of(siteOptional.get()) : siteRepository.findAllInList(siteList.getUrlList());

        for (Site site : sitesToQuery) {
            List<Lemma> lemmas = lemmaRepository.findAllBySiteAndLemmaList(site, lemmasRequest);
            int pageCount = pageRepository.countBySite(site);

//        2. Исключать из полученного списка леммы, которые встречаются на слишком большом количестве страниц. Поэкспериментируйте и определите этот процент самостоятельно
//        СООТНОШЕНИЕ frequency / кол-во страниц - то, что нам надо
            Iterator i = lemmas.iterator();
            while (i.hasNext()) {
                int relativeFrequency = ((Lemma) i.next()).getFrequency() / pageCount;
                if (relativeFrequency > 0.95) { //TODO: figure out which percentage is best here and apply it
                    System.out.println("DEBUG (search): relative frequency is beyond acceptable for lemma '" + ((Lemma) i.next()).getLemma() + "', removing that lemma");
                    i.remove();
                }
            }

//        3. Сортировать леммы в порядке увеличения частоты встречаемости (по возрастанию значения поля frequency) — от самых редких до самых частых.
            lemmas = getAscendingLemmaList(lemmas);

//        4. По первой, самой редкой лемме из списка, находить все страницы, на которых она встречается. Далее искать соответствия следующей леммы из этого списка страниц,
//          а затем повторять операцию по каждой следующей лемме. Список страниц при этом на каждой итерации должен уменьшаться.
            List<Page> pages = pageRepository.findAllBySite(site);

            System.out.print("DEBUG (search): list of pages found for '" + site.getUrl() + "': ");
            for (Page page : pages) {
                System.out.print("'" + page.getPath() + "' ");
            }
            System.out.println("");

            List<Index> index = indexRepository.findAllByPageList(pages);

            i = pages.iterator();
            while (i.hasNext()) {
                Page page = (Page) i.next();

                List<Index> pageIndex = index.stream().filter(e -> e.getPage().getId() == page.getId()).toList();
                List<Integer> lemmaIds = getLemmaIdsFromIndex(index);

                for (Lemma lemma : lemmas) {
                    if (!lemmaIds.contains(lemma.getId())) {
                        System.out.println("  DEBUG (search) lemma ID " + lemma.getId() + " not found for page '" + page.getPath() + "', deleting the page");
                        i.remove();
                    }
                }
            }

            /*for (Index entry : index) {
                Lemma lemmaFromIndex = entry.getLemma();
                //System.out.println("  DEBUG (search): entry to parse: [lemma = '" + lemmaFromIndex + "', page = '" + entry.getPage() + "'");
                if (!lemmas.contains(lemmaFromIndex)) {
                    System.out.println("    DEBUG (search): lemma '" + lemmaFromIndex.getLemma() + "' not found in index entry, removing from lemma list.");
                    lemmas.remove(lemmaFromIndex);
                } else {
                    //System.out.println("    DEBUG (search): lemma found in index entry");
                }
            }*/

            System.out.print("  DEBUG (search): all lemmas parsed in index, current page list = ");
            for (Page page : pages) {
                System.out.print("'" + page.getPath() + "' ");
            }
            System.out.println("");

        }



//        4. По первой, самой редкой лемме из списка, находить все страницы, на которых она встречается. Далее искать соответствия следующей леммы из этого списка страниц,
//          а затем повторять операцию по каждой следующей лемме. Список страниц при этом на каждой итерации должен уменьшаться.
//                Если в итоге не осталось ни одной страницы, то выводить пустой список.
//                Если страницы найдены, рассчитывать по каждой из них релевантность (и выводить её потом, см. ниже) и возвращать.
//        5. Для каждой страницы рассчитывать абсолютную релевантность — сумму всех rank всех найденных на странице лемм (из таблицы index), которая делится на максимальное
//          значение этой абсолютной релевантности для всех найденных страниц.
//        6. Сортировать страницы по убыванию релевантности (от большей к меньшей) и выдавать в виде списка объектов
//        7. Сниппеты — фрагменты текстов, в которых найдены совпадения, для всех страниц должны быть примерно одинаковой длины — такие, чтобы на странице с результатами
//          поиска они занимали примерно три строки. В них необходимо выделять жирным совпадения с исходным поисковым запросом. Выделение должно происходить в формате HTML
//          при помощи тега <b>. Алгоритм получения сниппета из веб-страницы реализуйте самостоятельно.
//        8. Обратите внимание, что метод поиска должен учитывать, по каким сайтам происходит этот поиск — по всем или по тому, который выбран в веб-интерфейсе в
//          выпадающем списке.

        return new SearchResponse();
    }

    private List<Integer> getLemmaIdsFromIndex(List<Index> index) {
        List<Integer> ids = new ArrayList<>();

        for (Index entry : index) {
            ids.add(entry.getLemma().getId());
        }

        return ids;
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

    private void validateRequest(String query, String site, int offset, int limit) {
        if (site != null) {
            if (!HttpPattern.matcher(site).matches()) {
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

    private List<Lemma> getAscendingLemmaList(List<Lemma> lemmas) {
        List<Lemma> result = new ArrayList<>(lemmas);

        for (int i = 0; i < result.size() - 1; i++) {
            for (int j = i; j < result.size() - i - 1; j++) {
                Lemma lj = result.get(j);
                Lemma ljp = result.get(j + 1);
                if (ljp.getFrequency() > lj.getFrequency()) {
                    result.set(j, ljp);
                    result.set(j + 1, lj);
                }
            }
        }

        return result;
    }

}
