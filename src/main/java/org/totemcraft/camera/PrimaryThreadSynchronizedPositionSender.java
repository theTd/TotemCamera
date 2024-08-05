package org.totemcraft.camera;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joor.Reflect;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

class PrimaryThreadSynchronizedPositionSender implements Runnable {

    final static ReflectionRemapper REFLECTION_REMAPPER = ReflectionRemapper.forReobfMappingsInPaperJar();

    final Player player;
    final Iterator<Camera.Point> points;
    final Camera.Point startPoint;
    ScheduledFuture<?> schedule;

    Camera.Point lastPoint;

    boolean cameraMounted = false;

    boolean finished = false;

    PrimaryThreadSynchronizedPositionSender(Player player, List<Camera.Point> points) {
        for (int i = 0; i < 10; i++) {
            if (points.size() <= i) break;
        }

        this.player = player;
        this.points = points.iterator();
        this.startPoint = this.points.next();

        initCamera();
    }

    int pseudoEntityId = 18640000;

    double cameraX, cameraY, cameraZ;

    void initCamera() {
        Camera.Point point = startPoint;
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

        cameraX = point.x();
        cameraY = point.y();
        cameraZ = point.z();
        ClientboundAddEntityPacket addPacket = new ClientboundAddEntityPacket(
                pseudoEntityId, UUID.randomUUID(),
                point.x(), point.y(), point.z(),
                (float) point.yaw(), (float) point.pitch(),
                EntityType.SLIME, 0,
                Vec3.ZERO
        );
        nmsPlayer.connection.send(addPacket);

        FriendlyByteBuf buf = new EntityDataWriter(pseudoEntityId)
                .write(ReflectionUtil.DATA_SHARED_FLAGS_ID, (byte) (1 << 5)) // invisible
                .write(ReflectionUtil.DATA_NO_GRAVITY, true)
                .write(ReflectionUtil.SLIME_DATA_ID_SIZE, 1)
                .create();

        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(buf);
        nmsPlayer.connection.send(dataPacket);

        originalGameMode = player.getGameMode();
        player.setGameMode(GameMode.SPECTATOR);

        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(pseudoEntityId);
        nmsPlayer.connection.send(new ClientboundSetCameraPacket(msg));
        cameraMounted = true;
    }

    GameMode originalGameMode;

    void destroyCamera() {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(nmsPlayer.getId());
        nmsPlayer.connection.send(new ClientboundSetCameraPacket(msg));

        ClientboundRemoveEntitiesPacket removePkt = new ClientboundRemoveEntitiesPacket(pseudoEntityId);
        nmsPlayer.connection.send(removePkt);

        if (originalGameMode != null) player.setGameMode(originalGameMode);
    }

    void syncTick() {
        if (finished) {
            destroyCamera();
            return;
        }

        Camera.Point syncPoint = lastPoint;
        if (syncPoint == null) return;
        ((CraftPlayer) player).getHandle().absMoveTo(syncPoint.x(), syncPoint.y(), syncPoint.z(), (float) syncPoint.yaw(), (float) syncPoint.pitch());
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            finished = true;
            schedule.cancel(false);
            return;
        }

        if (!points.hasNext()) {
            finished = true;
            schedule.cancel(false);
            return;
        }

        lastPoint = points.next();

        ServerGamePacketListenerImpl pktHandler = (ServerGamePacketListenerImpl) ((CraftPlayer) player).getHandle().networkManager.getPacketListener();
        int awaitingTeleport = Reflect.on(pktHandler).get(REFLECTION_REMAPPER.remapFieldName(ServerGamePacketListenerImpl.class, "awaitingTeleport"));

        Reflect.on(pktHandler).set(REFLECTION_REMAPPER.remapFieldName(ServerGamePacketListenerImpl.class, "awaitingTeleport"), ++awaitingTeleport);

        short dx = (short) ((lastPoint.x() - cameraX) * 4096.0D);
        short dy = (short) ((lastPoint.y() - cameraY) * 4096.0D);
        short dz = (short) ((lastPoint.z() - cameraZ) * 4096.0D);

        int yaw = Mth.floor((lastPoint.yaw() % 360F) * 256.0F / 360.0F);

        int pitch = Mth.floor((lastPoint.pitch() % 360F) * 256.0F / 360.0F);

        Packet<?> pkt = new ClientboundMoveEntityPacket.PosRot(pseudoEntityId
                , dx, dy, dz, (byte) yaw, (byte) pitch, false);
        pktHandler.send(pkt);

        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(pseudoEntityId);
        msg.writeByte((byte) yaw);
        pkt = new ClientboundRotateHeadPacket(msg);
        pktHandler.send(pkt);

        cameraX = lastPoint.x();
        cameraY = lastPoint.y();
        cameraZ = lastPoint.z();
    }
}
