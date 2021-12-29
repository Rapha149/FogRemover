package de.rapha149.clearfog;

import de.rapha149.clearfog.version.VersionWrapper;
import io.netty.channel.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

public class Util {

    private static final String HANDLER_NAME = "ClearFog";
    public static FileConfiguration config;
    public static VersionWrapper WRAPPER;

    public static int checkViewDistance(int distance) {
        return Math.max(1, distance);
    }

    public static void checkViewDistances() {
        if (config.getBoolean("default.enabled")) {
            int viewDistance = config.getInt("default.view-distance");
            if (viewDistance < 1)
                ClearFog.getInstance().getLogger().warning("The view distance set in the config is invalid. It has to be between 2 and 32.");
        }
        if (config.getBoolean("individual.enabled")) {
            config.getConfigurationSection("individual.players").getKeys(false).forEach(uuid -> {
                int viewDistance = config.getInt("individual.players." + uuid);
                if (viewDistance < 1)
                    ClearFog.getInstance().getLogger().warning("The individual view distance for " + uuid +
                                                               " set in the config is invalid. It has to be between 2 and 32.");
            });
        }
    }

    public static void registerHandler() throws NoSuchFieldException, IllegalAccessException {
        if (!config.getBoolean("default.enabled") && !config.getBoolean("individual.enabled"))
            return;

        ChannelHandler packetInit = new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel channel) {
                channel.eventLoop().submit(() -> {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (!pipeline.names().contains(HANDLER_NAME)) {
                        pipeline.addAfter("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {

                            private UUID player;

                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                try {
                                    if (msg.getClass() == WRAPPER.getLoginSuccessPacketClass())
                                        player = WRAPPER.getUUIDFromLoginPacket(msg);

                                    if (msg.getClass() == WRAPPER.getLoginPlayPacketClass() ||
                                        msg.getClass() == WRAPPER.getUpdateViewDistanceClass()) {
                                        int viewDistance = -1;
                                        if (config.getBoolean("default.enabled"))
                                            viewDistance = config.getInt("default.view-distance");
                                        if (player != null && config.getBoolean("individual.enabled") &&
                                            config.isSet("individual.players." + player)) {
                                            viewDistance = config.getInt("individual.players." + player);
                                        }

                                        if (viewDistance != -1)
                                            msg = WRAPPER.replaceViewDistance(msg, checkViewDistance(viewDistance));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                super.write(ctx, msg, promise);
                            }
                        });
                    }
                });
            }
        };
        ChannelHandler init = new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel channel) {
                channel.pipeline().addLast(packetInit);
            }
        };
        ChannelHandler handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ((Channel) msg).pipeline().addFirst(init);
                ctx.fireChannelRead(msg);
            }
        };
        WRAPPER.getServerPipelines().forEach(pipeline -> {
            if (pipeline.names().contains(HANDLER_NAME))
                pipeline.remove(HANDLER_NAME);
            pipeline.addFirst(HANDLER_NAME, handler);
        });
    }

    public static void unregisterHandler() throws NoSuchFieldException, IllegalAccessException {
        if (WRAPPER != null)
            WRAPPER.getServerPipelines().forEach(pipeline -> {
                if (pipeline.names().contains(HANDLER_NAME))
                    pipeline.remove(HANDLER_NAME);
            });
    }
}
