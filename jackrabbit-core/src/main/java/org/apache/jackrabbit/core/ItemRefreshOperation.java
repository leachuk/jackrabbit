package org.apache.jackrabbit.core;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.core.session.SessionContext;
import org.apache.jackrabbit.core.session.SessionOperation;
import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.SessionItemStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemRefreshOperation extends SessionOperation {

    /**
     * Logger instance.
     */
    private static final Logger log =
        LoggerFactory.getLogger(ItemRefreshOperation.class);

    private final ItemState state;

    public ItemRefreshOperation(SessionContext context, ItemState state) {
        super("item refresh", context);
        this.state = state;
    }

    @Override
    public void perform() throws RepositoryException {
        SessionItemStateManager stateMgr = context.getItemStateManager();

        // Optimisation for the root node
        if (state.getParentId() == null) {
            stateMgr.disposeAllTransientItemStates();
            return;
        }

        // list of transient items that should be discarded
        List<ItemState> transientStates = new ArrayList<ItemState>();

        // check status of this item's state
        if (state.isTransient()) {
            switch (state.getStatus()) {
            case ItemState.STATUS_STALE_MODIFIED:
            case ItemState.STATUS_STALE_DESTROYED:
                // add this item's state to the list
                transientStates.add(state);
                break;
            case ItemState.STATUS_EXISTING_MODIFIED:
                if (!state.getParentId().equals(
                        state.getOverlayedState().getParentId())) {
                    throw new RepositoryException(
                            "Cannot refresh a moved item,"
                            + " try refreshing the parent: " + this);
                }
                transientStates.add(state);
                break;
            case ItemState.STATUS_NEW:
                throw new RepositoryException(
                        "Cannot refresh a new item: " + this);
            default:
                // log and ignore
                log.warn("Unexpected item state status {} of {}",
                        state.getStatus(), this);
                break;
            }
        }

        if (state.isNode()) {
            // build list of 'new', 'modified' or 'stale' descendants
            for (ItemState transientState
                    : stateMgr.getDescendantTransientItemStates(state.getId())) {
                switch (transientState.getStatus()) {
                case ItemState.STATUS_STALE_MODIFIED:
                case ItemState.STATUS_STALE_DESTROYED:
                case ItemState.STATUS_NEW:
                case ItemState.STATUS_EXISTING_MODIFIED:
                    // add new or modified state to the list
                    transientStates.add(transientState);
                    break;

                default:
                    // log and ignore
                    log.debug("unexpected state status ({})",
                            transientState.getStatus());
                    break;
                }
            }
        }

        // process list of 'new', 'modified' or 'stale' transient states
        for (ItemState transientState : transientStates) {
            // dispose the transient state, it is no longer used;
            // this will indirectly (through stateDiscarded listener method)
            // either restore or permanently invalidate the wrapping Item instances
            stateMgr.disposeTransientItemState(transientState);
        }

        if (state.isNode()) {
            // discard all transient descendants in the attic (i.e. those marked
            // as 'removed'); this will resurrect the removed items
            for (ItemState descendant
                    : stateMgr.getDescendantTransientItemStatesInAttic(state.getId())) {
                // dispose the transient state; this will indirectly
                // (through stateDiscarded listener method) resurrect
                // the wrapping Item instances
                stateMgr.disposeTransientItemStateInAttic(descendant);
            }
        }
    }

}