package searchengine.model.request;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class SearchRequest {

    private String query;

    private String site;

    @Builder.Default
    private Integer offset = 0;

    @Builder.Default
    private Integer limit = 20;

}
