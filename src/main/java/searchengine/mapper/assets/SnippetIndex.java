package searchengine.mapper.assets;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SnippetIndex {

    private int start;
    private int end;

    public void mergeIndexes(SnippetIndex toMerge) {
        start = start > toMerge.getStart() ? toMerge.getStart() : start;
        end = end < toMerge.getEnd() ? toMerge.getEnd() : end;
    }

    @Override
    public boolean equals(Object obj) {
        SnippetIndex toMatch = (SnippetIndex) obj;

        return toMatch.getStart() == start && toMatch.getEnd() == end;
    }
}
