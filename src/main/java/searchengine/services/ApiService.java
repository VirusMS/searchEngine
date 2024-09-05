package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.assets.SiteShort;
import searchengine.config.SitesList;
import searchengine.entity.*;
import searchengine.exception.ApiServiceException;
import searchengine.mapper.LemmaMapper;
import searchengine.mapper.assets.SnippetIndex;
import searchengine.model.response.IndexingResponse;
import searchengine.model.response.SearchItem;
import searchengine.model.response.SearchResponse;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.DebugUtils;

import java.util.*;
import java.util.regex.Pattern;

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
    private final DebugUtils debugUtils;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingService indexingService;
    private final AppConfig appConfig;

    //  TODO: make sure startIndexing starts indexing in new thread, so one can stop it later
    public IndexingResponse startIndexing() {

        if (isIndexingInProgress(siteRepository.findAll())) {
            throw new ApiServiceException(ERR_INDEXING_ON);
        }
        indexingService.startIndexing();
        //System.out.println("DEBUG: current appConfig contains: \n\tapplication.debug: " + appConfig.isDebug() + "\n\tapplication.web-request.user-agent: " + appConfig.getUserAgent() + "\n\tapplication.web-request.referrer: " + appConfig.getReferrer());
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

    public SearchResponse search(String query, String siteToQuery, int offset, int limit) {

        validateRequest(query, siteToQuery, offset, limit);

        //System.out.println("DEBUG (search) : request = " + request);

//        1. Разбивать поисковый запрос на отдельные слова и формировать из этих слов список уникальных лемм, исключая междометия, союзы, предлоги и частицы.
        List<String> lemmasRequest = LemmaMapper.getLemmasListFromText(query);

        Optional<Site> siteOptional = siteRepository.findByUrl(siteToQuery);
        List<Site> sitesToQuery = siteOptional.isPresent() ? List.of(siteOptional.get()) : siteRepository.findAllInList(siteList.getUrlList());

        SearchResponse response = new SearchResponse();

//        Обратите внимание, что метод поиска должен учитывать, по каким сайтам происходит этот поиск — по всем или по тому, который выбран в веб-интерфейсе в
//          выпадающем списке.
        for (Site site : sitesToQuery) {
            List<SearchResponse> items = new ArrayList<>();

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
            List<Integer> lemmaIds = new ArrayList<>(lemmas.stream().map(Lemma::getId).toList());

//        4. По первой, самой редкой лемме из списка, находить все страницы, на которых она встречается. Далее искать соответствия следующей леммы из этого списка страниц,
//          а затем повторять операцию по каждой следующей лемме. Список страниц при этом на каждой итерации должен уменьшаться.
            List<Page> pages = pageRepository.findAllBySite(site);

            System.out.print("DEBUG (search): list of pages found for '" + site.getUrl() + "': ");
            for (Page page : pages) {
                System.out.print("'" + page.getPath() + "' ");
            }
            System.out.println("");

            List<Index> index = indexRepository.findByPageAndLemmaLists(pages, lemmas);

            i = pages.iterator();
            while (i.hasNext()) {
                Page page = (Page) i.next();

                List<Index> pageIndex = index.stream()
                        .filter(e -> e.getPage().getId() == page.getId())
                        .toList();

                List<Integer> lemmasIdsInIndex = index.stream()
                        .filter(e -> {
                            return e.getPage().getId() == page.getId();
                        }).map(e -> {
                            return e.getLemma().getId();
                        })
                        .toList();

                System.out.println("");

                if (lemmasIdsInIndex.isEmpty()) {
                    System.out.println("DEBUG (search): none of lemmas are in index for page '" + page.getPath() + "'. Removing that page.");
                    i.remove();
                } else {
                    for (Lemma lemma : lemmas) {
                        if (!lemmasIdsInIndex.contains(lemma.getId())) {
                            System.out.println("DEBUG (search): lemma '" + lemma.getLemma() + "' not found for page '" + page.getPath() + "'. Removing that page.");
                            i.remove();
                            break;
                        }
                    }
                }
            }

            System.out.print("  DEBUG (search): all lemmas parsed in index, current page list = ");
            response.setCount(response.getCount() + pages.size());

//        5. Для каждой страницы рассчитывать абсолютную релевантность — сумму всех rank всех найденных на странице лемм (из таблицы index), которая делится на максимальное
//          значение этой абсолютной релевантности для всех найденных страниц.
            for (Page page : pages) {
                System.out.print("'" + page.getPath() + "' ");
                List<Float> absRelevance = new ArrayList<>();

                for (Lemma lemma : lemmas) {
                    Index entry = findIndexEntryByPageAndLemma(index, page.getId(), lemma.getId());
                    absRelevance.add(entry.getRank());
                }

                float maxAbsRelevance = (float) absRelevance.stream().mapToDouble(r -> r).max().getAsDouble();
                float relativeRelevance = (float) absRelevance.stream().mapToDouble(r -> r).sum() / maxAbsRelevance;

//        7. Сниппеты — фрагменты текстов, в которых найдены совпадения, для всех страниц должны быть примерно одинаковой длины — такие, чтобы на странице с результатами
//          поиска они занимали примерно три строки. В них необходимо выделять жирным совпадения с исходным поисковым запросом. Выделение должно происходить в формате HTML
//          при помощи тега <b>. Алгоритм получения сниппета из веб-страницы реализуйте самостоятельно.

                String content = page.getContent().replaceAll("<[/]?((b|i|t|em|strong|font|a|br)(>|[ ]{1}[^>]{1,}>))", "") //These are the tags that are strictly word emphasis only
                .replaceAll("\n", " ");

                List<String> wordsFoundOnPage = LemmaMapper.getListOfWordsWithBaseForms(content, getBaseFormsFromLemmaList(lemmas));  //Key is word, Value is base form
                HashMap<Integer, String> wordPositions = new HashMap<>();

                for (String word : wordsFoundOnPage) {
                    int wordIndex = content.indexOf(word);
                    if (wordIndex != -1) {
                        wordPositions.put(content.indexOf(word), word);
                    }
                }

                List<SnippetIndex> snippetIndexList = new ArrayList<>();
                for (int pos : wordPositions.keySet()) {
                    int pl = pos == 0 ? pos : pos - 1;
                    int pr = pos + wordPositions.get(pos).length();
                    int wc = 0;
                    while (wc < appConfig.getSnippetRadius()) {
                        while (!isPartOfWord(content.charAt(pl)) && pl > 0) {
                            if (content.charAt(pl) == '>') {
                                pl++;
                                break;
                            }
                            pl--;
                        }
                        while (isPartOfWord(content.charAt(pl)) && pl > 0) {
                            pl--;
                        }
                        wc++;
                        if (content.charAt(pl) == '>') {
                            pl++;
                            break;
                        }
                    }

                    wc = 0;
                    while (wc < appConfig.getSnippetRadius()) {
                        while (!isPartOfWord(content.charAt(pr)) && pr < content.length()) {
                            if (content.charAt(pr) == '<') {
                                pr--;
                                break;
                            }
                            pr++;
                        }
                        while (isPartOfWord(content.charAt(pr)) && pr < content.length()) {
                            pr++;
                        }
                        wc++;
                        if (content.charAt(pl) == '<') {
                            pl--;
                            break;
                        }
                    }
                    snippetIndexList.add(new SnippetIndex(pl, pr));
                }

                Iterator<SnippetIndex> snippetIterator = snippetIndexList.iterator();

                while (snippetIterator.hasNext()) {
                    SnippetIndex entry = snippetIterator.next();
                    SnippetIndex collision = getSnippetCollision(entry, snippetIndexList);

                    while (collision != null) {
                        entry.mergeIndexes(collision);
                        snippetIndexList.remove(collision);
                        collision = getSnippetCollision(entry, snippetIndexList);
                    }
                }

                StringBuilder snippetBuilder = new StringBuilder();

                for (SnippetIndex entry : snippetIndexList) {
                    snippetBuilder.append("... ").append(content.substring(entry.getStart(), entry.getEnd())).append(" ... <...> ");
                }
                snippetBuilder = new StringBuilder(snippetBuilder.substring(0, snippetBuilder.length() - 7));
                for (String word : wordsFoundOnPage) {
                    int wordPos = snippetBuilder.indexOf(word);
                    if (wordPos == -1) {
                        continue;
                    }
                    snippetBuilder.replace(wordPos, wordPos + word.length(), "<b>" + word + "</b>");
                }

                response.addData(new SearchItem(site.getUrl(), site.getName(), page.getPath(), page.getTitle(), snippetBuilder.toString(), Float.toString(round(relativeRelevance, 2))));
            }
            System.out.println("");

        }

//        6. Сортировать страницы по убыванию релевантности (от большей к меньшей) и выдавать в виде списка объектов
        response.sortResultsByDescendingRelevancy();

        System.out.println("DEBUG (ApiService.search): List of responses with relevances below:");
        for (SearchItem item : response.getData()) {
            System.out.println("  " + item.getTitle() + " - " + item.getRelevance());
        }



        return response;
    }

    //https://stackoverflow.com/questions/8911356/whats-the-best-practice-to-round-a-float-to-2-decimals
    public float round(float number, int scale) {
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

    private List<String> getBaseFormsFromLemmaList(List<Lemma> lemmas) {
        List<String> result = new ArrayList<>();

        for (Lemma lemma : lemmas) {
            result.add(lemma.getLemma());
        }

        return result;
    }

    private boolean isPartOfWord(char symbol) {
        return ((symbol >= 'а' && symbol <= 'я') || (symbol >= 'А' && symbol <= 'Я') || (symbol == 'ё' || symbol == 'е')
                || (symbol >= 'a' && symbol <= 'z') || (symbol >= 'A' && symbol <= 'Z')
                || symbol == '-');
    }

    private SnippetIndex getSnippetCollision(SnippetIndex target, List<SnippetIndex> snippetIndexList) {
        int targetStart = target.getStart();
        int targetEnd = target.getEnd();

        for (SnippetIndex entry : snippetIndexList) {
            if (target.equals(entry)) {
                continue;
            }

            int entryStart = entry.getStart();
            int entryEnd = entry.getEnd();

            if ((entryStart >= targetStart && entryStart <= targetEnd) || (entryEnd >= targetStart && entryEnd <= targetEnd)) {
                return entry;
            }
        }

        return null;
    }

}
