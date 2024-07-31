package searchengine.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import searchengine.entity.Lemma;
import searchengine.entity.Page;
import searchengine.mapper.assets.IndexTask;
import searchengine.mapper.assets.WebPage;
import searchengine.utils.SqlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinTask;

@RequiredArgsConstructor
public class WebsiteIndexMapper extends ForkJoinTask<IndexTask> {

    private final JdbcTemplate jdbcTemplate;
    private final IndexTask indexTask;

    @Override
    public IndexTask getRawResult() {
        return null;
    }

    @Override
    protected void setRawResult(IndexTask value) {

    }

    @Override
    protected boolean exec() {
            WebPage webpage = indexTask.getWebpage();

            StringBuilder sql = new StringBuilder();
            int count = 0;

            HashMap<String, Integer> pageLemmas = webpage.getLemmas();
            if (pageLemmas == null || !webpage.isDefinedCorrectly()) { //Need to figure out when that happens and why
                System.out.println("DEBUG (WebsiteIndexMapper): incorrect page is below\n" + webpage);
                return false;
            }

            for (Lemma lemma : indexTask.getLemmas()) {
                String lemmaToFind = lemma.getLemma();
                if (pageLemmas.containsKey(lemmaToFind)) {


                    Page page = findPageByUrl(indexTask.getPages(), webpage.getUrl().replace(indexTask.getBaseUrl(), ""));
                    sql.append(sql.isEmpty() ? " " : ", ")
                            .append("('").append(page.getId()).append("', '").append(lemma.getId())
                            .append("', '").append(webpage.getLemmas().get(lemmaToFind))
                            .append("')");
                }
                if (SqlUtils.sendBatchRequest(jdbcTemplate, count, indexTask.getMaxSqlBatchSize(),
                        "INSERT INTO website_index(page_id, lemma_id, lemma_rank) VALUES", sql, indexTask.getSiteId())) {
                    sql = new StringBuilder();
                    count = 0;
                } else {
                    count++;
                }
            }
            SqlUtils.sendLastRequest(jdbcTemplate, "INSERT INTO website_index(page_id, lemma_id, lemma_rank) VALUES", sql.toString());
        return true;
    }

    private Page findPageByUrl(List<Page> pages, String path) {
        for (Page page : pages) {
            if (page.getPath().equals(path)) {
                return page;
            }
        }
        return new Page();
    }
}
