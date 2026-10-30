package xyz.mdkgameteam.portmux;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

public class MDKChannelInspector extends ByteToMessageDecoder {
    private final String geyserHost;
    private final int geyserPort;
    private final Logger logger;

    public MDKChannelInspector(String geyserHost, int geyserPort, Logger logger) {
        this.geyserHost = geyserHost;
        this.geyserPort = geyserPort;
        this.logger = logger;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) {
            return; // Esperar al menos 4 bytes para identificar el protocolo
        }

        byte b0 = in.getByte(in.readerIndex());
        byte b1 = in.getByte(in.readerIndex() + 1);
        byte b2 = in.getByte(in.readerIndex() + 2);
        byte b3 = in.getByte(in.readerIndex() + 3);

        boolean isHttp = (b0 == 'G' && b1 == 'E' && b2 == 'T') ||
                         (b0 == 'P' && b1 == 'O' && b2 == 'S' && b3 == 'T') ||
                         (b0 == 'O' && b1 == 'P' && b2 == 'T');

        Channel clientChannel = ctx.channel();

        if (isHttp) {
            // Detectado Minecraft Bedrock (NetherNet HTTP Signaling)
            ctx.pipeline().remove(this);
            cleanPaperHandlers(ctx.pipeline());

            clientChannel.config().setAutoRead(false);

            // Conectar al servidor HTTP de señalización de Geyser
            Bootstrap b = new Bootstrap();
            b.group(clientChannel.eventLoop())
             .channel(clientChannel.getClass())
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel geyserCh) {
                     geyserCh.pipeline().addLast(new NettyRelayHandler(clientChannel));
                 }
             });

            b.connect(geyserHost, geyserPort).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    Channel geyserCh = f.channel();
                    clientChannel.pipeline().addLast(new NettyRelayHandler(geyserCh));

                    // Enviar cabecera HAProxy PROXY Protocol v1 para pasar la IP real del jugador
                    sendProxyHeader(clientChannel, geyserCh);

                    // Reenviar los bytes iniciales que ya leyo el buffer
                    ByteBuf initial = in.readBytes(in.readableBytes());
                    geyserCh.writeAndFlush(initial);

                    clientChannel.config().setAutoRead(true);
                } else {
                    clientChannel.close();
                }
            });
        } else {
            // Detectado Minecraft Java
            ctx.pipeline().remove(this);
            // Pasar los datos directamente al pipeline de Paper
            out.add(in.readBytes(in.readableBytes()));
        }
    }

    private void sendProxyHeader(Channel clientChannel, Channel geyserCh) {
        try {
            SocketAddress remote = clientChannel.remoteAddress();
            SocketAddress local = clientChannel.localAddress();
            if (remote instanceof InetSocketAddress rem && local instanceof InetSocketAddress loc) {
                boolean isIpv6 = rem.getAddress().getAddress().length == 16;
                String proto = isIpv6 ? "TCP6" : "TCP4";
                String srcIp = rem.getAddress().getHostAddress();
                String dstIp = loc.getAddress().getHostAddress();
                int srcPort = rem.getPort();
                int dstPort = loc.getPort();
                String header = "PROXY " + proto + " " + srcIp + " " + dstIp + " " + srcPort + " " + dstPort + "\r\n";
                byte[] bytes = header.getBytes(StandardCharsets.US_ASCII);
                ByteBuf buf = geyserCh.alloc().buffer(bytes.length);
                buf.writeBytes(bytes);
                geyserCh.writeAndFlush(buf);
            }
        } catch (Exception ignored) {}
    }

    private void cleanPaperHandlers(ChannelPipeline p) {
        String[] toRemove = {"timeout", "legacy_query", "splitter", "decoder", "prepender", "encoder", "packet_handler"};
        for (String name : toRemove) {
            if (p.get(name) != null) {
                try {
                    p.remove(name);
                } catch (Exception ignored) {}
            }
        }
    }
}
