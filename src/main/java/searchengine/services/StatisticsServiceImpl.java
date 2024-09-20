package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.assets.SiteShort;
import searchengine.entity.Site;
import searchengine.model.DetailedStatisticsItem;
import searchengine.model.StatisticsData;
import searchengine.model.response.StatisticsResponse;
import searchengine.model.TotalStatistics;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    
    private final AppConfig appConfig;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        List<SiteShort> sitesList = appConfig.getSites();
        for (int i = 0; i < sitesList.size(); i++) {
            detailed.add(getDetailedStatisticsItem(sitesList.get(i)));
        }

        return new StatisticsResponse(true, new StatisticsData(getTotalStatistics(), detailed));
    }

    private DetailedStatisticsItem getDetailedStatisticsItem(SiteShort siteShort) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        Optional<Site> siteOptional = siteRepository.findByUrl(siteShort.getUrl());

        item.setName(siteShort.getName());
        item.setUrl(siteShort.getUrl());

        if (siteOptional.isPresent()) {
            Site site = siteOptional.get();

            item.setPages(pageRepository.countBySite(site));
            item.setLemmas(lemmaRepository.countBySite(site));
            item.setStatus(site.getStatus().toString());
            String error = site.getLastError() == null ? "" : site.getLastError();
            item.setError(error);
            item.setStatusTime(site.getStatusTime().getTime());
        } else {
            item.setPages(0);
            item.setLemmas(0);
            item.setStatus("NOT INDEXED");
            item.setStatusTime(System.currentTimeMillis());
            item.setError("");
        }

        return item;
    }

    private TotalStatistics getTotalStatistics() {
        TotalStatistics total = new TotalStatistics();

        total.setSites(appConfig.getSites().size());
        total.setPages(pageRepository.count());
        total.setLemmas(lemmaRepository.count());
        total.setIndexing(true);

        return total;
    }
}
