package com.xiaomian124.anvilxp;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Anvilxp extends JavaPlugin {

    public static NamespacedKey REAL_COST_KEY;

    public static final Map<UUID, Integer> COST_CACHE = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        REAL_COST_KEY = new NamespacedKey(this, "real_cost");
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
        getLogger().info("§a插件已加载 - 已配置Geyser及Floodgate相关内容");
    }

    @Override
    public void onDisable() {
        COST_CACHE.clear(); // 清理缓存
        getLogger().info("§c插件已卸载");
    }
}