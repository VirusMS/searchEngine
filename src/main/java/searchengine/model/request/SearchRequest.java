package searchengine.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchRequest {

    private String query;

    private String site;

    @Builder.Default
    private Integer offset = 0;

    @Builder.Default
    private Integer limit = 20;

}
