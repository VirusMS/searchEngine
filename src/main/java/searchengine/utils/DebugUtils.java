package searchengine.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import searchengine.config.AppConfig;

@Component
@Getter
@RequiredArgsConstructor
public class DebugUtils {

    private final AppConfig appConfig;

    public void println(String message) {

        if (appConfig.isDebug()) {
            System.out.println(message);
        }
    }

}
