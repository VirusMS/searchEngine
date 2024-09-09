package searchengine.model;

import lombok.Data;

@Data
public class TotalStatistics {
    private int sites;
    private long pages;
    private long lemmas;
    private boolean indexing;
}
