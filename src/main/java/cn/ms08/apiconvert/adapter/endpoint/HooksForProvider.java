package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.provider.ProviderType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 {@link ProviderHook} 归属的供应商类型，{@link ProviderHookRegistry} 据此建立索引。
 * <p>
 * 一个供应商可以注册多个 hook（用 {@link java.util.List} 形式），但通常一个供应商一个 hook
 * 就够了——hook 内部按需处理多个端点协议。
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HooksForProvider {
    ProviderType value();
}
