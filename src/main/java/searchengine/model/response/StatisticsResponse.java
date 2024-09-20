package searchengine.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import searchengine.model.StatisticsData;

@Data
@AllArgsConstructor
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
