package dev.astralclash.model;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wraps ModelEngine R4 API via reflection so the plugin compiles
 * even without ModelEngine on the classpath (it is a softdepend).
 *
 * All methods are safe to call when ModelEngine is absent — they
 * return immediately if the API is unavailable.
 */
public class ModelEngineService {

    private final AstralClash plugin;
    private final boolean     available;

    // Cached reflection handles — null when ModelEngine is absent
    private Method mCreateModeledEntity;  // ModelEngineAPI.createModeledEntity(Entity)
    private Method mCreateActiveModel;    // ModelEngineAPI.createActiveModel(String)
    private Method mGetModeledEntity;     // ModelEngineAPI.getModeledEntity(Entity)
    private Method mAddModel;             // ModeledEntity.addModel(ActiveModel, boolean)
    private Method mSetBaseEntityVisible; // ModeledEntity.setBaseEntityVisible(boolean)
    private Method mGetModels;            // ModeledEntity.getModels() -> Map
    private Method mGetAnimationHandler;  // ActiveModel.getAnimationHandler()
    private Method mPlayAnimation;        // AnimationHandler.playAnimation(String,double,double,double,boolean)

    /** Maps champion-instance UUID → base ArmorStand UUID for cleanup. */
    private final Map<UUID, UUID> instanceToEntity = new HashMap<>();

    public ModelEngineService(AstralClash plugin) {
        this.plugin    = plugin;
        this.available = initReflection();
        if (available) {
            plugin.getLogger().info("[ModelEngineService] ModelEngine detected — 3-D models enabled.");
        } else {
            plugin.getLogger().info("[ModelEngineService] ModelEngine not found — running without 3-D models.");
        }
    }

    // ── Reflection bootstrap ──────────────────────────────────────────────────

    private boolean initReflection() {
        try {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine")) return false;

            Class<?> api          = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Class<?> modeledEnt   = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> activeModel  = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Class<?> animHandler  = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler");
            Class<?> entityClass  = org.bukkit.entity.Entity.class;

            mCreateModeledEntity  = api.getMethod("createModeledEntity", entityClass);
            mCreateActiveModel    = api.getMethod("createActiveModel", String.class);
            mGetModeledEntity     = api.getMethod("getModeledEntity", entityClass);
            mAddModel             = modeledEnt.getMethod("addModel", activeModel, boolean.class);
            mSetBaseEntityVisible = modeledEnt.getMethod("setBaseEntityVisible", boolean.class);
            mGetModels            = modeledEnt.getMethod("getModels");
            mGetAnimationHandler  = activeModel.getMethod("getAnimationHandler");
            mPlayAnimation        = animHandler.getMethod("playAnimation",
                                        String.class, double.class, double.class, double.class, boolean.class);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.INFO,
                    "[ModelEngineService] ModelEngine API not available: " + e.getMessage());
            return false;
        }
    }

    public boolean isAvailable() { return available; }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    public boolean spawnModel(ChampionInstance ci, Location location) {
        if (!available || location.getWorld() == null) return false;

        String modelId = ci.getChampion().getModelId();
        if (modelId == null || modelId.isBlank()) return false;

        try {
            ArmorStand stand = (ArmorStand) location.getWorld()
                    .spawnEntity(location, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);

            Object me = mCreateModeledEntity.invoke(null, stand);
            if (me == null) { stand.remove(); return false; }

            Object model = mCreateActiveModel.invoke(null, modelId);
            if (model == null) { stand.remove(); return false; }

            mAddModel.invoke(me, model, true);
            mSetBaseEntityVisible.invoke(me, false);

            ci.setModelEntityId(stand.getUniqueId());
            instanceToEntity.put(ci.getModelEntityId(), stand.getUniqueId());

            playAnimation(ci, "idle", true);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[ModelEngineService] Failed to spawn model for "
                    + ci.getChampion().getDisplayName(), e);
            return false;
        }
    }

    // ── Animate ───────────────────────────────────────────────────────────────

    public void playAnimation(ChampionInstance ci, String animation, boolean loop) {
        if (!available || ci.getModelEntityId() == null) return;

        try {
            var entity = plugin.getServer().getEntity(ci.getModelEntityId());
            if (entity == null) return;

            Object me = mGetModeledEntity.invoke(null, entity);
            if (me == null) return;

            @SuppressWarnings("unchecked")
            Map<?, Object> models = (Map<?, Object>) mGetModels.invoke(me);
            for (Object activeModel : models.values()) {
                Object handler = mGetAnimationHandler.invoke(activeModel);
                mPlayAnimation.invoke(handler, animation, 1.0d, 1.0d, 1.0d, loop);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "[ModelEngineService] Animation '" + animation + "' failed for "
                    + ci.getChampion().getDisplayName(), e);
        }
    }

    // ── Despawn ───────────────────────────────────────────────────────────────

    public void despawnModel(ChampionInstance ci) {
        if (!available || ci.getModelEntityId() == null) return;

        try {
            playAnimation(ci, "death", false);
            UUID entityId = ci.getModelEntityId();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                var entity = plugin.getServer().getEntity(entityId);
                if (entity != null) entity.remove();
            }, 30L);
            instanceToEntity.remove(entityId);
            ci.setModelEntityId(null);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[ModelEngineService] Failed to despawn model for "
                    + ci.getChampion().getDisplayName(), e);
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void despawnAll() {
        if (!available) return;
        for (UUID entityId : instanceToEntity.values()) {
            var entity = plugin.getServer().getEntity(entityId);
            if (entity != null) entity.remove();
        }
        instanceToEntity.clear();
    }
}
