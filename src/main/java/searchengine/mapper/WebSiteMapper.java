package searchengine.mapper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.JsoupRequestConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSiteMapper extends RecursiveTask<WebPage> {

    private final WebPage webpage;

    private final JsoupRequestConfig requestConfig;

    public WebSiteMapper(String url, Integer pageId, JsoupRequestConfig requestConfig) {
        webpage = new WebPage(url, pageId);
        this.requestConfig = requestConfig;
    }

    public WebSiteMapper(WebPage webpage, JsoupRequestConfig requestConfig) {
        this.webpage = webpage;
        this.requestConfig = requestConfig;
    }


    @Override
    protected WebPage compute() {
        Map<String, WebSiteMapper> taskList = new HashMap<>();
        String originalLink = webpage.getOriginalLink();
        try {
            Thread.sleep(250);
            Connection.Response response = Jsoup.connect(webpage.getUrl())
                    .userAgent(requestConfig.getUserAgent())
                    .referrer(requestConfig.getReferrer())
                    .followRedirects(false)
                    .execute();
            Document doc = response.parse();

            webpage.setStatusCode(response.statusCode());
            webpage.setContent(Jsoup.parse(doc.html()).wholeText() //We do not need tags, we only need page text to work with l8r
                    .replace('\n', ' ')
                    .replaceAll("[^\\x20-\\x7e]", "") //Replacing non-ASCII chars.
                    .replaceAll("[' ']{2,}", " ")
                    //https://stackoverflow.com/questions/2869072/remove-non-utf-8-characters-from-xml-with-declared-encoding-utf-8-java
            );

            Elements elements = doc.select("a[href]");
            for (Element item : elements) {
                String url = parseHref(item.attr("href"), originalLink);
                if (!webpage.hasLink(url)) {
                    if (url.contains(originalLink) && !taskList.containsKey(url) && !webpage.equalsLeniently(webpage.getUrl(), url)) {
                        webpage.addUrlToList(url); // It will be replaced l8r when task is done, for now we need it in list to avoid repeating same links
                        WebSiteMapper task = new WebSiteMapper(webpage.getUrlList().get(url), requestConfig);// Don't create new branches here, stay in initial tree!
                        task.fork();
                        taskList.put(url, task);
                        System.out.println("[" + this + "]: \n  to [" + webpage + "] " + webpage.getUrl() + "\n  added [" + webpage.getUrlList().get(url) + "] " + url);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (Map.Entry<String, WebSiteMapper> entry : taskList.entrySet()) {
            WebPage webpage = entry.getValue().join();
            this.webpage.addUrlToList(webpage);
        }

        return webpage;
    }

    private String parseHref(String url, String originalLink) {

        if (url.equals("/") || isFile(url)) {
            url = originalLink;
        }
        url = parsePartialLink(url);


        if (url.indexOf('?') != -1) { // Dealing with duplicate links
            return url.substring(0, url.indexOf('?'));
        } else if (url.indexOf('#') != -1 && url.indexOf('#') != 0) {
            return url.substring(0, url.indexOf('#'));
        }
        return url;
    }

    private String parsePartialLink(String url) {

        String initialLink = webpage.getOriginalLink();

        Pattern ofExtra = Pattern.compile("^/.*$");
        Matcher matcher = ofExtra.matcher(url);
        if (matcher.find()) {
            if (initialLink.charAt(initialLink.length() - 1) == '/') {
                url = initialLink + url.substring(1);
            } else {
                url = initialLink + url;
            }
        }
        return url;
    }

    private boolean isFile(String url) {
        Pattern ofFile = Pattern.compile("^.*[.](?!htm[l]?)[^/]+$");
        Matcher matcher = ofFile.matcher(url);
        return matcher.find();
    }
}
