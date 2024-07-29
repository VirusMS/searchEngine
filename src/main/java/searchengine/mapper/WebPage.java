package searchengine.mapper;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class WebPage {
    private Map<String, WebPage> urlList = new HashMap<>();
    private String url;
    private String parentUrl;
    private Integer pageId;
    private Integer statusCode;
    private String content;

    public WebPage(String url, Integer pageId) {
        this.url = url;
        this.pageId = pageId;
        this.parentUrl = null;
    }

    public WebPage(String url, String parentUrl, WebPage parentPage, Integer pageId) {
        this.url = url;
        this.parentUrl = parentUrl;
        urlList.put(parentUrl, parentPage);
        this.pageId = pageId;
    }

    public void addUrlToList(String url) {
        urlList.put(url, new WebPage(url, this.url, this, this.pageId));
    }

    public void addUrlToList(WebPage webpage) {
        urlList.put(webpage.getUrl(), webpage);
    }

    public String getOriginalLink() {
        if (parentUrl == null) {
            return url;
        }
        return urlList.get(parentUrl).getOriginalLink();
    }

    public WebPage getOriginalWebPage() {
        if (parentUrl != null) {
            return urlList.get(parentUrl).getOriginalWebPage();
        }
        return this;
    }

    public boolean hasLink(String url) {
        return getOriginalWebPage().hasLink(url, new ArrayList<>());
    }

    public boolean equalsLeniently(String url1, String url2) {
        if (url1.length() > 1) {
            if (url1.charAt(url1.length() - 1) != '/') {
                url1 = url1.concat("/");
            }
        }
        if (url2.length() > 1) {
            if (url2.charAt(url2.length() - 1) != '/') {
                url2 = url2.concat("/");
            }
        }
        return url1.equals(url2);
    }

    public Map<String, WebPage> getFlatUrlList() {
        Map<String, WebPage> result = new HashMap<>(urlList);

        for (WebPage webpage : urlList.values()) {
            String url = webpage.getUrl();
            if (!url.equals(getOriginalLink()) && !url.equals(parentUrl)) {
                result.putAll(webpage.getFlatUrlList(urlList));
            }
        }

        return result;
    }

    private Map<String, WebPage> getFlatUrlList(Map<String, WebPage> previousList) {
        Map<String, WebPage> prevList = new HashMap<>(previousList);
        Map<String, WebPage> result = new HashMap<>(urlList);

        for (String url : prevList.keySet()) {
            result.remove(url);
        }

        for (WebPage webpage : urlList.values()) {
            String url = webpage.getUrl();
            if (!url.equals(getOriginalLink()) && !url.equals(parentUrl) && !prevList.containsKey(url)) {
                prevList.putAll(result);
                result.putAll(webpage.getFlatUrlList(prevList));
            }
        }

        return result;
    }

    private boolean hasLink(String url, List<String> checkedUrls) {
        if (equalsLeniently(url, this.url)) {
            return true;
        }
        for (WebPage webpage : urlList.values()) {
            if (containsLeniently(webpage.getUrl(), checkedUrls) || webpage.getUrl().equals(parentUrl)) {
                continue;
            }
            if (webpage.hasLink(url, checkedUrls)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLeniently(String url, List<String> urls) {
        for (String item : urls) {
            if (equalsLeniently(url, item)) {
                return true;
            }
        }
        return false;
    }
}
