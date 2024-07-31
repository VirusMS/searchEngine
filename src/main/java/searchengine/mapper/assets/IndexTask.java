package searchengine.mapper.assets;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import searchengine.entity.Lemma;
import searchengine.entity.Page;

import java.util.List;

@RequiredArgsConstructor
@Data
public class IndexTask {

    private final int maxSqlBatchSize;
    private final WebPage webpage;
    private final int siteId;
    private final String baseUrl;
    private final List<Lemma> lemmas;
    private final List<Page> pages;


}
