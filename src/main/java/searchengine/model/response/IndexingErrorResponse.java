package searchengine.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IndexingErrorResponse {

    private Boolean result;

    private String error;
}
