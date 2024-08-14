package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import searchengine.model.request.SearchRequest;
import searchengine.model.response.IndexingResponse;
import searchengine.model.response.SearchResponse;
import searchengine.model.response.StatisticsResponse;
import searchengine.services.ApiService;
import searchengine.services.StatisticsService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    private final ApiService apiService;

    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() {
        return apiService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        return apiService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public IndexingResponse indexPage(@RequestParam String url) {
        //if website is not within configured websites, throw error. check api specifications.
        return apiService.indexPage(url);
    }

    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    /*@GetMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest request) {
        return apiService.search(request);
    }*/

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String query,
                                 @RequestParam(required = false) String site,
                                 @RequestParam(defaultValue = "0") int offset,
                                 @RequestParam(defaultValue = "20") int limit) {
        return apiService.search(query, site, offset, limit);
    }

}
