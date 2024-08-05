package org.totemcraft.camera;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;

class EntityDataWriter {
    final FriendlyByteBuf buf;

    EntityDataWriter(int entityId) {
        this.buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(entityId);
    }

    <T> EntityDataWriter write(EntityDataAccessor<T> accessor, T value) {
        buf.writeVarInt(accessor.getId());
        int serializedId = EntityDataSerializers.getSerializedId(accessor.getSerializer());
        buf.writeVarInt(serializedId);
        accessor.getSerializer().write(buf, value);
        return this;
    }

    FriendlyByteBuf create() {
        buf.writeByte(255);
        return buf;
    }
}
