package searchengine.mapper;

import org.jsoup.Connection;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.AppConfig;
import searchengine.mapper.assets.WebPage;
import searchengine.utils.DebugUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebsiteMapper extends RecursiveTask<WebPage> {

    private final WebPage webpage;
    private final AppConfig appConfig;
    private final WebpageMapper webpageMapper;
    private final DebugUtils debugUtils;

    public WebsiteMapper(String url, Integer pageId, AppConfig appConfig) {
        webpage = new WebPage(url, pageId);
        this.appConfig = appConfig;
        webpageMapper = new WebpageMapper(appConfig);
        debugUtils = new DebugUtils(appConfig);
    }

    public WebsiteMapper(WebPage webpage, AppConfig appConfig) {
        this.webpage = webpage;
        this.appConfig = appConfig;
        webpageMapper = new WebpageMapper(appConfig);
        debugUtils = new DebugUtils(appConfig);
    }

    //TODO: resolve java.net.SocketTimeoutException: Read timed out exceptions.
    //TODO: resolve java.net.ConnectException: Connection timed out: getsockopt - possible exception, prolly website thinks I am DDoS attacking them
    //TODO: handle cases when Connection.Response from WebpageMapper is null, if any
    @Override
    protected WebPage compute() {
        Map<String, WebsiteMapper> taskList = new HashMap<>();
        String originalLink = webpage.getOriginalLink();

        try {

            Connection.Response response = webpageMapper.getWebpageResponse(webpage.getUrl());
            Document doc = response.parse();

            if (response.statusCode() == 302) {
                return null;
            }

            webpage.clone(webpageMapper.getWebpage(response, webpage, doc));

            Elements elements = doc.select("a[href]");
            for (Element item : elements) {
                String url = parseHref(item.attr("href"), originalLink);
                if (!webpage.hasLink(url)) {
                    if (url.contains(originalLink) && !taskList.containsKey(url) && !webpage.equalsLeniently(webpage.getUrl(), url)) {
                        webpage.addUrlToList(url);
                        WebsiteMapper task = new WebsiteMapper(webpage.getUrlList().get(url), appConfig);
                        task.fork();
                        taskList.put(url, task);
                        debugUtils.println("DEBUG (WebsiteMapper): [" + this + "]: \n  to [" + webpage + "] " + webpage.getUrl() + "\n  added [" + webpage.getUrlList().get(url) + "] " + url);
                    }
                }
            }
        } catch (NullPointerException e) {
        } catch (Exception e) {
            e.printStackTrace();

        }

        for (Map.Entry<String, WebsiteMapper> entry : taskList.entrySet()) {
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


        if (url.indexOf('?') != -1) {
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
