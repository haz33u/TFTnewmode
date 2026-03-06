package dev.astralclash.model;

import dev.astralclash.AstralClash;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages champion skin URLs for ModelEngine player-skin models.
 * Skins are loaded from plugins/AstralClash/skins.yml.
 *
 * Each champion can have a custom skin URL (from mineskin.org, namemc, etc.)
 * or fall back to a default generated skin.
 */
public class ChampionSkinManager {

    private final AstralClash plugin;
    private final Map<String, String> skinUrls = new HashMap<>();
    private final Map<String, String> skinSignatures = new HashMap<>();
    
    // Default HSR-inspired skins (using texture URLs from skin services)
    private static final Map<String, String> DEFAULT_SKINS = Map.ofEntries(
        // These are placeholder URLs - in production, use actual skin textures
        Map.entry("asta", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJBc3RhIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FzdGFfc2tpbiIKICAgIH0KICB9Cn0="),
        Map.entry("natasha", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJOYXRhc2hhIgp9"),
        Map.entry("dan_heng", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJEYW5IZW5nIgp9"),
        Map.entry("march7th", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYXJjaDd0aCIKfQ=="),
        Map.entry("pela", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJQZWxhIgp9"),
        Map.entry("welt", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJXZWx0Igp9"),
        Map.entry("gepard", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJHZXBhcmQiCn0="),
        Map.entry("bronya", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJCcm9ueWEiCn0="),
        Map.entry("seele", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJTZWVsZSIKfQ=="),
        Map.entry("himeko", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJIaW1la28iCn0="),
        Map.entry("kafka", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJLYWZrYSIKfQ=="),
        Map.entry("jing_yuan", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJKaW5nWXVhbiIKfQ=="),
        Map.entry("fu_xuan", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJGdVh1YW4iCn0="),
        Map.entry("luocha", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJMdW9jaGEiCn0="),
        Map.entry("jingliu", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJKaW5nbGl1Igp9"),
        Map.entry("blade", "ewogICJ0aW1lc3RhbXAiIDogMTczNDU2Nzg5MDAwMCwKICAicHJvZmlsZUlkIiA6ICIxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJCbGFkZSIKfQ==")
    );

    public ChampionSkinManager(AstralClash plugin) {
        this.plugin = plugin;
        loadSkins();
    }

    private void loadSkins() {
        File skinsFile = new File(plugin.getDataFolder(), "skins.yml");
        
        if (!skinsFile.exists()) {
            createDefaultSkinsFile(skinsFile);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(skinsFile);
        
        for (String championId : config.getKeys(false)) {
            String url = config.getString(championId + ".texture");
            String sig = config.getString(championId + ".signature", "");
            if (url != null && !url.isEmpty()) {
                skinUrls.put(championId, url);
                skinSignatures.put(championId, sig);
            }
        }
        
        plugin.getLogger().info("[ChampionSkinManager] Loaded " + skinUrls.size() + " custom skins.");
    }

    private void createDefaultSkinsFile(File file) {
        YamlConfiguration config = new YamlConfiguration();
        
        config.options().header("""
            # AstralClash Champion Skins Configuration
            # 
            # HOW TO GET SKINS:
            # 1. Go to https://mineskin.org or https://namemc.com
            # 2. Find/upload a skin that looks like the HSR character
            # 3. Copy the base64 texture value
            # 4. Paste it below for the corresponding champion
            #
            # Format:
            # champion_id:
            #   texture: "base64_texture_value"
            #   signature: "base64_signature" (optional, from mineskin)
            #
            # Example with real MineSkin data:
            # asta:
            #   texture: "eyJ0aW1lc3RhbXAi..."
            #   signature: "abc123..."
            """);
        
        // Add placeholder entries for each champion
        for (String champion : DEFAULT_SKINS.keySet()) {
            config.set(champion + ".texture", "");
            config.set(champion + ".signature", "");
        }
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create skins.yml: " + e.getMessage());
        }
    }

    /**
     * Gets the skin texture for a champion.
     * Returns the custom skin if configured, otherwise a default.
     */
    public String getSkinTexture(String championId) {
        String custom = skinUrls.get(championId);
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        return DEFAULT_SKINS.getOrDefault(championId, DEFAULT_SKINS.get("asta"));
    }

    public String getSkinSignature(String championId) {
        return skinSignatures.getOrDefault(championId, "");
    }

    public boolean hasCustomSkin(String championId) {
        String skin = skinUrls.get(championId);
        return skin != null && !skin.isEmpty();
    }

    public void reload() {
        skinUrls.clear();
        skinSignatures.clear();
        loadSkins();
    }
}
