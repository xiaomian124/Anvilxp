package com.xiaomian124.anvilxp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class CustomAnvilGUI implements InventoryHolder {
    private final Player player;
    private final Inventory inventory;
    private int realCost;
    private boolean awaitingRename = false;
    private ItemStack renameItem = null;

    // 槽位常量
    public static final int SLOT_LEFT_INPUT = 10;
    public static final int SLOT_RIGHT_INPUT = 12;
    public static final int SLOT_INFO = 14;
    public static final int SLOT_OUTPUT = 16;
    public static final int SLOT_SWITCH_ANVIL = 19; // 第3行第2列（索引19）
    public static final int SLOT_RENAME = 21;        // 第3行第4列（索引21）

    public static final String PLACEHOLDER_NAME = "§7点击放入物品";

    public CustomAnvilGUI(Player player, int initialCost) {
        this.player = player;
        this.realCost = initialCost;

        inventory = Bukkit.createInventory(this, 27, "§c铁砧 (基岩版)");

        // 装饰玻璃（深灰色）
        ItemStack glass = createGlass(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (i == SLOT_LEFT_INPUT || i == SLOT_RIGHT_INPUT ||
                    i == SLOT_INFO || i == SLOT_OUTPUT ||
                    i == SLOT_SWITCH_ANVIL || i == SLOT_RENAME) {
                continue;
            }
            inventory.setItem(i, glass);
        }

        // 输入槽占位物品
        inventory.setItem(SLOT_LEFT_INPUT, createPlaceholder());
        inventory.setItem(SLOT_RIGHT_INPUT, createPlaceholder());

        // 按钮物品
        inventory.setItem(SLOT_SWITCH_ANVIL, createSwitchAnvilButton());
        inventory.setItem(SLOT_RENAME, createRenameButton());

        updateInfo();
    }

    // ---- 按钮物品 ----
    public ItemStack createSwitchAnvilButton() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6切换到原版铁砧");
        meta.setLore(Arrays.asList("§7点击后使用原版铁砧界面"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRenameButton() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6重命名物品");
        meta.setLore(Arrays.asList("§7点击后输入新名称"));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSwitchAnvilSlot(int slot) { return slot == SLOT_SWITCH_ANVIL; }
    public boolean isRenameSlot(int slot) { return slot == SLOT_RENAME; }

    // ---- 占位物品 ----
    public ItemStack createPlaceholder() {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(PLACEHOLDER_NAME);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return PLACEHOLDER_NAME.equals(item.getItemMeta().getDisplayName());
    }

    // ---- 获取真实输入（忽略占位） ----
    public ItemStack getLeftInput() {
        ItemStack item = inventory.getItem(SLOT_LEFT_INPUT);
        return isPlaceholder(item) ? null : item;
    }

    public ItemStack getRightInput() {
        ItemStack item = inventory.getItem(SLOT_RIGHT_INPUT);
        return isPlaceholder(item) ? null : item;
    }

    // ---- 更新信息 ----
    public void updateInfo() {
        inventory.setItem(SLOT_INFO, createInfoItem());
    }

    public void updateResult(ItemStack result) {
        if (result != null && !result.getType().isAir()) {
            inventory.setItem(SLOT_OUTPUT, result.clone());
        } else {
            inventory.setItem(SLOT_OUTPUT, null);
        }
    }

    // ---- Getter / Setter ----
    public ItemStack getResult() { return inventory.getItem(SLOT_OUTPUT); }
    public Inventory getInventory() { return inventory; }
    public Player getPlayer() { return player; }
    public int getRealCost() { return realCost; }
    public void setRealCost(int cost) { this.realCost = cost; updateInfo(); }

    public boolean isInputSlot(int slot) {
        return slot == SLOT_LEFT_INPUT || slot == SLOT_RIGHT_INPUT;
    }

    public boolean isAwaitingRename() { return awaitingRename; }
    public void setAwaitingRename(boolean awaiting) { this.awaitingRename = awaiting; }

    public ItemStack getRenameItem() { return renameItem; }
    public void setRenameItem(ItemStack item) { this.renameItem = item; }

    public void clearSlots() {
        inventory.setItem(SLOT_LEFT_INPUT, null);
        inventory.setItem(SLOT_RIGHT_INPUT, null);
        inventory.setItem(SLOT_OUTPUT, null);
    }

    // ---- 内部辅助 ----
    private ItemStack createGlass(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6所需经验");
        meta.setLore(Arrays.asList(
                "§7需要等级: §e" + realCost,
                "§7当前经验: §e" + player.getLevel(),
                player.getLevel() >= realCost ? "§a✔ 经验充足" : "§c✘ 经验不足"
        ));
        item.setItemMeta(meta);
        return item;
    }
}