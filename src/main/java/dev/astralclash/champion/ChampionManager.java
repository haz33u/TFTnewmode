package dev.astralclash.champion;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.impl.*;
import dev.astralclash.champion.trait.Trait;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads all champion definitions from {@code champions.yml} and wires up
 * their {@link dev.astralclash.champion.ability.Ability} implementations.
 *
 * Also manages the champion pool (deck) used by the shop.
 */
public class ChampionManager {

    private final AstralClash plugin;

    /** id → Champion (immutable static data) */
    private final Map<String, Champion> champions = new LinkedHashMap<>();

    /**
     * Pool of available copies per champion id.
     * Starts filled based on pool-size config and is modified by the shop.
     */
    private final Map<String, Integer> pool = new HashMap<>();

    public ChampionManager(AstralClash plugin) {
        this.plugin = plugin;
        loadChampions();
        fillPool();
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    private void loadChampions() {
        FileConfiguration cfg = loadChampionsYaml();
        ConfigurationSection sec = cfg.getConfigurationSection("champions");
        if (sec == null) {
            plugin.getLogger().severe("champions.yml is empty or malformed!");
            return;
        }

        for (String id : sec.getKeys(false)) {
            try {
                Champion c = parseChampion(id, sec.getConfigurationSection(id));
                wireAbility(c);
                champions.put(id, c);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load champion: " + id, e);
            }
        }

        plugin.getLogger().info("Loaded " + champions.size() + " champions.");
    }

    private FileConfiguration loadChampionsYaml() {
        File file = new File(plugin.getDataFolder(), "champions.yml");
        if (!file.exists()) plugin.saveResource("champions.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    private Champion parseChampion(String id, ConfigurationSection s) {
        String      name      = s.getString("display-name", id);
        int         cost      = s.getInt("cost", 1);
        ChampionTier tier     = ChampionTier.fromCost(cost);

        List<Trait> traits = new ArrayList<>();
        for (String traitName : s.getStringList("traits")) {
            try { traits.add(Trait.valueOf(traitName.toUpperCase())); }
            catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown trait '" + traitName + "' for champion " + id);
            }
        }

        ConfigurationSection st = s.getConfigurationSection("stats");
        double hp          = st != null ? st.getDouble("hp", 500)            : 500;
        double armor       = st != null ? st.getDouble("armor", 30)          : 30;
        double mr          = st != null ? st.getDouble("magic-resist", 30)   : 30;
        double ad          = st != null ? st.getDouble("attack-damage", 50)  : 50;
        double as          = st != null ? st.getDouble("attack-speed", 0.8)  : 0.8;
        int    ar          = st != null ? st.getInt("attack-range", 1)       : 1;
        double ms          = st != null ? st.getDouble("move-speed", 300)    : 300;
        int    maxMana     = st != null ? st.getInt("max-mana", 100)         : 100;

        String modelId = s.getString("model-id", id);

        return new Champion(id, name, tier, traits, hp, armor, mr, ad, as, ar, ms, maxMana, modelId);
    }

    /**
     * Wires the Java {@link dev.astralclash.champion.ability.Ability} implementation
     * to each champion. If none is found, a no-op ability is assigned.
     */
    private void wireAbility(Champion c) {
        c.setAbility(switch (c.getId()) {
            case "kafka"     -> new Kafka.KafkaAbility();
            case "bronya"    -> new Bronya.BronyaAbility();
            case "himeko"    -> new Himeko.HimekoAbility();
            case "seele"     -> new Seele.SeeleAbility();
            case "welt"      -> new Welt.WeltAbility();
            case "gepard"    -> new Gepard.GepardAbility();
            case "natasha"   -> new Natasha.NatashaAbility();
            case "pela"      -> new Pela.PelaAbility();
            case "asta"      -> new Asta.AstaAbility();
            case "dan-heng"  -> new DanHeng.DanHengAbility();
            case "jing-yuan" -> new JingYuan.JingYuanAbility();
            case "fu-xuan"   -> new FuXuan.FuXuanAbility();
            case "luocha"    -> new Luocha.LuochaAbility();
            case "jingliu"   -> new Jingliu.JingliuAbility();
            case "blade"     -> new Blade.BladeAbility();
            case "march7th"  -> new March7th.March7thAbility();
            default          -> new NoopAbility(c.getDisplayName() + " Ability");
        });
    }

    // ── Pool management ──────────────────────────────────────────────────────

    private void fillPool() {
        for (Champion c : champions.values()) {
            int size = plugin.getConfigManager().getPoolSize(c.getTier().getCost());
            pool.put(c.getId(), size);
        }
    }

    /** Removes one copy from the pool. Returns false if pool is empty. */
    public boolean drawFromPool(String championId) {
        int remaining = pool.getOrDefault(championId, 0);
        if (remaining <= 0) return false;
        pool.put(championId, remaining - 1);
        return true;
    }

    /** Returns one copy to the pool (e.g., after sell or game end). */
    public void returnToPool(String championId) {
        pool.merge(championId, 1, Integer::sum);
    }

    /** Returns all champions in the pool with count > 0, grouped by tier. */
    public List<Champion> getAvailableByTier(int tier) {
        List<Champion> result = new ArrayList<>();
        for (Champion c : champions.values()) {
            if (c.getTier().getCost() == tier && pool.getOrDefault(c.getId(), 0) > 0) {
                result.add(c);
            }
        }
        return result;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Champion getChampion(String id)                   { return champions.get(id); }
    public Collection<Champion> getAllChampions()            { return champions.values(); }
    public int getPoolCount(String id)                       { return pool.getOrDefault(id, 0); }

    /**
     * Creates a fresh 1-star instance of the given champion.
     */
    public ChampionInstance createInstance(String id, UUID ownerId) {
        Champion c = champions.get(id);
        if (c == null) return null;
        return new ChampionInstance(c, StarLevel.ONE, ownerId);
    }

    // ── No-op fallback ability ────────────────────────────────────────────────

    private record NoopAbility(String name) implements dev.astralclash.champion.ability.Ability {
        @Override public String getName()        { return name; }
        @Override public String getDescription() { return "Passive: awaiting implementation."; }
        @Override public void execute(ChampionInstance caster,
                                      List<ChampionInstance> allies,
                                      List<ChampionInstance> enemies) { /* no-op */ }
    }
}
