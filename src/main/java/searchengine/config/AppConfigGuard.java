package searchengine.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import searchengine.config.assets.SiteShort;

@Component
@RequiredArgsConstructor
public class AppConfigGuard implements InitializingBean {

    private final AppConfig appConfig;

    @Override
    public void afterPropertiesSet() {
        if (appConfig.getSnippetRadius() < 1) {
            throw new IllegalArgumentException("Value of application.search.snippet-radius must be a positive number!");
        }
        if (appConfig.getMaxRelativeFrequency() < 0.0 || appConfig.getMaxRelativeFrequency() > 1.0) {
            throw new IllegalArgumentException("Value of application.search.max-relative-frequency must be a number between 0.0 and 1.0 inclusive!");
        }
        if (!sitesUrlsDefinedCorrectly()) {
            throw new IllegalArgumentException("At least one of URL values in application.sites contains a slash ('/') in the end.");
        }

    }

    private boolean sitesUrlsDefinedCorrectly() {
        for (SiteShort site: appConfig.getSites()) {
            String url = site.getUrl();
            if (url.charAt(url.length() - 1) == '/') {
                return false;
            }
        }

        return true;
    }
}
