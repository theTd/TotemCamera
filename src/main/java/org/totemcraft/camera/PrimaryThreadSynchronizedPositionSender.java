package org.totemcraft.camera;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joor.Reflect;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.util.*;
import java.util.concurrent.ScheduledFuture;

public class PrimaryThreadSynchronizedPositionSender implements Runnable {

    public final static ReflectionRemapper REFLECTION_REMAPPER = ReflectionRemapper.forReobfMappingsInPaperJar();
    public static int pseudoEntityId = 18640000;

    public final Player player;
    public final ListIterator<Camera.Point> points;
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
                EntityType.ACACIA_BOAT, 0,
                Vec3.ZERO, point.yaw()
        );
        ClientboundTeleportEntityPacket posPkt = new ClientboundTeleportEntityPacket(pseudoEntityId, new PositionMoveRotation(
                new Vec3(point.x(), point.y() + 1.0, point.z()),
                Vec3.ZERO, (float) point.yaw(), (float) point.pitch()
        ), Collections.emptySet(), false);

        List<SynchedEntityData.DataValue<?>> dataValues = new ArrayList<>();
//        dataValues.add(new SynchedEntityData.DataValue<>(
//                ReflectionUtil.DATA_SHARED_FLAGS_ID.id(),
//                EntityDataSerializers.BYTE, (byte) (1 << 5) // invisible
//        ));
        dataValues.add(new SynchedEntityData.DataValue<>(
                ReflectionUtil.DATA_NO_GRAVITY.id(),
                EntityDataSerializers.BOOLEAN, true
        ));
//        dataValues.add(new SynchedEntityData.DataValue<>(
//                ReflectionUtil.SLIME_DATA_ID_SIZE.id(),
//                EntityDataSerializers.INT, 1
//        ));

        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(pseudoEntityId, dataValues);

        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        packets.add(addPacket);
        packets.add(posPkt);
        packets.add(dataPacket);
        nmsPlayer.connection.send(new ClientboundBundlePacket(packets));
    }

    public static void teleportCamera(Player player, Camera.Point point) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        ClientboundTeleportEntityPacket tpPkt = new ClientboundTeleportEntityPacket(pseudoEntityId, new PositionMoveRotation(
                new Vec3(point.x(), point.y() + 1.0, point.z()),
                Vec3.ZERO, ((float) point.yaw()), ((float) point.pitch())
        ), Collections.emptySet(), false);
//        ClientboundEntityPositionSyncPacket tpPkt = new ClientboundEntityPositionSyncPacket(pseudoEntityId, , false);
        nmsPlayer.connection.send(tpPkt);
    }

    public void moveCamera(Player player, Camera.Point point, @Nullable Camera.Point nextPoint) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

        float yaw = (float) point.yaw();
        float pitch = (float) point.pitch();

        Vec3 deltaMovement = Vec3.ZERO;
        if (nextPoint != null) {
            deltaMovement = new Vec3(
                    nextPoint.x() - point.x(),
                    nextPoint.y() - point.y(),
                    nextPoint.z() - point.z()
            );
        }
        ClientboundEntityPositionSyncPacket posPkt = new ClientboundEntityPositionSyncPacket(pseudoEntityId, new PositionMoveRotation(
                new Vec3(point.x(), point.y() + 1.0, point.z()),
                deltaMovement, yaw, pitch
        ), false);
        nmsPlayer.connection.send(posPkt);
    }

    public static void mountCamera(Player player) {
        player.setGameMode(GameMode.SPECTATOR);

        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(pseudoEntityId);
        nmsPlayer.connection.send(ClientboundSetCameraPacket.STREAM_CODEC.decode(msg));

        // set helmet to pumpkin
        ClientboundContainerSetSlotPacket pkt = new ClientboundContainerSetSlotPacket(
                0, nmsPlayer.inventoryMenu.getStateId(), 5, CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(Material.CARVED_PUMPKIN))
        );
        nmsPlayer.connection.send(pkt);
    }

    public static void unmountCamera(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(nmsPlayer.getId());
        nmsPlayer.connection.send(ClientboundSetCameraPacket.STREAM_CODEC.decode(msg));

        // set helmet back
        ItemStack helmet = player.getInventory().getHelmet();
        ClientboundContainerSetSlotPacket pkt = new ClientboundContainerSetSlotPacket(
                0, nmsPlayer.inventoryMenu.getStateId(), 5, CraftItemStack.asNMSCopy(Objects.requireNonNullElseGet(helmet, () -> new org.bukkit.inventory.ItemStack(Material.AIR)))
        );
        nmsPlayer.connection.send(pkt);

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
        this.points = points.listIterator();
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
        Camera.Point nextPoint = null;
        if (points.hasNext()) {
            nextPoint = points.next();
            points.previous();
        }

        ServerGamePacketListenerImpl pktHandler = ((CraftPlayer) player).getHandle().connection;
        int awaitingTeleport = Reflect.on(pktHandler).get(REFLECTION_REMAPPER.remapFieldName(ServerGamePacketListenerImpl.class, "awaitingTeleport"));

        Reflect.on(pktHandler).set(REFLECTION_REMAPPER.remapFieldName(ServerGamePacketListenerImpl.class, "awaitingTeleport"), ++awaitingTeleport);
        moveCamera(player, lastPoint, nextPoint);

        cameraX = lastPoint.x();
        cameraY = lastPoint.y();
        cameraZ = lastPoint.z();
    }
}
