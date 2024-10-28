package org.totemcraft.camera;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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

public class PrimaryThreadSynchronizedPositionSender implements Runnable {

    public final static ReflectionRemapper REFLECTION_REMAPPER = ReflectionRemapper.forReobfMappingsInPaperJar();
    public static int pseudoEntityId = 18640000;

    public final Player player;
    public final Iterator<Camera.Point> points;
    public final Camera.Point startPoint;
    public ScheduledFuture<?> schedule;

    Camera.Point lastPoint;

    boolean cameraMounted = false;

    boolean finished = false;

    private boolean cameraInitialized = false;

    public static void createCamera(Player player, Camera.Point point) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        ClientboundAddEntityPacket addPacket = new ClientboundAddEntityPacket(
                pseudoEntityId, UUID.randomUUID(),
                point.x(), point.y() + 1.0, point.z(),
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

    }

    public static void teleportCamera(Player player, Camera.Point point) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(pseudoEntityId);
        buf.writeDouble(point.x());
        buf.writeDouble(point.y() + 1.0);
        buf.writeDouble(point.z());
        buf.writeByte((byte) (point.yaw() * 256.0F / 360.0F));
        buf.writeByte((byte) (point.pitch() * 256.0F / 360.0F));
        buf.writeBoolean(false);

        ClientboundTeleportEntityPacket teleportPacket = new ClientboundTeleportEntityPacket(buf);
        nmsPlayer.connection.send(teleportPacket);

        FriendlyByteBuf buf1 = new FriendlyByteBuf(Unpooled.buffer());
        buf1.writeVarInt(pseudoEntityId);
        buf1.writeByte((byte) (point.yaw() * 256.0F / 360.0F));
        nmsPlayer.connection.send(new ClientboundRotateHeadPacket(buf1));
    }

    public static void mountCamera(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(pseudoEntityId);
        nmsPlayer.connection.send(new ClientboundSetCameraPacket(msg));
    }

    public static void unmountCamera(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(nmsPlayer.getId());
        nmsPlayer.connection.send(new ClientboundSetCameraPacket(msg));
    }

    public static void removeCamera(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        ClientboundRemoveEntitiesPacket removePkt = new ClientboundRemoveEntitiesPacket(pseudoEntityId);
        nmsPlayer.connection.send(removePkt);
    }

    public PrimaryThreadSynchronizedPositionSender(Player player, List<Camera.Point> points) {
        for (int i = 0; i < 10; i++) {
            if (points.size() <= i) break;
        }

        this.player = player;
        this.points = points.iterator();
        this.startPoint = this.points.next();
    }

    private boolean reuseCamera = false;

    public void reuseCamera(GameMode originalGameMode) {
        reuseCamera = true;
        this.originalGameMode = originalGameMode;
    }

    private boolean keepCamera = false;

    public void keepCamera() {
        keepCamera = true;
    }

    double cameraX, cameraY, cameraZ;

    void initCamera() {
        Camera.Point point = startPoint;

        cameraX = point.x();
        cameraY = point.y();
        cameraZ = point.z();

        if (reuseCamera) {
            // teleport camera
            teleportCamera(player, point);
            cameraMounted = true;
        } else {
            // create camera
            createCamera(player, point);
            originalGameMode = player.getGameMode();
            player.setGameMode(GameMode.SPECTATOR);
            mountCamera(player);
            cameraMounted = true;
        }
    }

    GameMode originalGameMode;

    void destroyCamera() {
        unmountCamera(player);
        removeCamera(player);
        if (originalGameMode != null) player.setGameMode(originalGameMode);
    }

    private boolean finalized = false;

    public void syncTick() {
        if (!cameraInitialized) {
            initCamera();
            cameraInitialized = true;
        }

        if (finished) {
            if (!finalized) {
                finalized = true;
                if (!keepCamera) {
                    destroyCamera();
                }
                onFinish();
            }
            return;
        }

        Camera.Point syncPoint = lastPoint;
        if (syncPoint == null) return;
        ((CraftPlayer) player).getHandle().absMoveTo(syncPoint.x(), syncPoint.y(), syncPoint.z(), (float) syncPoint.yaw(), (float) syncPoint.pitch());
    }

    public void onFinish() {
    }

    @Override
    public void run() {
        if (!cameraInitialized) return;

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

//        short dx = (short) ((lastPoint.x() - cameraX) * 4096.0D);
//        short dy = (short) ((lastPoint.y() - cameraY) * 4096.0D);
//        short dz = (short) ((lastPoint.z() - cameraZ) * 4096.0D);
//
//        int yaw = Mth.floor((lastPoint.yaw() % 360F) * 256.0F / 360.0F);
//
//        int pitch = Mth.floor((lastPoint.pitch() % 360F) * 256.0F / 360.0F);
//
//        Packet<?> pkt = new ClientboundMoveEntityPacket.PosRot(pseudoEntityId
//                , dx, dy, dz, (byte) yaw, (byte) pitch, false);
//        pktHandler.send(pkt);
//
//        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
//        msg.writeVarInt(pseudoEntityId);
//        msg.writeByte((byte) yaw);
//        pkt = new ClientboundRotateHeadPacket(msg);
//        pktHandler.send(pkt);
        teleportCamera(player, lastPoint);

        cameraX = lastPoint.x();
        cameraY = lastPoint.y();
        cameraZ = lastPoint.z();
    }
}
