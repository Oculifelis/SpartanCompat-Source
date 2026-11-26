package dev.oculifelis.spartancompat.compat;

import com.mojang.authlib.GameProfile;
import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.config.TraitConfig;
import dev.oculifelis.spartancompat.trait.CompatTraits;
import dev.oculifelis.spartancompat.trait.SourceTraits;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Game-bus handlers for the source-mod traits that are not plain hit callbacks. */
public final class SourceEvents {
    private SourceEvents() {}

    private static final ResourceLocation PIXIE_ID = SpartanCompat.id("pixie_affinity");
    private static final ResourceLocation REACH_ID = SpartanCompat.id("shulker_reach");
    private static final ResourceLocation HEFT_ID = SpartanCompat.id("unrealium_heft");
    public static final ResourceLocation TIDAL_ID = SpartanCompat.id("tidal_speed");
    public static final ResourceLocation OVERCLOCK_ID = SpartanCompat.id("overclock_speed");

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (CompatTraits.has(stack, SourceTraits.PIXIE_AFFINITY)) {
            BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("botania:pixie_spawn_chance")).ifPresent(h ->
                    event.addModifier(h, new AttributeModifier(PIXIE_ID, SourceTraits.PIXIE_AFFINITY.get().getMagnitude(),
                            AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
        }
        if (CompatTraits.has(stack, SourceTraits.SHULKER_REACH)) {
            double add = SourceTraits.SHULKER_REACH.get().getMagnitude();
            event.addModifier(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, add, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            event.addModifier(Attributes.BLOCK_INTERACTION_RANGE, new AttributeModifier(REACH_ID, add, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (CompatTraits.has(stack, SourceTraits.UNREALIUM_HEFT)) {
            event.addModifier(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(HEFT_ID, -SourceTraits.UNREALIUM_HEFT.get().getMagnitude(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
        }
    }

    /** Beheading: the victim's head, if it has one. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
        if (!CompatTraits.has(killer.getMainHandItem(), SourceTraits.BEHEADING)) return;
        if (killer.getRandom().nextFloat() >= SourceTraits.BEHEADING.get().getMagnitude()) return;
        ItemStack head = headOf(event.getEntity());
        if (head.isEmpty()) return;
        LivingEntity v = event.getEntity();
        event.getDrops().add(new ItemEntity(v.level(), v.getX(), v.getY(), v.getZ(), head));
    }

    public static ItemStack headOf(LivingEntity victim) {
        EntityType<?> t = victim.getType();
        if (t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.BOGGED) return new ItemStack(Items.SKELETON_SKULL);
        if (t == EntityType.WITHER_SKELETON) return new ItemStack(Items.WITHER_SKELETON_SKULL);
        if (t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.DROWNED || t == EntityType.ZOMBIE_VILLAGER)
            return new ItemStack(Items.ZOMBIE_HEAD);
        if (t == EntityType.CREEPER) return new ItemStack(Items.CREEPER_HEAD);
        if (t == EntityType.ENDER_DRAGON) return new ItemStack(Items.DRAGON_HEAD);
        if (t == EntityType.PIGLIN || t == EntityType.PIGLIN_BRUTE) return new ItemStack(Items.PIGLIN_HEAD);
        if (t == EntityType.ENDERMAN) {
            Item eh = BuiltInRegistries.ITEM.get(ResourceLocation.parse("enderio:enderman_head"));
            return eh == Items.AIR ? ItemStack.EMPTY : new ItemStack(eh);
        }
        if (victim instanceof Player p) {
            ItemStack s = new ItemStack(Items.PLAYER_HEAD);
            GameProfile profile = p.getGameProfile();
            s.set(DataComponents.PROFILE, new ResolvableProfile(profile));
            return s;
        }
        return ItemStack.EMPTY;
    }

    /** Poseidite: faster swings in rain or water (transient modifier while it applies). */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !SourceTraits.TIDAL.isBound()) return;
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) return;
        boolean want = player.isInWaterRainOrBubble() && CompatTraits.has(player.getMainHandItem(), SourceTraits.TIDAL);
        boolean has = speed.hasModifier(TIDAL_ID);
        if (want && !has) speed.addTransientModifier(new AttributeModifier(TIDAL_ID, TraitConfig.number("tidal_attack_speed"),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else if (!want && has) speed.removeModifier(TIDAL_ID);
        overclock(player, speed);
    }

    /** Accelerite: attack speed from live Overclock stacks, only while an Overclock weapon is in the main hand. */
    private static void overclock(Player player, AttributeInstance speed) {
        int stacks = CompatTraits.has(player.getMainHandItem(), SourceTraits.OVERCLOCK) ? SourceTraits.overclockStacks(player) : 0;
        double want = stacks * TraitConfig.number("overclock_speed_per_stack");
        AttributeModifier cur = speed.getModifier(OVERCLOCK_ID);
        if (cur != null && cur.amount() == want) return;
        if (cur != null) speed.removeModifier(OVERCLOCK_ID);
        if (want > 0) speed.addTransientModifier(new AttributeModifier(OVERCLOCK_ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (SourceTraits.flag("dark_steel_energy_enabled") && CompatTraits.has(stack, SourceTraits.EMPOWERED)) {
            event.getToolTip().add(Component.translatable("tooltip.spartan_compat.energy",
                    String.format("%,d", DarkSteelEnergy.get(stack)), String.format("%,d", DarkSteelEnergy.capacity()))
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ---- Terra beam: server on entity hits, client sends a packet on empty swings, as Botania does ----
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide() && ModList.get().isLoaded("botania"))
            BotaniaCompat.trySpawnBeam(p, p.getAttackStrengthScale(0));
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (ModList.get().isLoaded("botania") && SourceTraits.flag("terra_beam_enabled")
                && CompatTraits.has(event.getItemStack(), SourceTraits.TERRA_BEAM))
            PacketDistributor.sendToServer(BeamPayload.INSTANCE);
    }

    public record BeamPayload() implements CustomPacketPayload {
        public static final BeamPayload INSTANCE = new BeamPayload();
        public static final Type<BeamPayload> TYPE = new Type<>(SpartanCompat.id("terra_beam"));
        public static final StreamCodec<ByteBuf, BeamPayload> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(BeamPayload.TYPE, BeamPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ModList.get().isLoaded("botania")) BotaniaCompat.trySpawnBeam(ctx.player(), ctx.player().getAttackStrengthScale(0));
        }));
    }
}
