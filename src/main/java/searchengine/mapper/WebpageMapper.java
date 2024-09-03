package searchengine.mapper;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import searchengine.config.AppConfig;
import searchengine.mapper.assets.WebPage;

@RequiredArgsConstructor
@Component
public class WebpageMapper {

    private final AppConfig appConfig;

    public Connection.Response getWebpageResponse(String url) {

        Connection.Response response = null;
        try {
            Thread.sleep(250);
            response = Jsoup.connect(url)
                    .header("Accept-Language", "ru")
                    .userAgent(appConfig.getUserAgent())
                    .referrer(appConfig.getReferrer())
                    .followRedirects(false)
                    .execute();
        } catch (HttpStatusException e) {
            e.printStackTrace();
        // If we catch HttpStatusException, we need to additionally parse error 404?
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }

    public WebPage getWebpage(String url, Integer siteId, Connection.Response response, Document doc) {
        WebPage webpage = new WebPage(url, siteId);

        webpage.setStatusCode(response.statusCode());

        try {
//            String content = Jsoup.parse(doc.html())
//                    .wholeText()
//                    .replace('\n', ' ')
//                    //.replaceAll("[^\\x20-\\x7e]", "") //Replacing non-ASCII chars.
//                    .replaceAll(" {2,}", " ");
            String content = doc.html();

            String pageText = doc.wholeText()
                    .replace('\n', ' ')
                    .replace('\t', ' ')
                    //.replaceAll("[^\\x20-\\x7e]", "") //Replacing non-ASCII chars.
                    .replaceAll(" {2,}", " ");
            if (pageText.charAt(0) == ' ') {
                pageText = pageText.substring(1);
            }

            webpage.setTitle(doc.title());
            webpage.setContent(content);
            webpage.setLemmas(LemmaMapper.getLemmasAndCountsFromText(pageText));

            //webpage.setContent(content);
            //webpage.setLemmas(LemmaMapper.getLemmasAndCountsFromText(webpage.getContent()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return webpage;
    }

    public WebPage getWebpage(Connection.Response response, WebPage source, Document doc) {
        WebPage result = getWebpage(source.getUrl(), source.getPageId(), response, doc);

        result.setParentUrl(source.getParentUrl());
        result.setUrlList(source.getUrlList());

        return result;

    }

}
