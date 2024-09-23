package searchengine.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    private boolean result = true;

    private int count;

    private List<SearchItem> data = new ArrayList<>();

    public void addData(List<SearchItem> toAdd) {
        data.addAll(toAdd);
    }

    public void sortResultsByDescendingRelevancy() {
        List<SearchItem> items = data;

        for (int i = 0; i < items.size() - 1; i++) {
            for (int j = 0; j < items.size() - i - 1; j++) {
                SearchItem ij = items.get(j);
                SearchItem ijp = items.get(j + 1);
                if (Float.parseFloat(ijp.getRelevance()) > Float.parseFloat(ij.getRelevance())) {
                    items.set(j, ijp);
                    items.set(j + 1, ij);
                }
            }
        }

        data = items;
    }

    public void setResultsByLimitAndOffset(int limit, int offset) {
        List<SearchItem> newItems = new ArrayList<>();

        for (int i = offset; i < offset + limit && i < data.size(); i++) {
            newItems.add(data.get(i));
        }

        data = newItems;
    }

}
