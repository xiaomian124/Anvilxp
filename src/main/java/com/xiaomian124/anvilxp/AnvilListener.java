package com.xiaomian124.anvilxp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnvilListener implements Listener {

    private final NamespacedKey realCostKey;
    private final Map<UUID, CustomAnvilGUI> openGuis = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>(); // 防刷

    public AnvilListener(Anvilxp plugin) {
        this.realCostKey = Anvilxp.REAL_COST_KEY;
    }

    // ======================= 基岩版拦截铁砧打开 =======================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!isBedrockPlayer(player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        // 检查是否已打开自定义 GUI（防止重复打开）
        if (openGuis.containsKey(player.getUniqueId())) {
            return;
        }

        // 取消原版铁砧
        event.setCancelled(true);

        // 创建自定义 GUI
        CustomAnvilGUI gui = new CustomAnvilGUI(player, 0);
        openGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    // ======================= Java 版铁砧准备事件 =======================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilView view = event.getView();
        Player player = (Player) view.getPlayer();
        if (player == null) return;

        if (isBedrockPlayer(player)) return;

        int baseCost = view.getRepairCost();
        if (baseCost <= 0) return;

        final int VANILLA_LIMIT = 39;
        int realCost = baseCost;
        if (baseCost > VANILLA_LIMIT) {
            int extraLevels = baseCost - VANILLA_LIMIT;
            int extraCost = (int) (extraLevels * 1.2);
            realCost = VANILLA_LIMIT + extraCost;
        }

        view.setRepairCost(realCost);
        view.setMaximumRepairCost(Integer.MAX_VALUE);

        Anvilxp.COST_CACHE.put(player.getUniqueId(), realCost);

        ItemStack result = view.getItem(2);
        if (result != null && !result.getType().isAir()) {
            ItemMeta meta = result.getItemMeta().clone();
            if (meta != null) {
                meta.getPersistentDataContainer().set(realCostKey, PersistentDataType.INTEGER, realCost);
                result.setItemMeta(meta);
                view.setItem(2, result);
            }
        }
    }

    // ======================= 统一的点击事件 =======================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getHolder() instanceof CustomAnvilGUI) {
            event.setCancelled(true);
            CustomAnvilGUI gui = (CustomAnvilGUI) topInv.getHolder();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 27) return;

            // 输出槽
            if (slot == CustomAnvilGUI.SLOT_OUTPUT) {
                handleResultClick(player, gui);
                return;
            }

            // 输入槽
            if (gui.isInputSlot(slot)) {
                handleInputSlotClick(player, gui, slot);
                return;
            }

            // 切换原版铁砧按钮
            if (gui.isSwitchAnvilSlot(slot)) {
                handleSwitchAnvilClick(player, gui);
                return;
            }

            // 重命名按钮
            if (gui.isRenameSlot(slot)) {
                handleRenameClick(player, gui);
                return;
            }

            return;
        }

        // Java 版铁砧
        if (event.getView().getType() != InventoryType.ANVIL) return;
        if (event.getRawSlot() != 2) return;
        if (!event.isLeftClick()) return;

        UUID uuid = player.getUniqueId();
        Integer realCost = Anvilxp.COST_CACHE.get(uuid);
        if (realCost == null) {
            ItemStack result = event.getCurrentItem();
            if (result != null && result.hasItemMeta()) {
                PersistentDataContainer pdc = result.getItemMeta().getPersistentDataContainer();
                if (pdc.has(realCostKey, PersistentDataType.INTEGER)) {
                    realCost = pdc.get(realCostKey, PersistentDataType.INTEGER);
                }
            }
        }
        if (realCost == null) return;

        int playerLevel = player.getLevel();
        if (playerLevel < realCost) {
            event.setCancelled(true);
            player.sendActionBar("§c经验等级不足！需要 §e" + realCost + " §c级");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        }
    }

    // ======================= 自定义 GUI 输入槽点击 =======================

    private void handleInputSlotClick(Player player, CustomAnvilGUI gui, int slot) {
        ItemStack current = gui.getInventory().getItem(slot);

        if (gui.isPlaceholder(current) || current == null) {
            ItemStack fromBag = getFirstItem(player);
            if (fromBag != null) {
                player.getInventory().removeItem(fromBag.clone());
                gui.getInventory().setItem(slot, fromBag);
                updateCustomGUI(gui);
            } else {
                player.sendActionBar("§e背包中没有物品可以放入");
            }
            return;
        }

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(current);
            gui.getInventory().setItem(slot, gui.createPlaceholder());
            updateCustomGUI(gui);
        } else {
            player.sendActionBar("§c背包已满，无法取出");
        }
    }

    private ItemStack getFirstItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        return null;
    }

    // ======================= 切换原版铁砧 =======================

    private void handleSwitchAnvilClick(Player player, CustomAnvilGUI gui) {
        // 关闭当前 GUI
        player.closeInventory();
        // 从缓存移除
        openGuis.remove(player.getUniqueId());
        Anvilxp.COST_CACHE.remove(player.getUniqueId());

        // 打开原版铁砧（基岩版会再次被拦截，但我们标记一下，避免再次进入自定义）
        // 实际上，我们可以直接打开原版铁砧，但需要在 InventoryOpenEvent 中放行
        // 使用一个临时标记
        player.openInventory(Bukkit.createInventory(null, InventoryType.ANVIL));
        player.sendMessage("§a已切换到原版铁砧（基岩版支持有限）");
    }

    // ======================= 重命名处理 =======================

    private void handleRenameClick(Player player, CustomAnvilGUI gui) {
        // 获取左侧输入槽的物品（要重命名的物品）
        ItemStack target = gui.getLeftInput();
        if (target == null || target.getType().isAir()) {
            player.sendActionBar("§c请先在左侧槽位放入要重命名的物品");
            return;
        }

        // 标记为重命名模式
        gui.setAwaitingRename(true);
        gui.setRenameItem(target.clone());

        // 关闭 GUI，提示玩家输入
        player.closeInventory();
        player.sendMessage("§e请在聊天框输入新的物品名称（输入 §cc §e取消）");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 检查是否在重命名模式
        CustomAnvilGUI gui = openGuis.get(uuid);
        if (gui == null || !gui.isAwaitingRename()) {
            return;
        }

        event.setCancelled(true); // 阻止消息显示

        String input = event.getMessage();

        // 取消重命名
        if (input.equalsIgnoreCase("c")) {
            gui.setAwaitingRename(false);
            gui.setRenameItem(null);
            // 重新打开 GUI
            player.closeInventory();
            player.openInventory(gui.getInventory());
            player.sendMessage("§e已取消重命名");
            return;
        }

        // 验证名称（长度和格式）
        if (input.length() > 50) {
            player.sendMessage("§c名称过长，请重新输入（最多50个字符）");
            return;
        }

        // 清除非法字符（可根据需要调整）
        String cleanName = input.replaceAll("[^\\w\\s§]", "").trim();
        if (cleanName.isEmpty()) {
            player.sendMessage("§c名称不能为空，请重新输入");
            return;
        }

        // 在 GUI 重新打开后执行重命名
        Bukkit.getScheduler().runTask(Anvilxp.getPlugin(Anvilxp.class), () -> {
            // 重新打开 GUI（如果已关闭）
            if (!gui.getInventory().getViewers().contains(player)) {
                player.openInventory(gui.getInventory());
            }

            // 获取重命名的物品
            ItemStack renameTarget = gui.getRenameItem();
            if (renameTarget == null || renameTarget.getType().isAir()) {
                player.sendMessage("§c物品丢失，请重新操作");
                gui.setAwaitingRename(false);
                return;
            }

            // 检查左侧输入槽是否还有该物品（可能已被取出）
            ItemStack currentLeft = gui.getLeftInput();
            if (currentLeft == null || currentLeft.getType().isAir()) {
                player.sendMessage("§c物品已被移走，请重新操作");
                gui.setAwaitingRename(false);
                gui.setRenameItem(null);
                return;
            }

            // 计算消耗（重命名消耗 = 原版基础消耗，但我们可以自定义）
            int renameCost = calculateRenameCost(currentLeft);
            int playerLevel = player.getLevel();

            if (playerLevel < renameCost) {
                player.sendActionBar("§c经验不足！需要 §e" + renameCost + " §c级");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                gui.setAwaitingRename(false);
                gui.setRenameItem(null);
                return;
            }

            // 执行重命名
            try {
                // 消耗经验
                player.setLevel(playerLevel - renameCost);

                // 创建重命名后的物品
                ItemStack renamed = currentLeft.clone();
                ItemMeta meta = renamed.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§r" + cleanName); // §r 清除格式继承
                    renamed.setItemMeta(meta);
                }

                // 消耗原物品（减1）
                ItemStack leftSlot = gui.getInventory().getItem(CustomAnvilGUI.SLOT_LEFT_INPUT);
                if (leftSlot != null && leftSlot.getAmount() > 0) {
                    leftSlot.setAmount(leftSlot.getAmount() - 1);
                    if (leftSlot.getAmount() <= 0) {
                        gui.getInventory().setItem(CustomAnvilGUI.SLOT_LEFT_INPUT, gui.createPlaceholder());
                    }
                }

                // 输出到输出槽
                gui.updateResult(renamed);

                // 更新信息
                gui.updateInfo();

                // 音效和消息
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                player.sendMessage("§a重命名成功！消耗 §e" + renameCost + " §a级");

                // 重置重命名状态
                gui.setAwaitingRename(false);
                gui.setRenameItem(null);

            } catch (Exception e) {
                player.sendMessage("§c重命名失败，请重试");
                e.printStackTrace();
                gui.setAwaitingRename(false);
                gui.setRenameItem(null);
            }
        });
    }

    // 计算重命名消耗（自定义）
    private int calculateRenameCost(ItemStack item) {
        // 基础消耗 = 5 级
        int baseCost = 5;
        // 如果有附魔，每级附魔增加 1 级消耗
        Map<Enchantment, Integer> enchants = getEnchantments(item);
        for (int level : enchants.values()) {
            baseCost += level;
        }
        // 应用超过39级的 ×1.2 规则
        final int LIMIT = 39;
        if (baseCost > LIMIT) {
            int extra = baseCost - LIMIT;
            return (int) (LIMIT + extra * 1.2);
        }
        return baseCost;
    }

    // ======================= 输出槽点击（附魔执行） =======================

    private void handleResultClick(Player player, CustomAnvilGUI gui) {
        ItemStack result = gui.getResult();
        if (result == null || result.getType().isAir()) {
            player.sendActionBar("§c无法附魔，请检查输入物品");
            return;
        }

        int realCost = gui.getRealCost();
        int playerLevel = player.getLevel();

        if (playerLevel < realCost) {
            player.sendActionBar("§c经验等级不足！需要 §e" + realCost + " §c级");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }

        try {
            player.setLevel(playerLevel - realCost);

            ItemStack left = gui.getLeftInput();
            ItemStack right = gui.getRightInput();

            if (left != null && !left.getType().isAir()) {
                left.setAmount(left.getAmount() - 1);
                if (left.getAmount() <= 0) {
                    gui.getInventory().setItem(CustomAnvilGUI.SLOT_LEFT_INPUT, gui.createPlaceholder());
                }
            }
            if (right != null && !right.getType().isAir()) {
                right.setAmount(right.getAmount() - 1);
                if (right.getAmount() <= 0) {
                    gui.getInventory().setItem(CustomAnvilGUI.SLOT_RIGHT_INPUT, gui.createPlaceholder());
                }
            }

            gui.updateResult(null);

            giveItemToPlayer(player, result.clone());

            gui.updateInfo();

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            player.sendMessage("§a附魔成功！消耗 §e" + realCost + " §a级");

        } catch (Exception e) {
            player.sendMessage("§c附魔过程发生错误，请重试");
            e.printStackTrace();
        }
    }

    // ======================= 更新自定义 GUI =======================

    private void updateCustomGUI(CustomAnvilGUI gui) {
        ItemStack left = gui.getLeftInput();
        ItemStack right = gui.getRightInput();

        int cost = calculateCustomCost(left, right);
        gui.setRealCost(cost);

        ItemStack result = calculateResult(left, right);
        gui.updateResult(result);
    }

    // ======================= 自定义消耗计算 =======================

    private int calculateCustomCost(ItemStack left, ItemStack right) {
        if (left == null || right == null) return 0;
        Map<Enchantment, Integer> leftEnchants = getEnchantments(left);
        Map<Enchantment, Integer> rightEnchants = getEnchantments(right);
        int totalLevels = 0;
        for (int lv : leftEnchants.values()) totalLevels += lv;
        for (int lv : rightEnchants.values()) totalLevels += lv;
        int baseCost = totalLevels * 2;
        final int LIMIT = 39;
        if (baseCost > LIMIT) {
            int extra = baseCost - LIMIT;
            return (int) (LIMIT + extra * 1.2);
        }
        return baseCost;
    }

    // ======================= 附魔合并逻辑（修复版） =======================

    private ItemStack calculateResult(ItemStack left, ItemStack right) {
        if (left == null || right == null) return null;

        boolean isBookMerge = (left.getType() == Material.ENCHANTED_BOOK && right.getType() == Material.ENCHANTED_BOOK);
        boolean isToolMerge = left.getType() == right.getType() && isEnchantableItem(left.getType());

        if (!isBookMerge && !isToolMerge) return null;

        try {
            Map<Enchantment, Integer> leftEnchants = getEnchantments(left);
            Map<Enchantment, Integer> rightEnchants = getEnchantments(right);

            if (leftEnchants.isEmpty() && rightEnchants.isEmpty()) {
                // 没有附魔，但如果两个物品相同，可以合并数量？
                // 对于工具，如果是相同的未附魔物品，可以修复耐久
                // 简单起见，返回 null
                return null;
            }

            ItemStack result = left.clone();
            result.setAmount(1);

            Map<Enchantment, Integer> mergedEnchants = new HashMap<>(leftEnchants);

            for (Map.Entry<Enchantment, Integer> rightEntry : rightEnchants.entrySet()) {
                Enchantment ench = rightEntry.getKey();
                int rightLevel = rightEntry.getValue();
                int leftLevel = mergedEnchants.getOrDefault(ench, 0);

                if (hasConflict(mergedEnchants, ench)) continue;

                int maxLevel = getCustomMaxLevel(ench);

                if (leftLevel == rightLevel) {
                    // 同级合并：升一级
                    mergedEnchants.put(ench, Math.min(maxLevel, leftLevel + 1));
                } else {
                    // 取较高等级
                    mergedEnchants.put(ench, Math.min(maxLevel, Math.max(leftLevel, rightLevel)));
                }
            }

            applyEnchantments(result, mergedEnchants);

            if (left.getType() == Material.ENCHANTED_BOOK && right.getType() == Material.ENCHANTED_BOOK) {
                result.setType(Material.ENCHANTED_BOOK);
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ======================= 辅助方法 =======================

    private Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        if (item == null) return new HashMap<>();
        if (item.getType() == Material.ENCHANTED_BOOK) {
            if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                return meta.getStoredEnchants();
            }
        } else {
            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                return item.getItemMeta().getEnchants();
            }
        }
        return new HashMap<>();
    }

    private boolean isEnchantableItem(Material material) {
        return material.isItem() && (
                material.name().contains("PICKAXE") ||
                        material.name().contains("AXE") ||
                        material.name().contains("SHOVEL") ||
                        material.name().contains("HOE") ||
                        material.name().contains("SWORD") ||
                        material.name().contains("HELMET") ||
                        material.name().contains("CHESTPLATE") ||
                        material.name().contains("LEGGINGS") ||
                        material.name().contains("BOOTS") ||
                        material.name().contains("ELYTRA") ||
                        material.name().contains("BOW") ||
                        material.name().contains("CROSSBOW") ||
                        material.name().contains("TRIDENT") ||
                        material.name().contains("FISHING_ROD") ||
                        material.name().contains("SHEARS") ||
                        material.name().contains("FLINT_AND_STEEL") ||
                        material.name().contains("SHIELD")
        );
    }

    private boolean hasConflict(Map<Enchantment, Integer> existing, Enchantment enchantment) {
        for (Enchantment existingEnch : existing.keySet()) {
            if (existingEnch.conflictsWith(enchantment)) {
                return true;
            }
        }
        return false;
    }

    private void applyEnchantments(ItemStack item, Map<Enchantment, Integer> enchants) {
        if (enchants.isEmpty()) return;

        if (item.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                }
                item.setItemMeta(storageMeta);
            }
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
                item.setItemMeta(meta);
            }
        }
    }

    private int getCustomMaxLevel(Enchantment enchantment) {
        return 1000;
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        if (item == null) return;
        ItemStack remaining = player.getInventory().addItem(item.clone()).values().stream().findFirst().orElse(null);
        if (remaining != null) {
            player.getWorld().dropItem(player.getLocation(), remaining);
            player.sendMessage("§c背包已满，物品已掉落在地上");
        }
    }

    // ======================= 关闭界面 =======================

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory topInv = event.getView().getTopInventory();

        if (topInv.getHolder() instanceof CustomAnvilGUI) {
            CustomAnvilGUI gui = (CustomAnvilGUI) topInv.getHolder();

            // 如果正在重命名模式，不清除物品（保留状态）
            if (gui.isAwaitingRename()) {
                return;
            }

            ItemStack left = gui.getInventory().getItem(CustomAnvilGUI.SLOT_LEFT_INPUT);
            ItemStack right = gui.getInventory().getItem(CustomAnvilGUI.SLOT_RIGHT_INPUT);
            if (!gui.isPlaceholder(left) && left != null) {
                giveItemToPlayer(player, left);
            }
            if (!gui.isPlaceholder(right) && right != null) {
                giveItemToPlayer(player, right);
            }
            openGuis.remove(player.getUniqueId());
            Anvilxp.COST_CACHE.remove(player.getUniqueId());
            return;
        }

        if (event.getView().getType() == InventoryType.ANVIL) {
            Anvilxp.COST_CACHE.remove(player.getUniqueId());
        }
    }

    // ======================= 检测基岩版玩家 =======================

    private boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}