package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.Asset;
import org.springframework.context.ApplicationEvent;

/**
 * Event published before an asset is deleted.
 */
public class BeforeAssetDeleteEvent extends ApplicationEvent {
    final Asset<?> asset;
    public BeforeAssetDeleteEvent(Object source, Asset<?> asset) {
        super(source);
        this.asset = asset;
    }

    public Asset<?> getAsset() {
        return asset;
    }
}
