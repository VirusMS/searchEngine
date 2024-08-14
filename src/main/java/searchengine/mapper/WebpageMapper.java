package searchengine.mapper;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import searchengine.config.JsoupRequestConfig;
import searchengine.mapper.assets.WebPage;

@RequiredArgsConstructor
@Component
public class WebpageMapper {

    private final JsoupRequestConfig requestConfig;

    public Connection.Response getWebpageResponse(String url) {

        Connection.Response response = null;
        try {
            Thread.sleep(250);
            response = Jsoup.connect(url)
                    .header("Accept-Language", "ru")
                    .userAgent(requestConfig.getUserAgent())
                    .referrer(requestConfig.getReferrer())
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

    public WebPage getWebpage(String url, Integer siteId, Connection.Response response) {
        WebPage webpage = new WebPage(url, siteId);

        webpage.setStatusCode(response.statusCode());

        try {
            String content = Jsoup.parse(response.parse().html()).wholeText() //We do not need tags, we only need page text to work with l8r
                    .replace('\n', ' ')
                    //.replaceAll("[^\\x20-\\x7e]", "") //Replacing non-ASCII chars.
                    .replaceAll(" {2,}", " ");
            if (content.charAt(0) == ' ') {
                content = content.substring(1);
            }
            webpage.setContent(content);
            webpage.setLemmas(LemmaMapper.getLemmasAndCountsFromText(webpage.getContent()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return webpage;
    }

    public WebPage getWebpage(Connection.Response response, WebPage source) {
        WebPage result = getWebpage(source.getUrl(), source.getPageId(), response);

        result.setParentUrl(source.getParentUrl());
        result.setUrlList(source.getUrlList());

        return result;

    }

}
