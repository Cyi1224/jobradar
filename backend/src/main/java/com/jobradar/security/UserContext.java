package com.jobradar.security;

/** 每个请求线程内保存当前登录用户 id，由 JwtAuthFilter 设置/清理。 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long userId) { CURRENT.set(userId); }
    public static void clear() { CURRENT.remove(); }
    public static Long get() { return CURRENT.get(); }

    /** 取当前用户 id；未登录时抛异常（受保护接口理论上不会走到这里）。 */
    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) throw new IllegalStateException("未登录");
        return id;
    }
}
