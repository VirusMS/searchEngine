package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupRequestConfig;
import searchengine.config.SiteShort;
import searchengine.config.SitesList;
import searchengine.entity.*;
import searchengine.mapper.WebpageMapper;
import searchengine.mapper.WebsiteIndexMapper;
import searchengine.mapper.assets.IndexTask;
import searchengine.mapper.assets.WebPage;
import searchengine.mapper.WebsiteMapper;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.SqlUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingService {

    private static final int MAX_PAGE_SQL_BATCH_SIZE = 100;
    private static final int MAX_NON_PAGE_SQL_BATCH_SIZE = 500;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //TODO: solve last slash in website addresses. There must be none at the end for the program to work properly
    private final SitesList siteList;

    private final JsoupRequestConfig requestConfig;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WebpageMapper webpageMapper;

    private ForkJoinPool taskPool = new ForkJoinPool();

    private Map<String, WebsiteMapper> websiteMapperTaskList = new HashMap<>();

    //private Map<String, WebsiteIndexMapper> websiteIndexMapperTaskList = new HashMap<>();

    public void startIndexing() {
        //ATTN: make sure to verify whether the website in settings contains the last slash or not. IT MUST NOT CONTAIN LAST SLASH OR ERROR

        //1. удалять все имеющиеся данные по этому сайту (записи из таблиц site и page);
        buildAndExecuteCleanupQueries();

        //2. создавать в таблице site новую запись со статусом INDEXING;
        createIndexingSitesEntries();

        //3. обходить все страницы, начиная с главной, добавлять их адреса, статусы и содержимое в базу данных в таблицу page;
        //4. в процессе обхода постоянно обновлять дату и время в поле status_time таблицы site на текущее;
        //Map<String, WebPage> pageList = new HashMap<>();
        indexPages();
    }

    public void stopIndexing() {
        for (Map.Entry<String, WebsiteMapper> entry : websiteMapperTaskList.entrySet()) {
            Optional<Site> siteInDb = siteRepository.findByUrl(entry.getKey());
            int siteId = siteInDb.isPresent() ? siteInDb.get().getId() : -1;

            String sql = "UPDATE site SET status = 'FAILED', status_time = '"
                    + dateFormat.format(System.currentTimeMillis()) + "', last_error = 'Индексация остановлена пользователем' "
                    + "WHERE id = " + siteId;
            jdbcTemplate.execute(sql);
        }

        websiteMapperTaskList = new HashMap<>();
        //websiteIndexMapperTaskList = new HashMap<>();
        taskPool.shutdownNow();
        taskPool = new ForkJoinPool();
    }

    public void indexSinglePage(SiteShort website, String pageUrl) {
        String siteUrl = website.getUrl();
        String siteName = website.getName();

        Optional<Site> siteInDb = siteRepository.findByUrl(siteUrl);

        Site site;
        if (siteInDb.isPresent()) {
            site = siteInDb.get();
        } else {
            String siteSql = "INSERT INTO site(status, last_error, status_time, url, name) VALUES ('"
                    + SiteStatus.INDEXED + "', NULL, '" + dateFormat.format(System.currentTimeMillis()) + "', '" + siteUrl + "', '" + siteName + "')";
            jdbcTemplate.execute(siteSql);
            site = siteRepository.findByUrl(siteUrl).get();
        }

        String path = pageUrl.substring(pageUrl.indexOf(site.getUrl()) + 1);
        Optional<Page> pageInDb = pageRepository.findByPath(path);
        WebPage webpage = webpageMapper.getWebpage(path, site.getId(), webpageMapper.getWebpageResponse(pageUrl));

        if (pageInDb.isPresent()) {
            Page page = pageInDb.get();
            List<Index> indexList = indexRepository.findAllByPage(page);
            List<Lemma> lemmaList = getLemmasAvailableInIndex(indexList, lemmaRepository.findAllBySite(site));

            updateLemmasInDb(getLemmasAvailableInIndex(indexList, lemmaList));
            jdbcTemplate.execute("UPDATE page SET site = "
                    + webpage.getPageId() + ", status_code = "
                    + webpage.getStatusCode() + ", content = '"
                    + webpage.getContent() + "'");
            jdbcTemplate.execute("DELETE FROM website_index WHERE page_id = " + page.getId());
        } else {
            jdbcTemplate.execute("INSERT INTO page(site_id, path, code, content) VALUES ("
                            + site.getId() + ", '"
                            + path + "', "
                            + webpage.getStatusCode() + ", '"
                            + webpage.getContent() + "')"
            );
        }


        Page page = pageRepository.findByPath(path).get();

        HashMap<String, Integer> lemmasFound = webpage.getLemmas();
        List<Lemma> lemmasInDb = lemmaRepository.findAllBySite(site);

        StringBuilder insertSql = new StringBuilder();
        StringBuilder updateSql = new StringBuilder();

        Integer count = 0;

        for (String lemma : lemmasFound.keySet()) {

            Lemma lemmaFromDb = getLemmaByBaseForm(lemmasInDb, lemma);

            if (lemmaFromDb != null) {
                updateSql = updateSql.isEmpty() ? updateSql.append(" id = ") : updateSql.append(" OR id = ");
                updateSql.append(lemmaFromDb.getId());
            } else {
                insertSql = insertSql.isEmpty() ? insertSql.append(" ") : insertSql.append(", ");
                insertSql.append("(").append(site.getId()).append(", '").append(lemma).append("', 1)");
            }

            if (SqlUtils.sendBatchRequest(jdbcTemplate, count, MAX_NON_PAGE_SQL_BATCH_SIZE, "UPDATE lemma SET frequency = frequency + 1 WHERE",
                    updateSql, site.getId())) {
                SqlUtils.sendBatchRequest(jdbcTemplate, count, MAX_NON_PAGE_SQL_BATCH_SIZE, "INSERT INTO lemma(site_id, lemma, frequency) VALUES",
                        insertSql, site.getId());
                System.out.println("DEBUG (indexSinglePage): batch requests sent, table 'lemma'");
                insertSql = new StringBuilder();
                updateSql = new StringBuilder();
                count = 0;
            } else {
                count++;
            }
        }

        SqlUtils.sendLastRequest(jdbcTemplate,"INSERT INTO lemma(site_id, lemma, frequency) VALUES", insertSql);
        SqlUtils.sendLastRequest(jdbcTemplate,"UPDATE lemma SET frequency = frequency + 1 WHERE", updateSql);

        WebsiteIndexMapper task = new WebsiteIndexMapper(jdbcTemplate, new IndexTask(
                MAX_NON_PAGE_SQL_BATCH_SIZE,
                webpage,
                site.getId(),
                siteUrl,
                lemmaRepository.findAllBySite(site),
                List.of(page)
        ));
        taskPool.invoke(task);
    }

    private Lemma getLemmaByBaseForm(List<Lemma> lemmaList, String baseForm) {
        for (Lemma lemma : lemmaList) {
            if (lemma.getLemma().equals(baseForm)) {
                return lemma;
            }
        }

        return null;
    }

    private List<Lemma> getLemmasAvailableInIndex(List<Index> indexList, List<Lemma> lemmaList) {
        List<Lemma> result = new ArrayList<>();

        for (Lemma lemma : lemmaList) {
            if (indexListContainsLemma(indexList, lemma)) {
                result.add(lemma);
            }
        }

        return result;
    }

    private boolean indexListContainsLemma(List<Index> indexList, Lemma lemma) {
        for (Index index : indexList) {
            if (index.getLemma().getId() == lemma.getId()) {
                return true;
            }
        }

        return false;
    }

    private void updateLemmasInDb(List<Lemma> lemmas) {
        StringBuilder deleteSql = new StringBuilder();
        StringBuilder updateSql = new StringBuilder();

        for (Lemma lemma : lemmas) {
            if (lemma.getFrequency() == 1) {
                deleteSql = deleteSql.isEmpty() ? deleteSql.append(" id = ") : deleteSql.append(" OR id = ");
                deleteSql.append(lemma.getId());
            } else { //String sql = "UPDATE Lemma SET frequency = frequency - 1 WHERE id = n OR id = n OR ...
                updateSql = updateSql.isEmpty() ? updateSql.append(" id = ") : updateSql.append(" OR id = ");
                updateSql.append(lemma.getId());
            }
        }

        jdbcTemplate.execute("DELETE FROM lemma WHERE" + deleteSql);
        jdbcTemplate.execute("UPDATE lemma SET frequency = frequency - 1 WHERE" + updateSql);
    }

    private void indexPages() {
        for (String url : siteList.getUrlList()) {
            for (Site site : siteRepository.findAll()) {
                if (site.getUrl().equals(url)) {
                    Integer siteId = site.getId();
                    WebsiteMapper task = new WebsiteMapper(url, siteId, requestConfig);
                    websiteMapperTaskList.put(url, task);
                    taskPool.invoke(task);
                    //pages.put(url, taskPool.invoke(new WebSiteMapper(url, siteId, requestConfig)));
                }
            }
        }

        //5. по завершении обхода изменять статус (поле status) на INDEXED;
        for (Map.Entry<String, WebsiteMapper> entry : websiteMapperTaskList.entrySet()) {
            WebPage page = entry.getValue().join();
            createIndexedPagesAndLemmasEntries(page, entry.getKey());

            String sql = "UPDATE site SET status = 'INDEXED', status_time = '"
                    + dateFormat.format(System.currentTimeMillis()) + "' WHERE id = " + page.getPageId();
            jdbcTemplate.execute(sql);
        }
        //6. если произошла ошибка и обход завершить не удалось, изменять статус на FAILED и вносить в поле last_error понятную информацию о произошедшей ошибке.
        System.out.println("DEBUG: Indexing done!");
    }

    private void buildAndExecuteCleanupQueries() {

        List<SiteShort> toRemove = siteList.getSites();
        int siteCount = toRemove.size();

        StringBuilder siteSql = new StringBuilder();
        StringBuilder pageAndLemmaSql = new StringBuilder();
        StringBuilder indexSql = new StringBuilder();

        for (SiteShort site : toRemove) {
            String url = site.getUrl();

            Optional<Site> siteInDb = siteRepository.findByUrl(url);

            int siteId = siteInDb.isPresent() ? siteInDb.get().getId() : -1;

            if (siteId != -1) {
                pageAndLemmaSql.append("site_id = ").append(siteId);
                siteSql.append("url LIKE '").append(url);

                indexSql = appendPageInfoToIndexSql(siteInDb.get(), indexSql);

                if (siteCount > 0) {
                    pageAndLemmaSql.append(" OR ");
                    siteSql.append("' OR ");
                    indexSql.append(" OR ");
                } else {
                    pageAndLemmaSql.append(")");
                    siteSql.append("')");
                    indexSql.append(")");
                }

                siteCount--;
            }
        }

        if (!pageAndLemmaSql.isEmpty()) { //Both pageSql and siteSql will be empty if none were found, doesn't matter which 1 to check on
            if (pageAndLemmaSql.substring(pageAndLemmaSql.length() - 4, pageAndLemmaSql.length()).equals(" OR ")) {
                pageAndLemmaSql = new StringBuilder(pageAndLemmaSql.substring(0, pageAndLemmaSql.length() - 4)).append(")");
                siteSql = new StringBuilder(siteSql.substring(0, siteSql.length() - 4)).append(")");
                indexSql = new StringBuilder(indexSql.substring(0, indexSql.length() - 4)).append(")");
            }
            jdbcTemplate.execute("DELETE FROM website_index WHERE (" + pageAndLemmaSql);
            jdbcTemplate.execute("DELETE FROM lemma WHERE (" + pageAndLemmaSql);
            jdbcTemplate.execute("DELETE FROM page WHERE (" + pageAndLemmaSql);
            jdbcTemplate.execute("DELETE FROM site WHERE (" + siteSql);
        }

    }

    private StringBuilder appendPageInfoToIndexSql(Site site, StringBuilder sql) {
        List<Page> pages = pageRepository.findAllBySite(site);
        StringBuilder result = new StringBuilder(sql);

        for (Page page : pages) {
            result.append("page_id = ").append(page.getId()).append(" OR ");
        }

        return result;
    }

    private void createIndexingSitesEntries() {
        StringBuilder sql = new StringBuilder();
        for (SiteShort site : siteList.getSites()) {
            String url = site.getUrl();
            String name = site.getName();

            sql.append(sql.isEmpty() ? " " : ", ")
                    .append("('").append(SiteStatus.INDEXING).append("', NULL, '").append(dateFormat.format(System.currentTimeMillis()))
                    .append("', '").append(url).append("', '").append(name).append("')");
        }

        sql = new StringBuilder("INSERT INTO site(status, last_error, status_time, url, name) VALUES" + sql);
        jdbcTemplate.execute(sql.toString());
    }

    private void createIndexedPagesAndLemmasEntries(WebPage startPage, String baseUrl) {

        Map<String, WebPage> pageList = startPage.getFlatUrlList();
        StringBuilder pageSql = new StringBuilder();
        StringBuilder lemmaSql = new StringBuilder();
        int pageCount = 0;
        int lemmaCount = 0;
        int startPageId = startPage.getPageId();

        for (Map.Entry<String, WebPage> webpageEntry : pageList.entrySet()) {

            WebPage webpage = webpageEntry.getValue();
            if (!webpage.isDefinedCorrectly()) {    //This is a failsafe for now. Reasonably, we need to parse errors that cause this
                System.out.println("DEBUG (createIndexedPagesAndLemmasEntries): incorrect page is below\n" + webpage);
                continue;
            }
            Integer pageId = webpage.getPageId();

            String webpageUrl = webpageEntry.getKey();
            pageSql.append(pageSql.isEmpty() ? " " : ", ")
                    .append("('").append(pageId).append("', '")
                    .append(webpageUrl.equals(baseUrl) ? "/" : webpageUrl.replace(baseUrl, ""))
                    .append("', '").append(webpage.getStatusCode()).append("', '")
                    .append(webpage.getContent() == null ? ' ' : webpage.getContent().replace('\'', ' '))
                    .append("')")
                    .toString().replace('\n', ' ');

            if (SqlUtils.sendBatchRequest(jdbcTemplate, pageCount, MAX_PAGE_SQL_BATCH_SIZE, "INSERT INTO page(site_id, path, code, content) VALUES",
                                pageSql, startPageId)) {
                System.out.println("DEBUG (createIndexedPagesAndLemmasEntries): batch request sent, table 'page'");
                pageSql = new StringBuilder();
                pageCount = 0;
            } else {
                pageCount++;
            }

            Map<String, Integer> lemmaFrequencies = webpage.getLemmasFrequencyList();
            for (Map.Entry<String, Integer> lemmaEntry : lemmaFrequencies.entrySet() ) {
                lemmaSql.append(lemmaSql.isEmpty() ? " " : ", ")
                        .append("('").append(pageId).append("', '")
                        .append(lemmaEntry.getKey()).append("', '")
                        .append(lemmaEntry.getValue()).append("')");

                if (SqlUtils.sendBatchRequest(jdbcTemplate, lemmaCount, MAX_NON_PAGE_SQL_BATCH_SIZE, "INSERT INTO lemma(site_id, lemma, frequency) VALUES",
                                    lemmaSql, startPageId)) {
                    System.out.println("DEBUG (createIndexedPagesAndLemmasEntries): batch request sent, table 'lemma'");
                    lemmaSql = new StringBuilder();
                    lemmaCount = 0;
                } else {
                    lemmaCount++;
                }
            }
        }

        SqlUtils.sendLastRequest(jdbcTemplate,"INSERT INTO page(site_id, path, code, content) VALUES", pageSql);
        SqlUtils.sendLastRequest(jdbcTemplate, "INSERT INTO lemma(site_id, lemma, frequency) VALUES", lemmaSql);
        System.out.println("DEBUG (createIndexedPagesAndLemmasEntries): last batch requests sent, tables 'page', 'lemma'");

        createIndexEntries(startPage, baseUrl); //This must be done AFTER all lemmas are added
    }

    private void createIndexEntries(WebPage startPage, String baseUrl) {
        Map<String, WebPage> pageList = startPage.getFlatUrlList();
        Integer siteId = startPage.getPageId();

        Site site = siteRepository.findById(siteId).orElse(null);//Site should not be null here, may need extra check.
        List<Lemma> lemmas = lemmaRepository.findAllBySite(site);
        List<Page> pages = pageRepository.findAllBySite(site);

        for (WebPage webpage : pageList.values()) {
            WebsiteIndexMapper task = new WebsiteIndexMapper(jdbcTemplate, new IndexTask(
                    MAX_NON_PAGE_SQL_BATCH_SIZE,
                    webpage,
                    siteId,
                    baseUrl,
                    lemmas,
                    pages
            ));

            //websiteIndexMapperTaskList.put(webpage.getUrl(), task);
            taskPool.invoke(task);
        }
    }

}
