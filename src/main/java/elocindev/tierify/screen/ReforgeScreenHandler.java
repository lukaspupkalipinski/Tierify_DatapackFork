package elocindev.tierify.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;

import java.util.List;

import draylar.tiered.api.ModifierUtils;
import draylar.tiered.api.TieredItemTags;
import elocindev.tierify.Tierify;
import elocindev.tierify.network.TieredServerPacket;

public class ReforgeScreenHandler extends ScreenHandler {

    private final Inventory inventory = new SimpleInventory(3) {
        @Override
        public void markDirty() {
            super.markDirty();
            ReforgeScreenHandler.this.onContentChanged(this);
        }
    };

    private final ScreenHandlerContext context;
    private final PlayerEntity player;
    private boolean reforgeReady;
    private BlockPos pos;

    public ReforgeScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(Tierify.REFORGE_SCREEN_HANDLER_TYPE, syncId);

        this.context = context;
        this.player = playerInventory.player;
        this.addSlot(new Slot(this.inventory, 0, 45, 47));
        this.addSlot(new Slot(this.inventory, 1, 80, 34));
        this.addSlot(new Slot(this.inventory, 2, 115, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return isValidAddition(stack);
            }
        });

        int i;
        for (i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
        this.context.run((world, pos) -> {
            ReforgeScreenHandler.this.setPos(pos);
        });
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        if (!player.getWorld().isClient() && inventory == this.inventory) {
            this.updateResult();
        }

    }

    private void updateResult() {
        if (this.getSlot(0).hasStack() && this.getSlot(1).hasStack() && this.getSlot(2).hasStack()) {
            Item item = this.getSlot(1).getStack().getItem();
            if (ModifierUtils.getRandomAttributeIDFor(null, item, false) != null && !this.getSlot(1).getStack().isDamaged()) {

                List<Item> items = Tierify.REFORGE_DATA_LOADER.getReforgeBaseItems(item);
                ItemStack baseItem = this.getSlot(0).getStack();
                if (!items.isEmpty()) {
                    this.reforgeReady = items.stream().anyMatch(it -> it == baseItem.getItem());
                } else if (item instanceof ToolItem toolItem) {
                    this.reforgeReady = toolItem.getMaterial().getRepairIngredient().test(baseItem);
                } else if (item instanceof ArmorItem armorItem && armorItem.getMaterial().getRepairIngredient() != null) {
                    this.reforgeReady = armorItem.getMaterial().getRepairIngredient().test(baseItem);
                } else {
                    this.reforgeReady = baseItem.isIn(TieredItemTags.REFORGE_BASE_ITEM);
                }
            } else {
                this.reforgeReady = false;
            }
        } else {
            this.reforgeReady = false;
        }
        // if (this.reforgeReady && !Tierify.CONFIG.uniqueReforge && ModifierUtils.getAttributeID(this.getSlot(1).getStack()) != null
        //         && ModifierUtils.getAttributeID(this.getSlot(1).getStack()).getPath().contains("unique")) {
        //     this.reforgeReady = false;
        // }
        TieredServerPacket.writeS2CReforgeReadyPacket((ServerPlayerEntity) player, !this.reforgeReady);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.context.run((world, pos) -> this.dropInventory(player, this.inventory));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.context.get((world, pos) -> {
            return player.squaredDistanceTo((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5) <= 64.0;
        }, true);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack itemStack2 = slot.getStack();
            itemStack = itemStack2.copy();
            if (index == 1) {
                if (!this.insertItem(itemStack2, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickTransfer(itemStack2, itemStack);
            } else if (index == 0 || index == 2) {
                if (!this.insertItem(itemStack2, 3, 39, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= 3 && index < 39) {
                if (isValidAddition(itemStack) && !this.insertItem(itemStack2, 2, 3, false)) {
                    return ItemStack.EMPTY;
                }
                if (this.getSlot(1).hasStack()) {
                    Item item = this.getSlot(1).getStack().getItem();
                    if (item instanceof ToolItem toolItem && toolItem.getMaterial().getRepairIngredient().test(itemStack) && !this.insertItem(itemStack2, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                    if (item instanceof ArmorItem armorItem && armorItem.getMaterial().getRepairIngredient() != null && armorItem.getMaterial().getRepairIngredient().test(itemStack)
                            && !this.insertItem(itemStack2, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                    if (itemStack.isIn(TieredItemTags.REFORGE_BASE_ITEM) && !this.insertItem(itemStack2, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                    List<Item> items = Tierify.REFORGE_DATA_LOADER.getReforgeBaseItems(item);
                    if (items.stream().anyMatch(it -> it == itemStack2.copy().getItem()) && !this.insertItem(itemStack2, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
                if (ModifierUtils.getRandomAttributeIDFor(null, itemStack.getItem(), false) != null && !this.insertItem(itemStack2, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (itemStack2.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTakeItem(player, itemStack2);
        }
        return itemStack;
    }

    public void reforge() {
        ItemStack itemStack = this.getSlot(1).getStack();
        ModifierUtils.removeItemStackAttribute(itemStack);
        ModifierUtils.setItemStackAttribute(player, itemStack, true, this.getSlot(2).getStack());

        if (Registries.SOUND_EVENT.get(getReforgeSound(ModifierUtils.getAttributeID(itemStack))) !=null) {
            SoundEvent soundEvent = Registries.SOUND_EVENT.get(getReforgeSound(ModifierUtils.getAttributeID(itemStack)));
            this.context.run((world, pos) -> {
                if (!world.isClient) {
                    world.playSound(null, pos, soundEvent, SoundCategory.BLOCKS, 1f, 1f);
                }
            });
        }

        this.decrementStack(0);
        this.decrementStack(2);
        this.context.run((world, pos) -> world.syncWorldEvent(WorldEvents.ANVIL_USED, (BlockPos) pos, 0));
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    private void decrementStack(int slot) {
        ItemStack itemStack = this.inventory.getStack(slot);
        itemStack.decrement(1);
        this.inventory.setStack(slot, itemStack);
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != this.inventory && super.canInsertIntoSlot(stack, slot);
    }

    public static boolean isValidAddition(ItemStack stack) {
        return stack.isIn(TieredItemTags.TIER_1_ITEM) || stack.isIn(TieredItemTags.TIER_2_ITEM) || stack.isIn(TieredItemTags.TIER_3_ITEM);
    }

    public static Identifier getReforgeSound(Identifier identifier) {
        // plays the corresponding upgrade sound effect for the item tier
        String tier = Tierify.ATTRIBUTE_DATA_LOADER.getItemAttributes().get(identifier).getID();
        if (tier == null) return null;
        String soundId = null;

        String tierName = Text.translatable(tier + ".label").getString().toLowerCase();
        soundId = "reforge_sound_"+tierName;

        return new Identifier("tiered",soundId);
    }


}
