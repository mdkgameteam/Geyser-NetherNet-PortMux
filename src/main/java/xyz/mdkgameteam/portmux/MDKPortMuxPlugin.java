package xyz.mdkgameteam.portmux;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class MDKPortMuxPlugin extends JavaPlugin {
    private static final String GEYSER_HOST = "127.0.0.1";
    private static final int GEYSER_PORT = 19132;
    private boolean injected = false;

    @Override
    public void onEnable() {
        getLogger().info("Inicializando MDKPortMux (Multiplexor Dual-Stack Java/Bedrock)...");
        
        // Wait 1 tick to ensure ServerConnection has finished binding listening sockets
        Bukkit.getScheduler().runTask(this, this::inject);
    }

    private void inject() {
        try {
            Object serverConnection = getServerConnection();
            if (serverConnection == null) {
                getLogger().severe("No se pudo encontrar ServerConnection de Paper!");
                return;
            }

            List<ChannelFuture> channels = getChannelFutures(serverConnection);
            if (channels.isEmpty()) {
                getLogger().warning("No se encontraron ChannelFutures abiertos aun, reintentando en 1 segundo...");
                Bukkit.getScheduler().runTaskLater(this, this::inject, 20L);
                return;
            }

            int count = 0;
            for (ChannelFuture future : channels) {
                Channel ch = future.channel();
                ch.pipeline().addFirst("mdk-port-mux-acceptor", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof Channel clientChannel) {
                            clientChannel.pipeline().addFirst("mdk-port-mux-inspector", 
                                    new MDKChannelInspector(GEYSER_HOST, GEYSER_PORT, getLogger()));
                        }
                        super.channelRead(ctx, msg);
                    }
                });
                count++;
            }

            injected = true;
            getLogger().info("Multiplexor inyectado con exito en " + count + " canal(es) de Paper!");
            getLogger().info("Trafico HTTP (Bedrock NetherNet) -> 127.0.0.1:" + GEYSER_PORT);
            getLogger().info("Trafico Minecraft Java -> Pipeline nativo de Paper");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error inyectando MDKPortMux: " + e.getMessage(), e);
        }
    }

    private Object getServerConnection() {
        try {
            Server server = Bukkit.getServer();
            Method getServerMethod = server.getClass().getMethod("getServer");
            Object mcServer = getServerMethod.invoke(server);

            for (Method m : mcServer.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().contains("Connection")) {
                    return m.invoke(mcServer);
                }
            }
            for (Method m : mcServer.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().contains("Connection")) {
                    m.setAccessible(true);
                    return m.invoke(mcServer);
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error al obtener ServerConnection: " + e.getMessage(), e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<ChannelFuture> getChannelFutures(Object serverConnection) {
        if (serverConnection == null) return Collections.emptyList();
        for (Field f : serverConnection.getClass().getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                Type genericType = f.getGenericType();
                if (genericType instanceof ParameterizedType pt) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0].getTypeName().contains("ChannelFuture")) {
                        f.setAccessible(true);
                        try {
                            return (List<ChannelFuture>) f.get(serverConnection);
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Error accediendo a ChannelFutures: " + e.getMessage(), e);
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }
}
