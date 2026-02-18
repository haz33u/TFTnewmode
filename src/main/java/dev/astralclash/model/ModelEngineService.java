package dev.astralclash.model;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wraps ModelEngine R4 API to spawn and despawn 3-D champion models.
 *
 * <p>Usage:
 * <ol>
 *   <li>Call {@link #spawnModel} when a champion is deployed to the board.</li>
 *   <li>Call {@link #playAnimation} to trigger an attack/idle animation.</li>
 *   <li>Call {@link #despawnModel} when a champion dies or is removed.</li>
 * </ol>
 *
 * <p>All methods are safe to call even when ModelEngine is not installed —
 * they return immediately if the API is unavailable.
 */
public class ModelEngineService {

    private final AstralClash plugin;
    private final boolean     available;

    /** Maps champion instance → its model's base ArmorStand UUID for cleanup. */
    private final Map<UUID, UUID> instanceToEntity = new HashMap<>();

    public ModelEngineService(AstralClash plugin) {
        this.plugin    = plugin;
        this.available = isModelEnginePresent();
        if (available) {
            plugin.getLogger().info("[ModelEngineService] ModelEngine detected — 3-D models enabled.");
        } else {
            plugin.getLogger().info("[ModelEngineService] ModelEngine not found — running without 3-D models.");
        }
    }

    // ── API availability check ────────────────────────────────────────────────

    private boolean isModelEnginePresent() {
        try {
            Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            return plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine");
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isAvailable() { return available; }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    /**
     * Spawns a ModelEngine entity for {@code ci} at {@code location}.
     * Uses the model-id configured in the champion's data.
     *
     * @return true if the model was successfully spawned
     */
    public boolean spawnModel(ChampionInstance ci, Location location) {
        if (!available || location.getWorld() == null) return false;

        String modelId = ci.getChampion().getModelId();
        if (modelId == null || modelId.isBlank()) return false;

        try {
            // Spawn a small invisible ArmorStand as the base entity
            ArmorStand stand = (ArmorStand) location.getWorld()
                    .spawnEntity(location, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);

            // Attach ModelEngine model
            ModeledEntity me = ModelEngineAPI.createModeledEntity(stand);
            if (me == null) {
                stand.remove();
                return false;
            }

            ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
            if (model == null) {
                stand.remove();
                return false;
            }

            me.addModel(model, true);
            me.setBaseEntityVisible(false);

            // Store reference for later despawn/animation
            ci.setModelEntityId(stand.getUniqueId());
            instanceToEntity.put(ci.getModelEntityId(), stand.getUniqueId());

            // Start idle animation if available
            playAnimation(ci, "idle", true);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[ModelEngineService] Failed to spawn model for " +
                    ci.getChampion().getDisplayName(), e);
            return false;
        }
    }

    // ── Animate ───────────────────────────────────────────────────────────────

    /**
     * Plays a named animation on the champion's model.
     *
     * @param ci        the champion instance
     * @param animation animation name (e.g. "idle", "attack", "ability", "death")
     * @param loop      true to loop the animation
     */
    public void playAnimation(ChampionInstance ci, String animation, boolean loop) {
        if (!available || ci.getModelEntityId() == null) return;

        try {
            var entity = plugin.getServer().getEntity(ci.getModelEntityId());
            if (entity == null) return;

            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity);
            if (me == null) return;

            me.getModels().values().forEach(model ->
                    model.getAnimationHandler()
                         .playAnimation(animation, 1.0, 1.0, 1.0, loop));

        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "[ModelEngineService] Animation '" + animation + "' not found for " +
                    ci.getChampion().getDisplayName(), e);
        }
    }

    // ── Despawn ───────────────────────────────────────────────────────────────

    /**
     * Plays the death animation briefly, then removes the model entity.
     */
    public void despawnModel(ChampionInstance ci) {
        if (!available || ci.getModelEntityId() == null) return;

        try {
            playAnimation(ci, "death", false);

            UUID entityId = ci.getModelEntityId();
            // Remove after death animation (~1.5 s)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                var entity = plugin.getServer().getEntity(entityId);
                if (entity != null) entity.remove();
            }, 30L);

            instanceToEntity.remove(entityId);
            ci.setModelEntityId(null);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[ModelEngineService] Failed to despawn model for " +
                    ci.getChampion().getDisplayName(), e);
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Removes ALL active model entities (called on plugin disable). */
    public void despawnAll() {
        if (!available) return;
        for (UUID entityId : instanceToEntity.values()) {
            var entity = plugin.getServer().getEntity(entityId);
            if (entity != null) entity.remove();
        }
        instanceToEntity.clear();
    }
}
