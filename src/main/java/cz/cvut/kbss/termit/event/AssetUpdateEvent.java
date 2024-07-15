package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.Asset;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when an asset is updated.
 */
public class AssetUpdateEvent extends ApplicationEvent {

    private final Asset<?> asset;

    public AssetUpdateEvent(Object source, Asset<?> asset) {
        super(source);
        this.asset = asset;
    }

    public Asset<?> getAsset() {
        return asset;
    }
}
