package searchengine.mapper;

import searchengine.entity.Lemma;
import searchengine.entity.Page;
import searchengine.mapper.assets.SnippetIndex;

import java.util.*;

public class SnippetMapper {

    private static final String REGEX_TAGS_TO_REMOVE = "<[/]?((b|i|t|em|strong|font|a|br)(>|[ ]{1}[^>]{1,}>))";

    public static String getSnippets(Page page, List<Lemma> lemmas, int snippetRadius) {
        String content = page.getContent().replaceAll(REGEX_TAGS_TO_REMOVE, "").replaceAll("\n", " ");

        List<String> wordsFoundOnPage = LemmaMapper.getListOfWordsWithBaseForms(content, getBaseFormsFromLemmaList(lemmas));
        Map<Integer, String> wordPositions = getWordPositionsFromContent(content, wordsFoundOnPage);

        List<SnippetIndex> snippetIndexList = new ArrayList<>();
        for (int pos : wordPositions.keySet()) {
            int posL = getSnippetBorder(content, pos == 0 ? pos : pos - 1, snippetRadius, false);
            int posR = getSnippetBorder(content, pos + wordPositions.get(pos).length(), snippetRadius, true);
            snippetIndexList.add(new SnippetIndex(posL, posR));
        }

        return getSnippetsFromContent(content, squashCollisions(snippetIndexList), wordsFoundOnPage);
    }

    private static String getSnippetsFromContent(String content, List<SnippetIndex> snippetIndexList, List<String> wordsToHighlight) {
        StringBuilder snippets = new StringBuilder();

        for (SnippetIndex entry : snippetIndexList) {
            snippets.append("... ").append(content, entry.getStart(), entry.getEnd()).append(" ... <...> ");
        }
        snippets = new StringBuilder(snippets.substring(0, snippets.length() - 7));
        for (String word : wordsToHighlight) {
            int wordPos = snippets.indexOf(word);
            if (wordPos == -1) {
                continue;
            }
            snippets.replace(wordPos, wordPos + word.length(), "<b>" + word + "</b>");
        }

        return snippets.toString();
    }

    private static List<SnippetIndex> squashCollisions(List<SnippetIndex> snippetIndexList) {
        List<SnippetIndex> squashedIndex = new ArrayList<>(snippetIndexList);
        Iterator<SnippetIndex> i = squashedIndex.iterator();

        while (i.hasNext()) {
            SnippetIndex entry = i.next();
            SnippetIndex collision = getSnippetCollision(entry, squashedIndex);

            while (collision != null) {
                entry.mergeIndexes(collision);
                squashedIndex.remove(collision);
                collision = getSnippetCollision(entry, squashedIndex);
            }
        }

        return squashedIndex;
    }

    private static int getSnippetBorder(String content, int start, int snippetRadius, boolean isScanningRight) {
        int pos = start;
        int wordCount = 0;
        char bracket = isScanningRight ? '<' : '>';

        while (wordCount < snippetRadius) {
            while (!isPartOfWord(content.charAt(pos)) && isPosWithinContent(pos, content, isScanningRight)) {
                if (content.charAt(pos) == bracket) {
                    pos = isScanningRight ? pos - 1 : pos + 1;
                    break;
                }
                pos = isScanningRight ? pos + 1 : pos - 1;
            }
            while (isPartOfWord(content.charAt(pos))&& isPosWithinContent(pos, content, isScanningRight)) {
                pos = isScanningRight ? pos + 1 : pos - 1;
            }
            wordCount++;
            if (content.charAt(pos) == bracket) {
                pos = isScanningRight ? pos - 1 : pos + 1;
                break;
            }
        }

        return pos;
    }

    private static boolean isPosWithinContent(int pos, String content, boolean isScanningRight) {
        return isScanningRight ? pos < content.length() : pos > 0;
    }

    private static Map<Integer, String> getWordPositionsFromContent(String content, List<String> words) {
        HashMap<Integer, String> wordPositions = new HashMap<>();

        for (String word : words) {
            int wordIndex = content.indexOf(word);
            if (wordIndex != -1) {
                wordPositions.put(content.indexOf(word), word);
            }
        }

        return wordPositions;
    }

    private static List<String> getBaseFormsFromLemmaList(List<Lemma> lemmas) {
        List<String> result = new ArrayList<>();

        for (Lemma lemma : lemmas) {
            result.add(lemma.getLemma());
        }

        return result;
    }

    private static boolean isPartOfWord(char symbol) {
        return ((symbol >= 'а' && symbol <= 'я') || (symbol >= 'А' && symbol <= 'Я') || (symbol == 'ё' || symbol == 'е')
                || (symbol >= 'a' && symbol <= 'z') || (symbol >= 'A' && symbol <= 'Z')
                || symbol == '-');
    }

    private static SnippetIndex getSnippetCollision(SnippetIndex target, List<SnippetIndex> snippetIndexList) {
        int targetStart = target.getStart();
        int targetEnd = target.getEnd();

        for (SnippetIndex entry : snippetIndexList) {
            if (target.equals(entry)) {
                continue;
            }

            int entryStart = entry.getStart();
            int entryEnd = entry.getEnd();

            if ((entryStart >= targetStart && entryStart <= targetEnd) || (entryEnd >= targetStart && entryEnd <= targetEnd)) {
                return entry;
            }
        }

        return null;
    }

}
