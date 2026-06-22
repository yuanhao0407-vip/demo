package com.huawei.game.demo.yhchampion.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.nio.charset.StandardCharsets;

public class LengthPrefixedJsonFrameEncoder extends MessageToByteEncoder<String> {
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) {
        out.writeBytes(LengthPrefixedJson.frame(msg).getBytes(StandardCharsets.UTF_8));
    }
}
