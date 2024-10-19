package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.Asset;
import org.springframework.context.ApplicationEvent;

/**
 * Event published before an asset is deleted.
 */
public class BeforeAssetDeleteEvent<T extends Asset<?>> extends ApplicationEvent {
    final T asset;
    public BeforeAssetDeleteEvent(Object source, T asset) {
        super(source);
        this.asset = asset;
    }

    public T getAsset() {
        return asset;
    }
}
