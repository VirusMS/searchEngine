package searchengine.model.response;

import lombok.Data;
import searchengine.model.StatisticsData;

@Data
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
