package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupRequestConfig;
import searchengine.config.SiteShort;
import searchengine.config.SitesList;
import searchengine.entity.Site;
import searchengine.entity.SiteStatus;
import searchengine.mapper.WebPage;
import searchengine.mapper.WebSiteMapper;
import searchengine.repository.SiteRepository;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingService {

    private static final int MAX_SQL_BATCH_SIZE = 5;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final SitesList siteList;

    private final JsoupRequestConfig requestConfig;

    private final SiteRepository siteRepository;

    private final JdbcTemplate jdbcTemplate;

    private ForkJoinPool taskPool = new ForkJoinPool();

    private Map<String, WebSiteMapper> taskList = new HashMap<>();

    public void startIndexing() {
        //ATTN: make sure to verify whether the website in settings contains the last slash or not. IT MUST NOT CONTAIN LAST SLASH OR ERROR

        //5. по завершении обхода изменять статус (поле status) на INDEXED;
        //6. если произошла ошибка и обход завершить не удалось, изменять статус на FAILED и вносить в поле last_error понятную информацию о произошедшей ошибке.

        //1. удалять все имеющиеся данные по этому сайту (записи из таблиц site и page);
        buildAndExecuteCleanupQueries();

        //2. создавать в таблице site новую запись со статусом INDEXING;
        createIndexingSitesEntries();

        //3. обходить все страницы, начиная с главной, добавлять их адреса, статусы и содержимое в базу данных в таблицу page;
        //4. в процессе обхода постоянно обновлять дату и время в поле status_time таблицы site на текущее;
        //Map<String, WebPage> pageList = new HashMap<>();

        for (String url : siteList.getUrlList()) {
            for (Site site : siteRepository.findAll()) {
                if (site.getUrl().equals(url)) {
                    Integer siteId = site.getId();
                    WebSiteMapper task = new WebSiteMapper(url, siteId, requestConfig);
                    taskList.put(url, task);
                    taskPool.invoke(task);
                    //pages.put(url, taskPool.invoke(new WebSiteMapper(url, siteId, requestConfig)));
                }
            }
        }

        for (Map.Entry<String, WebSiteMapper> entry : taskList.entrySet()) {
            WebPage page = entry.getValue().join();
            createIndexedPageEntries(page, entry.getKey());

            String sql = "UPDATE site SET status = 'INDEXED', status_time = '"
                    + dateFormat.format(System.currentTimeMillis()) + "' WHERE id = " + page.getPageId();
            jdbcTemplate.execute(sql);
        }
        /*System.out.println("DEBUG: initiating shutdown...");
        taskPool.shutdown();
        try {
            taskPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            System.out.println("DEBUG: shutdown done");
            for (Map.Entry<String, WebPage> entry : pages.entrySet()) {
                String url = entry.getKey();
                WebPage page = entry.getValue();
                createIndexedPageEntries(page, url);
                String sql = "UPDATE site SET status = 'INDEXED', statusTime = "
                        + dateFormat.format(System.currentTimeMillis() + " WHERE id = " + page.getPageId());
                jdbcTemplate.execute(sql);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        System.out.println("DEBUG: Indexing done!");
    }

    public void stopIndexing() {
        for (Map.Entry<String, WebSiteMapper> entry : taskList.entrySet()) {
            Optional<Site> siteInDb = siteRepository.findByUrl(entry.getKey());
            int siteId = siteInDb.isPresent() ? siteInDb.get().getId() : -1;

            String sql = "UPDATE site SET status = 'FAILED', status_time = '"
                    + dateFormat.format(System.currentTimeMillis()) + "', last_error = 'Индексация остановлена пользователем' "
                    + "WHERE id = " + siteId;
            jdbcTemplate.execute(sql);
        }

        taskList = new HashMap<>();
        taskPool.shutdownNow();
        taskPool = new ForkJoinPool();
    }

    private void buildAndExecuteCleanupQueries() {

        List<SiteShort> toRemove = siteList.getSites();
        int siteCount = toRemove.size();

        StringBuilder siteSql = new StringBuilder();
        StringBuilder pageSql = new StringBuilder();

        for (SiteShort site : toRemove) {
            String url = site.getUrl();

            Optional<Site> siteInDb = siteRepository.findByUrl(url);

            int siteId = siteInDb.isPresent() ? siteInDb.get().getId() : -1;

            if (siteId != -1) {
                pageSql.append("site_id LIKE ").append(siteId);
                if (siteCount > 0) {
                    pageSql.append(" OR ");
                } else {
                    pageSql.append(")");
                }

                siteSql.append("url LIKE '").append(url);
                if (siteCount > 0) {
                    siteSql.append("' OR ");
                } else {
                    siteSql.append("')");
                }
                siteCount--;
            }
        }

        if (!pageSql.isEmpty()) { //Both pageSql and siteSql will be empty if none were found, doesn't matter which 1 to check on
            if (pageSql.substring(pageSql.length() - 4, pageSql.length()).equals(" OR ")) {
                pageSql = new StringBuilder(pageSql.substring(0, pageSql.length() - 4)).append(")");
                siteSql = new StringBuilder(siteSql.substring(0, siteSql.length() - 4)).append(")");
            }
            jdbcTemplate.execute("DELETE FROM page WHERE (" + pageSql);
            jdbcTemplate.execute("DELETE FROM site WHERE (" + siteSql);
        }

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

    private void createIndexedPageEntries(WebPage startPage, String baseUrl) {

        Map<String, WebPage> pageList = startPage.getFlatUrlList();
        StringBuilder sql = new StringBuilder();
        int count = 0;

        for (Map.Entry<String, WebPage> entry : pageList.entrySet()) {

            WebPage webpage = entry.getValue();

            sql.append(sql.isEmpty() ? " " : ", ")
                    .append("('").append(webpage.getPageId()).append("', '").append(entry.getKey().replace(baseUrl, ""))
                    .append("', '").append(webpage.getStatusCode()).append("', '").append(webpage.getContent().replace('\'', ' ')).append("')")
                    .toString().replace('\n', ' ');

            if (++count >= MAX_SQL_BATCH_SIZE && !sql.isEmpty()) {
                System.out.println("DEBUG: current SQL request: \n" + "INSERT INTO page(site_id, path, code, content) VALUES" + sql);
                jdbcTemplate.execute("INSERT INTO page(site_id, path, code, content) VALUES" + sql);

                String updateSql = "UPDATE site SET status_time = '"
                        + dateFormat.format(System.currentTimeMillis()) + "' WHERE id = " + startPage.getPageId();
                jdbcTemplate.execute(updateSql);
                sql = new StringBuilder();
                count = 0;
            }
        }

        if (!sql.isEmpty()) {
            jdbcTemplate.execute("INSERT INTO page(site_id, path, code, content) VALUES" + sql);
        }
    }

}
