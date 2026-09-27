package br.com.castel.restaurant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code PUT /api/restaurant/tabs/{tabId}/split-groups}: the split group of each item named.
 *
 * <p>Only the ids are checked here, because without them there is nothing to move: an absent list or
 * {@code itemId} answers 400. A group missing or outside 1 to 99 is a rule of the domain and answers
 * {@code INVALID_SPLIT_GROUP}.
 */
public class SplitGroupsRequest {

    @NotNull
    @Valid
    private List<Assignment> assignments;

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<Assignment> assignments) {
        this.assignments = assignments;
    }

    /** One item and the split group it goes to. */
    public static class Assignment {

        @NotBlank
        private String itemId;

        private Integer splitGroup;

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public Integer getSplitGroup() {
            return splitGroup;
        }

        public void setSplitGroup(Integer splitGroup) {
            this.splitGroup = splitGroup;
        }
    }
}
