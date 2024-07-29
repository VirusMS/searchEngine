package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<SiteShort> sites;

    public List<String> getUrlList() {
        List<String> result = new ArrayList<>();
        for (SiteShort site : sites) {
            result.add(site.getUrl());
        }

        return result;
    }
}
