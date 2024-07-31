package searchengine.mapper;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.HttpStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LemmaMapper {

    public static HashMap<String, Integer> getLemmasFromText(String text) {
        //System.out.println("DEBUG: text = '" + text + "'");
        HashMap<String, Integer> lemmas = new HashMap<>();

        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> words = splitTextToWords(text);

            //System.out.println("  words list - " + words);
            for (String word : words) {

                List<String> wordBaseForms = luceneMorph.getNormalForms(word);
                List<String> morphInfo = luceneMorph.getMorphInfo(word);

                for (int i = 0; i < wordBaseForms.size(); i++) { //both morphs and base forms are similar in position as well as size
                    String morph = morphInfo.get(i);
                    String baseForm = wordBaseForms.get(i);
                    //System.out.println("DEBUG (getLemmasFromText): baseForm = '" + baseForm + "', morph = '" + morph + "'");
                    if (!morph.contains("ПРЕДЛ") && !morph.contains("СОЮЗ") && !morph.contains("МЕЖД")) {
                        //System.out.println("  not ПРЕДЛ & not СОЮЗ, add to list");
                        if (lemmas.containsKey(baseForm)) {
                            lemmas.put(baseForm, lemmas.get(baseForm) + 1);
                        } else {
                            lemmas.put(baseForm, 1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return lemmas;
    }

    private static List<String> splitTextToWords(String text) {
        List<String> result = new ArrayList<>();

        text = text.replaceAll("[^А-Яа-яёЁ ]", "").replaceAll(" {2,}", " ").toLowerCase();
        while (text.contains(" ")) {
            result.add(text.substring(0, text.indexOf(" ")));
            if (text.length() > 1) {
                text = text.substring(text.indexOf(" ") + 1);
            } else {
                text = text.replace(" ", "-");
            }
        }

        return result;
    }
}
