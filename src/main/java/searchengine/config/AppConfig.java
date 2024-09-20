package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;
import searchengine.config.assets.SearchSettings;
import searchengine.config.assets.SiteShort;
import searchengine.config.assets.WebRequest;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "application")
public class AppConfig {

    private boolean debug;
    @NestedConfigurationProperty
    private SearchSettings search;
    @NestedConfigurationProperty
    private WebRequest webRequest;
    private List<SiteShort> sites;
    public String getUserAgent() {
        return webRequest.getUserAgent();
    }
    public String getReferrer() {
        return webRequest.getReferrer();
    }

    public int getSnippetRadius() {
        return search.getSnippetRadius();
    }

    public double getMaxRelativeFrequency() {
        return search.getMaxRelativeFrequency();
    }

    public List<String> getUrlList() {
        List<String> result = new ArrayList<>();
        for (SiteShort site : sites) {
            result.add(site.getUrl());
        }

        return result;
    }

}
