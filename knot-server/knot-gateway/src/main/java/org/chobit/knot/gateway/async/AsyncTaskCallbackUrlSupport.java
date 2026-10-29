package org.chobit.knot.gateway.async;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * {@code callback_url} 的入口守卫：只放行<b>可安全出站</b>的地址。
 *
 * <p>⭐⭐ <b>为什么必须有这一层</b>：{@code callback_url} 是<b>客户端指定、由网关发起</b>的
 * 出站目标。不加限制时，任何人只要提交一条带 {@code callback_url=http://10.0.0.5/admin}
 * 的视频任务，就能让网关带着业务数据向内部服务发 POST —— 这是典型的 SSRF。
 * 原先这段地址被整体剥掉，安全问题不存在；现在要支持回调，就必须把它当成一条安全边界守。</p>
 *
 * <p>守卫分两层：</p>
 * <ol>
 *   <li><b>一律拦截</b>回环 / 私网 / 链路本地 / CGNAT / 保留地址（含明文 IPv4 与 IPv6）；
 *       跳过它等于允许任意内网访问；</li>
 *   <li><b>可选主机白名单</b>（{@code callback-allowed-hosts}，后缀匹配）：
 *       配了就只允许这些域，适合「只允许合作方回调域」的部署。</li>
 * </ol>
 *
 * <p>⚠ <b>残余风险如实登记</b>：本类只做字符串层面的地址判定，<b>不做 DNS 解析后的 IP 复核</b>，
 * 因此攻击者若控制一个公网域名、把 A 记录指向内网（DNS rebinding），仍可绕过。
 * 要彻底收口需要「解析 → 判 IP → 用解析结果直连并锁 Host」，代价是引入自定义 HTTP 传输层。
 * 当前选择：默认拦截字面内网地址 + 提供白名单配置；对公网开放回调的场景，运维应配白名单。</p>
 */
public final class AsyncTaskCallbackUrlSupport {

    private AsyncTaskCallbackUrlSupport() {
    }

    /**
     * 归一化并校验一个回调地址。
     *
     * @param raw           客户端请求体里取到的原始值
     * @param allowedHosts  主机白名单（后缀匹配，空/null = 不限制主机，但仍拦内网地址）
     * @return 可安全投递的地址（已 trim）；不合法或不安全时返回 {@code null}
     */
    public static String normalize(String raw, List<String> allowedHosts) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        // 只允许 http/https：file:/ftp: 之类既不是回调语义，也可能被用来读本地文件
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            return null;
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            return null;
        }
        String normalizedHost = stripBrackets(host.toLowerCase(Locale.ROOT));
        if (isBlockedHost(normalizedHost)) {
            return null;
        }
        if (allowedHosts != null && !allowedHosts.isEmpty() && !hostAllowed(normalizedHost, allowedHosts)) {
            return null;
        }
        return trimmed;
    }

    /** 是否是被禁的内部地址（回环 / 私网 / 链路本地 / CGNAT / 保留） */
    static boolean isBlockedHost(String host) {
        if (host == null) {
            return true;
        }
        if (host.equals("localhost") || host.endsWith(".localhost")) {
            return true;
        }
        if (host.contains(":")) {
            // IPv6 字面量：只放行全球单播，回环/未指定/链路本地/唯一本地一律拦
            return host.equals("::1") || host.equals("::")
                    || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")
                    || host.startsWith("::ffff:");
        }
        return isBlockedIpv4(host);
    }

    private static boolean isBlockedIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            // 不是 IPv4 字面量 —— 域名交给白名单那层决定，这里不拦
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false; // 不是合法 IPv4（可能只是含点的域名），不在此拦截
            }
            if (octets[i] < 0 || octets[i] > 255) {
                return false;
            }
        }
        int first = octets[0];
        int second = octets[1];
        return first == 0                       // 0.0.0.0/8
                || first == 10                  // 10.0.0.0/8
                || first == 127                 // 127.0.0.0/8 回环
                || (first == 169 && second == 254)  // 169.254.0.0/16 链路本地
                || (first == 172 && second >= 16 && second <= 31) // 172.16.0.0/12
                || (first == 192 && second == 168)  // 192.168.0.0/16
                || (first == 100 && second >= 64 && second <= 127); // 100.64.0.0/10 CGNAT
    }

    private static boolean hostAllowed(String host, List<String> allowedHosts) {
        for (String allowed : allowedHosts) {
            if (StringUtils.isBlank(allowed)) {
                continue;
            }
            String normalized = stripBrackets(allowed.trim().toLowerCase(Locale.ROOT));
            if (host.equals(normalized) || host.endsWith("." + normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
}
