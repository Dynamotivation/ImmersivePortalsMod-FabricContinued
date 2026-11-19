package qouteall.q_misc_util.my_util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.Stream;

/**
 * Minecraft 26.x replaced direct CompoundTag access in entity/block-entity (de)serialization
 * with {@link ValueInput}/{@link ValueOutput}.
 * Immersive Portals has a lot of CompoundTag based save/load logic, so this bridge copies
 * a whole CompoundTag out of a ValueInput or into a ValueOutput, so the old logic can be reused.
 */
public class ValueTagBridge {
    /**
     * A MapCodec that reads/writes every key of the surrounding map as a CompoundTag.
     */
    public static final MapCodec<CompoundTag> ALL_KEYS = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            CompoundTag result = new CompoundTag();
            input.entries().forEach(pair -> {
                String key = ops.getStringValue(pair.getFirst()).result().orElse(null);
                if (key != null) {
                    Tag tag = ops.convertTo(NbtOps.INSTANCE, pair.getSecond());
                    result.put(key, tag);
                }
            });
            return DataResult.success(result);
        }

        @Override
        public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : input.keySet()) {
                Tag tag = input.get(key);
                if (tag != null) {
                    prefix.add(ops.createString(key), NbtOps.INSTANCE.convertTo(ops, tag));
                }
            }
            return prefix;
        }

        @Override
        public String toString() {
            return "ValueTagBridge.ALL_KEYS";
        }
    };

    /** Copy everything in the ValueInput into a new CompoundTag. */
    public static CompoundTag toTag(ValueInput input) {
        return input.read(ALL_KEYS).orElseGet(CompoundTag::new);
    }

    /** Write every entry of the tag into the ValueOutput. */
    public static void writeTag(ValueOutput output, CompoundTag tag) {
        output.store(ALL_KEYS, tag);
    }
    
    /** Wrap a CompoundTag as a ValueInput, for calling 26.x load(...) methods. */
    public static ValueInput inputOf(CompoundTag tag, HolderLookup.Provider lookup) {
        return TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag);
    }
    
    /** Wrap an entity's full save data into the given tag. */
    public static void saveEntityToTag(Entity entity, CompoundTag tag) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING, entity.level().registryAccess()
        );
        entity.saveWithoutId(output);
        tag.merge(output.buildResult());
    }
    
    /** Full entity save data (without id) as a CompoundTag. */
    public static CompoundTag entityToTag(Entity entity) {
        CompoundTag tag = new CompoundTag();
        saveEntityToTag(entity, tag);
        return tag;
    }
    
    /** Load an entity from a CompoundTag produced by {@link #entityToTag}. */
    public static void loadEntityFromTag(Entity entity, CompoundTag tag) {
        entity.load(inputOf(tag, entity.level().registryAccess()));
    }
}
