package searchengine.config.assets;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchSettings {

    private int snippetRadius;
    private double maxRelativeFrequency;

}
