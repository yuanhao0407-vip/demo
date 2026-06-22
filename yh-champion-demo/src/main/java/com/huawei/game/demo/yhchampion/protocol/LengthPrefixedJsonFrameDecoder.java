package com.huawei.game.demo.yhchampion.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LengthPrefixedJsonFrameDecoder extends ByteToMessageDecoder {
    private static final int PREFIX_LENGTH = 5;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < PREFIX_LENGTH) {
            return;
        }
        in.markReaderIndex();
        byte[] prefixBytes = new byte[PREFIX_LENGTH];
        in.readBytes(prefixBytes);
        int bodyLength = Integer.parseInt(new String(prefixBytes, StandardCharsets.UTF_8));
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }
        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);
        out.add(new String(bodyBytes, StandardCharsets.UTF_8));
    }
}
