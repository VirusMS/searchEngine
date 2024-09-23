package searchengine.mapper;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class LemmaMapper {

    private static final List<String> BAD_MORPHS = List.of("ПРЕДЛ", "СОЮЗ", "МЕЖД");

    public static HashMap<String, Integer> getLemmasAndCountsFromText(String text) {
        HashMap<String, Integer> lemmas = new HashMap<>();

        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> words = splitTextToWords(text);

            for (String word : words) {
                List<String> wordBaseForms = luceneMorph.getNormalForms(word);
                List<String> morphInfo = luceneMorph.getMorphInfo(word);

                for (int i = 0; i < wordBaseForms.size(); i++) {
                    String morph = morphInfo.get(i);
                    String baseForm = wordBaseForms.get(i);
                    if (!morphContainsBadDefinitions(morph)) {
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

    public static List<String> getListOfWordsWithBaseForms(String text, List<String> lemmas) {
        List<String> result = new ArrayList<>();

        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> words = splitTextToWords(text);

            for (String word : words) {
                List<String> wordBaseForms = luceneMorph.getNormalForms(word);
                List<String> morphInfo = luceneMorph.getMorphInfo(word);

                for (int i = 0; i < wordBaseForms.size(); i++) {
                    String morph = morphInfo.get(i);
                    String baseForm = wordBaseForms.get(i);
                    if (!morphContainsBadDefinitions(morph)) {
                        if (lemmas.contains(baseForm)) {
                            result.add(word);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result.stream().distinct().collect(Collectors.toList());
    }

    public static List<String> getLemmasListFromText(String text) {
        return new ArrayList<>(getLemmasAndCountsFromText(text).keySet());
    }

    private static boolean morphContainsBadDefinitions(String morph) {
        return BAD_MORPHS.stream().anyMatch(morph::contains);
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
            if (!toAdd.equals("") && !toAdd.equals("-") && !toAdd.equals("--")) {

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
