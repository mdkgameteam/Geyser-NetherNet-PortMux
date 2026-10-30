package xyz.mdkgameteam.portmux;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class NettyRelayHandler extends ChannelInboundHandlerAdapter {
    private final Channel target;

    public NettyRelayHandler(Channel target) {
        this.target = target;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        target.writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (target.isActive()) {
            target.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
        if (target.isActive()) {
            target.close();
        }
    }
}
