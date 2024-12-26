package com.tuachotu.http.handlers

import io.netty.channel.{ChannelHandlerContext, ChannelInitializer, SimpleChannelInboundHandler}
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http._
import io.netty.buffer.Unpooled
import com.tuachotu.http.core.RouteRegistry
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

class HttpHandler extends ChannelInitializer[SocketChannel] {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  override def initChannel(ch: SocketChannel): Unit = {
    val pipeline = ch.pipeline()
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpObjectAggregator(512 * 1024)) // MAX size allowed in HTTP method
    pipeline.addLast(new SimpleChannelInboundHandler[FullHttpRequest] {
      override def channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest): Unit = {
        LoggerUtil.info("Received request", "method", request.method(), "uri", request.uri())
        val route = RouteRegistry.getRoutes.find(r => r.path == request.uri() && r.method == request.method())
        route match {
          case Some(r) =>
            LoggerUtil.info("Matched route", "path",  r.path)
            val response = r.handle(request)
            LoggerUtil.info("Response content size", "size", response.content().readableBytes())
            LoggerUtil.info("Response headers", "headers", response.headers())
            ctx.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE)
          case None =>
            LoggerUtil.info("No route matched")
            val notFoundContent = Unpooled.copiedBuffer("""{"error": "Not Found"}""".getBytes())
            val response = new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1,
              HttpResponseStatus.NOT_FOUND,
              notFoundContent
            )
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, notFoundContent.readableBytes())
            ctx.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE)
        }
      }
    })
  }
}