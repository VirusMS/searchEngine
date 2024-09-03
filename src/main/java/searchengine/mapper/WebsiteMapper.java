package searchengine.mapper;

import org.jsoup.Connection;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.AppConfig;
import searchengine.mapper.assets.WebPage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebsiteMapper extends RecursiveTask<WebPage> {

    private final WebPage webpage;

    private final AppConfig appConfig;

    private final WebpageMapper webpageMapper;

    public WebsiteMapper(String url, Integer pageId, AppConfig requestConfig) {
        webpage = new WebPage(url, pageId);
        this.appConfig = requestConfig;
        webpageMapper = new WebpageMapper(requestConfig);
    }

    public WebsiteMapper(WebPage webpage, AppConfig requestConfig) {
        this.webpage = webpage;
        this.appConfig = requestConfig;
        webpageMapper = new WebpageMapper(requestConfig);
    }

    //TODO: resolve java.net.SocketTimeoutException: Read timed out exceptions.
    //TODO: resolve java.net.ConnectException: Connection timed out: getsockopt - possible exception, prolly website thinks I am DDoS attacking them
    //TODO: handle cases when Connection.Response from WebpageMapper is null, if any
    //TODO: handle java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 500163
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
            /*webpage.setStatusCode(response.statusCode());
            String content = Jsoup.parse(doc.html()).wholeText() //We do not need tags, we only need page text to work with l8r
                    .replace('\n', ' ')
                    //.replaceAll("[^\\x20-\\x7e]", "") //Replacing non-ASCII chars.
                    .replaceAll(" {2,}", " ");
            if (content.charAt(0) == ' ') {
                content = content.substring(1);
            }
            webpage.setContent(content);
            webpage.setLemmas(LemmaMapper.getLemmasFromText(webpage.getContent()));*/

            Elements elements = doc.select("a[href]");
            for (Element item : elements) {
                String url = parseHref(item.attr("href"), originalLink);
                if (!webpage.hasLink(url)) {
                    if (url.contains(originalLink) && !taskList.containsKey(url) && !webpage.equalsLeniently(webpage.getUrl(), url)) {
                        webpage.addUrlToList(url); // It will be replaced l8r when task is done, for now we need it in list to avoid repeating same links
                        WebsiteMapper task = new WebsiteMapper(webpage.getUrlList().get(url), appConfig);// Don't create new branches here, stay in initial tree!
                        task.fork();
                        taskList.put(url, task);
                        System.out.println("DEBUG (WebsiteMapper): [" + this + "]: \n  to [" + webpage + "] " + webpage.getUrl() + "\n  added [" + webpage.getUrlList().get(url) + "] " + url);
                    }
                }
            }
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
