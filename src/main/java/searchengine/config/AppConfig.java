package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;
import searchengine.config.assets.WebRequest;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "application")
public class AppConfig {

    private boolean debug;

    private int snippetRadius;

    private double maxRelativeFrequency;

    @NestedConfigurationProperty
    private WebRequest webRequest;

    public String getUserAgent() {
        return webRequest.getUserAgent();
    }

    public String getReferrer() {
        return webRequest.getReferrer();
    }

}
