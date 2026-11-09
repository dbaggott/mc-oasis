package com.oasissmp.technoblade;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.entity.property.type.GeyserBooleanEntityProperty;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.util.Identifier;

/**
 * Technoblade's crown as Bedrock players see it. Geyser gives every pig the
 * Bedrock entity property {@code oasis:crowned}, and sets it on Technoblade
 * as Geyser spawns it for each Bedrock player. The server's Bedrock pack
 * ({@code resourcepack-bedrock/}) draws the crown on a pig's head while the
 * property is set, so it moves with the head, as Java's painted crown does.
 *
 * <p>The only class that touches Geyser: the plugin uses it only when Geyser
 * is loaded, and runs without it.
 */
final class BedrockCrown {

    private static final Identifier PIG = Identifier.of("minecraft", "pig");
    private static final Identifier CROWNED = Identifier.of("oasis", "crowned");

    private final Predicate<UUID> isTechnoblade;
    private final Logger logger;
    // Set on Geyser's thread when it enables, read on its network threads.
    private volatile GeyserBooleanEntityProperty crowned;

    private BedrockCrown(Predicate<UUID> isTechnoblade, Logger logger) {
        this.isTechnoblade = isTechnoblade;
        this.logger = logger;
    }

    /**
     * Subscribes to Geyser's events. Called from the plugin's {@code onLoad},
     * since Geyser asks for entity properties when it enables, and a property
     * defined later is never sent to Bedrock players. The spawn event comes on
     * Geyser's own threads, so {@code isTechnoblade} has to be safe to call
     * from any thread.
     */
    static void register(Object owner, Predicate<UUID> isTechnoblade, Logger logger) {
        BedrockCrown crown = new BedrockCrown(isTechnoblade, logger);
        var events = GeyserApi.api().eventBus();
        EventRegistrar registrar = EventRegistrar.of(owner);
        events.subscribe(registrar, GeyserDefineEntityPropertiesEvent.class, crown::define);
        events.subscribe(registrar, ServerSpawnEntityEvent.class, crown::onSpawn);
    }

    private void define(GeyserDefineEntityPropertiesEvent event) {
        crowned = event.registerBooleanProperty(PIG, CROWNED, false);
        logger.info("Defined the Bedrock entity property " + CROWNED + " on " + PIG + " for Technoblade's crown");
    }

    private void onSpawn(ServerSpawnEntityEvent event) {
        if (!isTechnoblade.test(event.uuid())) {
            return;
        }
        GeyserBooleanEntityProperty property = crowned;
        if (property == null) {
            logger.severe("Geyser never asked for entity properties, so Bedrock players see Technoblade"
                    + " without its crown");
            return;
        }
        event.preSpawnConsumer(entity -> entity.updateProperty(property, true));
    }
}
