package dev.oculifelis.spartancompat.content;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.config.CompatConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI.WeaponItemType;
import org.xiyu.spartanweaponryunofficial.api.WeaponMaterial;
import org.xiyu.spartanweaponryunofficial.api.WeaponTraits;

/** Builds one Spartan weapon of every type for each material whose module is active. Ids are <material>_<type>. */
public final class CompatItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SpartanCompat.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpartanCompat.MOD_ID);

    public static final DeferredItem<Item> BLAZE_POLE = ITEMS.register("blaze_pole", () -> new Item(new Item.Properties()));

    /** Built materials by name, for the active modules only. */
    public static final Map<String, WeaponMaterial> MATERIALS = new LinkedHashMap<>();
    /** Settings of each built material, by name. */
    public static final Map<String, CompatConfig.Material> SETTINGS = new LinkedHashMap<>();
    /** Every registered weapon, in registration order. */
    public static final List<DeferredItem<Item>> WEAPONS = new ArrayList<>();
    /** Material name of each registered weapon id path. */
    public static final Map<String, String> MATERIAL_OF = new LinkedHashMap<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("weapons", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.spartan_compat"))
            .icon(() -> WEAPONS.isEmpty() ? new ItemStack(Items.IRON_SWORD) : new ItemStack(WEAPONS.getFirst().get()))
            .build());

    private CompatItems() {}

    public static String itemId(String material, WeaponItemType type) {
        return material + "_" + type.getSerializedName();
    }

    public static void register(IEventBus modBus) {
        for (CompatConfig.Material m : CompatConfig.materials()) {
            CompatConfig.Module module = CompatConfig.module(m.module());
            if (module == null || !module.active() || !m.enabled()) continue;
            WeaponMaterial material = WeaponMaterial.builder(m.name(), SpartanCompat.MOD_ID)
                    .colours(m.primaryColour(), m.secondaryColour())
                    .durability(m.durability())
                    .speed(m.speed())
                    .baseDamage(m.damage())
                    .enchantability(m.enchantability())
                    .repairTag(TagKey.create(Registries.ITEM, SpartanCompat.id("repair/" + m.name())))
                    .traitsTag(TagKey.create(WeaponTraits.REGISTRY_KEY, SpartanCompat.id("materials/" + m.name())))
                    .incorrectBlocksForDrops(TagKey.create(Registries.BLOCK, ResourceLocation.parse(m.incorrectFor())))
                    .build();
            MATERIALS.put(m.name(), material);
            SETTINGS.put(m.name(), m);
            for (WeaponItemType type : WeaponItemType.values()) {
                String id = itemId(m.name(), type);
                WEAPONS.add(ITEMS.register(id, () -> SpartanWeaponryAPI.createWeapon(type, material)));
                MATERIAL_OF.put(id, m.name());
            }
        }
        SpartanCompat.LOG.info("Registering {} weapons for {} materials", WEAPONS.size(), MATERIALS.size());
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    public static List<DeferredItem<Item>> weapons() {
        return Collections.unmodifiableList(WEAPONS);
    }

    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == TAB.getKey()) {
            for (DeferredItem<Item> item : WEAPONS) event.accept(item.get());
            if (MATERIALS.containsKey("fiery")) event.accept(BLAZE_POLE.get());
        }
    }
}
