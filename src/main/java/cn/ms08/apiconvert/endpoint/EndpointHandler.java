package cn.ms08.apiconvert.endpoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 端点策略接口，每个公开 API 端点实现此接口独立处理请求接收、适配和响应写出。
 */
public interface EndpointHandler {

    /**
     * 当前处理器支持的端点类型，EndpointRegistry 根据此值注册。
     */
    EndpointType endpointType();

    /**
     * 处理请求：读取请求体、调用网关/服务、写出响应（包括 SSE 流式）。
     */
    void handle(HttpServletRequest request, HttpServletResponse response) throws IOException;
}
