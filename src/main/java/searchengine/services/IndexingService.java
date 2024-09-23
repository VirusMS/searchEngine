package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.assets.SiteShort;
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
import searchengine.utils.DebugUtils;
import searchengine.utils.SqlUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingService {

    private static final int MAX_PAGE_SQL_BATCH_SIZE = 100;
    private static final int MAX_NON_PAGE_SQL_BATCH_SIZE = 10000;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final AppConfig appConfig;
    private final DebugUtils debugUtils;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WebpageMapper webpageMapper;

    private ForkJoinPool taskPool = new ForkJoinPool();
    private Map<String, WebsiteMapper> websiteMapperTaskList = new HashMap<>();

    public void startIndexing() {
        buildAndExecuteCleanupQueries();
        createIndexingSitesEntries();
        indexPages();
    }

    public void stopIndexing() {
        for (Map.Entry<String, WebsiteMapper> entry : websiteMapperTaskList.entrySet()) {
            Optional<Site> siteInDb = siteRepository.findByUrl(entry.getKey());
            int siteId = siteInDb.isPresent() ? siteInDb.get().getId() : -1;

            String sql = "UPDATE site SET status = '"+ SiteStatus.FAILED + "', status_time = '"
                    + dateFormat.format(System.currentTimeMillis()) + "', last_error = 'Индексация остановлена пользователем' "
                    + "WHERE id = " + siteId;
            jdbcTemplate.execute(sql);
        }

        websiteMapperTaskList = new HashMap<>();
        taskPool.shutdownNow();
        taskPool = new ForkJoinPool();
    }

    public void indexSinglePage(SiteShort website, String pageUrl) {
        try {
            Site site = getSiteFromDb(website);
            String path = pageUrl.substring(pageUrl.indexOf(site.getUrl()) + 1);

            Connection.Response response = webpageMapper.getWebpageResponse(pageUrl);
            WebPage webpage = webpageMapper.getWebpage(path, site.getId(), response, response.parse());

            updatePageInDb(site, webpage, path);
            Page page = pageRepository.findByPath(path).get();
            updatePageLemmasInDb(site, webpage);

            WebsiteIndexMapper task = new WebsiteIndexMapper(jdbcTemplate, new IndexTask(
                    MAX_NON_PAGE_SQL_BATCH_SIZE,
                    webpage,
                    site.getId(),
                    site.getUrl(),
                    lemmaRepository.findAllBySite(site),
                    List.of(page)
            ), debugUtils);
            taskPool.invoke(task);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelWebsiteMapperTask(String url, Class clazz) {
        WebsiteMapper toCancel = websiteMapperTaskList.get(url);
        if (toCancel == null) {
            debugUtils.println("DEBUG (IndexingService.cancelWebsiteMapperTask): toCancel = null, this is not expected behaviour. Need to investigate. URL: " + url);
            return;
        }
        websiteMapperTaskList.remove(url);

        WebPage page = toCancel.getOriginalWebpage();
        toCancel.cancel(true);
        String errorMessage;
        if (clazz == SocketTimeoutException.class) {
            errorMessage = "Таймаут чтения одной из страниц сайта. Невозможно получить данные с сайта для индексации";
        } else if (clazz == ConnectException.class) {
            errorMessage = "Проблема при соединении с сайтом. Возможно, сайт заблокировал запросы поискового движка.";
        } else {
            errorMessage = "Ошибка при взаимодействии с сайтом";
        }

        String sql = "UPDATE site SET status = '" + SiteStatus.FAILED + "', status_time = '"
                + dateFormat.format(System.currentTimeMillis()) + "', last_error = '" + errorMessage + "' WHERE id = " + page.getPageId();
        jdbcTemplate.execute(sql);
    }

    private Site getSiteFromDb(SiteShort website) {
        String url = website.getUrl();
        String name = website.getName();
        Optional<Site> siteInDb = siteRepository.findByUrl(url);
        Site site;

        if (siteInDb.isPresent()) {
            site = siteInDb.get();
        } else {
            String siteSql = "INSERT INTO site(status, last_error, status_time, url, name) VALUES ('"
                    + SiteStatus.INDEXED + "', NULL, '" + dateFormat.format(System.currentTimeMillis()) + "', '" + url + "', '" + name + "')";
            jdbcTemplate.execute(siteSql);
            site = siteRepository.findByUrl(url).get();
        }

        return site;
    }

    private void updatePageInDb(Site site, WebPage webpage, String path) {
        Optional<Page> pageInDb = pageRepository.findByPath(path);

        if (pageInDb.isPresent()) {
            Page page = pageInDb.get();
            List<Index> indexList = indexRepository.findByPage(page);
            List<Lemma> lemmaList = getLemmasAvailableInIndex(indexList, lemmaRepository.findAllBySite(site));

            updateLemmasInDb(getLemmasAvailableInIndex(indexList, lemmaList));
            jdbcTemplate.execute("UPDATE page SET site = "
                    + webpage.getPageId() + ", status_code = "
                    + webpage.getStatusCode() + ", content = '"
                    + webpage.getContent() + "' WHERE id = " + page.getId());
            jdbcTemplate.execute("DELETE FROM website_index WHERE page_id = " + page.getId());
        } else {
            jdbcTemplate.execute("INSERT INTO page(site_id, path, code, title, content) VALUES ("
                    + site.getId() + ", '"
                    + path + "', "
                    + webpage.getStatusCode() + ", '"
                    + webpage.getTitle() + "', '"
                    + webpage.getContent() + "')"
            );
        }
    }

    private void updatePageLemmasInDb(Site site, WebPage webpage) {
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
                debugUtils.println("DEBUG (indexSinglePage): batch requests sent, table 'lemma'");
                insertSql = new StringBuilder();
                updateSql = new StringBuilder();
                count = 0;
            } else {
                count++;
            }
        }

        SqlUtils.sendLastRequest(jdbcTemplate, "INSERT INTO lemma(site_id, lemma, frequency) VALUES", insertSql);
        SqlUtils.sendLastRequest(jdbcTemplate, "UPDATE lemma SET frequency = frequency + 1 WHERE", updateSql);
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
            } else {
                updateSql = updateSql.isEmpty() ? updateSql.append(" id = ") : updateSql.append(" OR id = ");
                updateSql.append(lemma.getId());
            }
        }

        jdbcTemplate.execute("DELETE FROM lemma WHERE" + deleteSql);
        jdbcTemplate.execute("UPDATE lemma SET frequency = frequency - 1 WHERE" + updateSql);
    }

    private void indexPages() {
        for (String url : appConfig.getUrlList()) {
            for (Site site : siteRepository.findAll()) {
                if (site.getUrl().equals(url)) {
                    Integer siteId = site.getId();
                    WebsiteMapper task = new WebsiteMapper(url, siteId, appConfig, this);
                    try {
                        websiteMapperTaskList.put(url, task);
                        taskPool.invoke(task);
                    } catch (CancellationException e) {
                    }
                }
            }
        }

        for (Map.Entry<String, WebsiteMapper> entry : websiteMapperTaskList.entrySet()) {
            WebPage page = entry.getValue().join();
            createIndexedPagesAndLemmasEntries(page, entry.getKey());

            String sql = "UPDATE site SET status = '" + SiteStatus.INDEXED + "', status_time = '"
                    + dateFormat.format(System.currentTimeMillis()) + "' WHERE id = " + page.getPageId();
            jdbcTemplate.execute(sql);
        }

        debugUtils.println("DEBUG (indexPages): Indexing done!");
    }

    private void buildAndExecuteCleanupQueries() {

        List<SiteShort> toRemove = appConfig.getSites();
        int siteCount = toRemove.size();

        StringBuilder siteSql = new StringBuilder();
        StringBuilder pageAndLemmaSql = new StringBuilder();
        //StringBuilder indexSql = new StringBuilder();
        List<Page> pagesToRemove = new ArrayList<>();

        for (SiteShort site : toRemove) {
            String url = site.getUrl();

            Optional<Site> siteOptional = siteRepository.findByUrl(url);
            Site siteFromDb = siteOptional.isPresent() ? siteOptional.get() : null;

            if (siteFromDb != null) {
                int siteId = siteFromDb.getId();

                pageAndLemmaSql.append(siteId);
                siteSql.append("url LIKE '").append(url);

                //indexSql = appendPageInfoToIndexSql(siteOptional.get(), indexSql);

                if (siteCount > 0) {
                    pageAndLemmaSql.append(", ");
                    siteSql.append("' OR ");
                } else {
                    pageAndLemmaSql.append(")");
                    siteSql.append("')");
                }

                siteCount--;
            }
        }

        if (!pageAndLemmaSql.isEmpty()) {
            if (pageAndLemmaSql.substring(pageAndLemmaSql.length() - 2, pageAndLemmaSql.length()).equals(", ")) {
                pageAndLemmaSql = new StringBuilder(pageAndLemmaSql.substring(0, pageAndLemmaSql.length() - 2)).append(")");
                siteSql = new StringBuilder(siteSql.substring(0, siteSql.length() - 4)).append(")");
                //indexSql = new StringBuilder(indexSql.substring(0, indexSql.length() - 4)).append(")");
            }
            jdbcTemplate.execute("TRUNCATE TABLE website_index");
            //jdbcTemplate.execute("DELETE FROM website_index WHERE page_id IN (" + indexSql);
            jdbcTemplate.execute("DELETE FROM lemma WHERE site_id IN (" + pageAndLemmaSql);
            jdbcTemplate.execute("DELETE FROM page WHERE site_id IN (" + pageAndLemmaSql);
            jdbcTemplate.execute("DELETE FROM site WHERE (" + siteSql);
        }

    }

    private void createIndexingSitesEntries() {
        StringBuilder sql = new StringBuilder();
        for (SiteShort site : appConfig.getSites()) {
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
        int startPageId = startPage.getPageId();

        for (Map.Entry<String, WebPage> webpageEntry : pageList.entrySet()) {

            WebPage webpage = webpageEntry.getValue();
            if (!webpage.isDefinedCorrectly()) {    //This is a failsafe, this should not be an issue at all
                debugUtils.println("DEBUG (createIndexedPagesAndLemmasEntries): incorrect page is below\n  " + webpage);
                continue;
            }

            pageSql = sendPageInsertRequests(webpageEntry, baseUrl, startPageId, pageSql);
            lemmaSql = sendLemmaInsertRequests(webpage.getLemmasFrequencyList(), webpage.getPageId(), startPageId, lemmaSql);
        }

        SqlUtils.sendLastRequest(jdbcTemplate,"INSERT INTO page(site_id, path, code, title, content) VALUES", pageSql);
        SqlUtils.sendLastRequest(jdbcTemplate, "INSERT INTO lemma(site_id, lemma, frequency) VALUES", lemmaSql);
        debugUtils.println("DEBUG (createIndexedPagesAndLemmasEntries): last batch requests sent, tables 'page', 'lemma'");

        createIndexEntries(startPage, baseUrl);
    }

    private StringBuilder sendPageInsertRequests(Map.Entry<String, WebPage> entry, String baseUrl, int startPageId, StringBuilder lastSql) {
        StringBuilder sql = new StringBuilder(lastSql);
        int count = 0;
        WebPage webpage = entry.getValue();
        int pageId = webpage.getPageId();
        String webpageUrl = entry.getKey();

        sql.append(sql.isEmpty() ? " " : ", ")
                .append("('").append(pageId).append("', '")
                .append(webpageUrl.equals(baseUrl) ? "/" : webpageUrl.replace(baseUrl, "")).append("', '")
                .append(webpage.getStatusCode()).append("', '")
                .append(webpage.getTitle()).append("', '")
                .append(webpage.getContent() == null ? ' ' : webpage.getContent().replace('\'', ' ')).append("')")
                .toString().replace('\n', ' ');

        if (SqlUtils.sendBatchRequest(jdbcTemplate, count, MAX_PAGE_SQL_BATCH_SIZE, "INSERT INTO page(site_id, path, code, title, content) VALUES",
                sql, startPageId)) {
            debugUtils.println("DEBUG (createIndexedPagesAndLemmasEntries): batch request sent, table 'page'");
            sql = new StringBuilder();
            count = 0;
        } else {
            count++;
        }

        return sql;
    }

    private StringBuilder sendLemmaInsertRequests(Map<String, Integer> lemmaFrequencies, int pageId, int startPageId, StringBuilder lastSql) {
        StringBuilder sql = new StringBuilder(lastSql);
        int count = 0;

        for (Map.Entry<String, Integer> lemmaEntry : lemmaFrequencies.entrySet() ) {
            sql.append(sql.isEmpty() ? " " : ", ")
                    .append("('").append(pageId).append("', '")
                    .append(lemmaEntry.getKey()).append("', '")
                    .append(lemmaEntry.getValue()).append("')");

            if (SqlUtils.sendBatchRequest(jdbcTemplate, count, MAX_NON_PAGE_SQL_BATCH_SIZE, "INSERT INTO lemma(site_id, lemma, frequency) VALUES",
                    sql, startPageId)) {
                debugUtils.println("DEBUG (createIndexedPagesAndLemmasEntries): batch request sent, table 'lemma'");
                sql = new StringBuilder();
                count = 0;
            } else {
                count++;
            }
        }

        return sql;
    }

    private void createIndexEntries(WebPage startPage, String baseUrl) {
        Map<String, WebPage> pageList = startPage.getFlatUrlList();
        Integer siteId = startPage.getPageId();

        Site site = siteRepository.findById(siteId).orElse(null);
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
            ), debugUtils);

            taskPool.invoke(task);
        }
    }

}
