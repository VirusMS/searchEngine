package searchengine.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchItem {

    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private String relevance;

}
