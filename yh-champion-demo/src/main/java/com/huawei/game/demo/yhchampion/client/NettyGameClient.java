package com.huawei.game.demo.yhchampion.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.huawei.game.demo.yhchampion.protocol.LengthPrefixedJsonFrameDecoder;
import com.huawei.game.demo.yhchampion.protocol.LengthPrefixedJsonFrameEncoder;
import com.huawei.game.demo.yhchampion.protocol.ProtocolMessages;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NettyGameClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NettyGameClient.class);

    private final GameClientConfig config;
    private final DemoAgent agent;
    private final NioEventLoopGroup group = new NioEventLoopGroup(1);

    public NettyGameClient(GameClientConfig config, DemoAgent agent) {
        this.config = config;
        this.agent = agent;
    }

    public void runUntilClosed() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new LengthPrefixedJsonFrameDecoder())
                                .addLast(new LengthPrefixedJsonFrameEncoder())
                                .addLast(new GameMessageHandler(config, agent));
                    }
        });

        ChannelFuture connectFuture = bootstrap.connect(config.backendHost(), config.backendPort()).sync();
        LOG.info("已连接后端 {}:{}，当前客户端 playerId={}", config.backendHost(), config.backendPort(),
                config.playerId());
        connectFuture.channel().closeFuture().sync();
    }

    @Override
    public void close() {
        group.shutdownGracefully();
    }

    private static final class GameMessageHandler extends SimpleChannelInboundHandler<String> {
        private final GameClientConfig config;
        private final DemoAgent agent;

        private GameMessageHandler(GameClientConfig config, DemoAgent agent) {
            this.config = config;
            this.agent = agent;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            LOG.info("连接建立，发送 registration。playerId={}, playerName={}, version={}",
                    config.playerId(), config.playerName(), config.version());
            ctx.writeAndFlush(ProtocolMessages.registration(config.playerId(), config.playerName(), config.version()));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String json) {
            // 后端所有消息都使用 msg_name/msg_data 包裹，业务层只关心消息名和数据体。
            JSONObject root = JSON.parseObject(json);
            String messageName = root.getString("msg_name");
            JSONObject data = root.getJSONObject("msg_data");
            if (data == null) {
                data = new JSONObject();
            }
            switch (messageName) {
                case "start":
                    LOG.info("收到 start，初始化 Agent 并发送 ready。playerId={}", config.playerId());
                    agent.onStart(data);
                    int startRound = data.getIntValue("round");
                    ctx.writeAndFlush(ProtocolMessages.ready(config.playerId(), data.getString("matchId"),
                            startRound > 0 ? startRound : 1));
                    break;
                case "inquire":
                    LOG.info("收到 inquire。matchId={}, round={}", data.getString("matchId"),
                            data.getIntValue("round"));
                    String action = agent.onInquire(data);
                    LOG.info("发送 action。{}", actionSummary(action));
                    ctx.writeAndFlush(action);
                    break;
                case "error":
                    agent.onError(data);
                    LOG.warn("后端返回 error：{}", data);
                    break;
                case "over":
                    LOG.info("收到 over，准备关闭客户端连接。{}", data);
                    agent.onOver(data);
                    ctx.close();
                    break;
                default:
                    LOG.info("忽略未知后端消息 msg_name={}，msg_data={}", messageName, data);
                    break;
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.error("客户端链路异常，关闭连接。", cause);
            ctx.close();
        }

        private String actionSummary(String actionJson) {
            JSONObject root = JSON.parseObject(actionJson);
            JSONObject data = root.getJSONObject("msg_data");
            JSONObject firstAction = data.getJSONArray("actions").getJSONObject(0);
            return "matchId=" + data.getString("matchId")
                    + ", round=" + data.getIntValue("round")
                    + ", action=" + firstAction.getString("action")
                    + ", target=" + firstAction.getString("targetNodeId")
                    + ", resource=" + firstAction.getString("resourceType")
                    + ", task=" + firstAction.getString("taskId");
        }
    }
}
