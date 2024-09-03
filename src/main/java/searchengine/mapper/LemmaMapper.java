package searchengine.mapper;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LemmaMapper {

    public static HashMap<String, Integer> getLemmasAndCountsFromText(String text) {
        HashMap<String, Integer> lemmas = new HashMap<>();

        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> words = splitTextToWords(text);

            for (String word : words) {
                //TODO: try to deal with cases of words' base forms such as "какой-ть"
                //TODO: java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 500163 @ getNormalForms
                List<String> wordBaseForms = luceneMorph.getNormalForms(word);
                List<String> morphInfo = luceneMorph.getMorphInfo(word);

                for (int i = 0; i < wordBaseForms.size(); i++) { //both morphs and base forms are similar in position as well as size
                    String morph = morphInfo.get(i);
                    String baseForm = wordBaseForms.get(i);
                    if (!morph.contains("ПРЕДЛ") && !morph.contains("СОЮЗ") && !morph.contains("МЕЖД")) {
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

    public static List<String> getLemmasListFromText(String text) {
        return new ArrayList<>(getLemmasAndCountsFromText(text).keySet());
    }

    private static List<String> splitTextToWords(String text) {
        List<String> result = new ArrayList<>();

        text = text.replaceAll("[^А-Яа-яёЁ -]", "").replaceAll("[^А-Яа-яёЁ][-][^А-Яа-яёЁ]", "").replaceAll(" {2,}", " ")
                .toLowerCase() + " ";
        if (text.charAt(0) == ' ') {
            text = text.substring(1);
        }
        while (text.contains(" ")) {

            String toAdd = text.substring(0, text.indexOf(" "));
            if (!toAdd.equals("") && !toAdd.equals("-") && !toAdd.equals("--")) { // Some words consist of Russian and English counterparts.

                if (toAdd.charAt(0) == '-') {
                    toAdd = toAdd.substring(1);
                }
                if (toAdd.charAt(toAdd.length() - 1) == '-') {
                    toAdd = toAdd.substring(0, toAdd.length() - 1);
                }
                result.add(text.substring(0, text.indexOf(" ")));
            }

            if (text.length() > 1) {
                text = text.substring(text.indexOf(" ") + 1);
            } else {
                text = text.replace(" ", "-");
            }
        }
        return result;
    }
}
