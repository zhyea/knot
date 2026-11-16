package org.chobit.knot.gateway.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守门：域声明与指纹查询的表清单必须完全一致。
 *
 * <p>两处都是硬编码（枚举的 {@code tables()} 与 {@code ConfigRevisionMapper.xml} 的 SQL），
 * 漂移没有任何运行时症状——少一张表，该域永远探测不到变更，配置改了要等 TTL 过期；
 * 多一张表，只是每拍多一次无用统计。</p>
 */
class ConfigCacheDomainTest {

    @Test
    void declaredTablesMatchRevisionQueryExactly() throws Exception {
        Set<String> probed = tablesInRevisionQuery();

        Set<String> declared = new HashSet<>();
        for (ConfigCacheDomain domain : ConfigCacheDomain.values()) {
            declared.addAll(domain.tables());
        }

        assertEquals(probed, declared,
                "ConfigCacheDomain 的表清单与 ConfigRevisionMapper.xml 必须一致");
    }

    @Test
    void noTableIsSharedByTwoDomains() {
        Set<String> seen = new HashSet<>();
        for (ConfigCacheDomain domain : ConfigCacheDomain.values()) {
            for (String table : domain.tables()) {
                assertTrue(seen.add(table),
                        "表 " + table + " 出现在多个域；同一变更会被重复失效，且归属无法解释");
            }
        }
    }

    @Test
    void everyDomainDeclaresAtLeastOneTable() {
        for (ConfigCacheDomain domain : ConfigCacheDomain.values()) {
            assertTrue(!domain.tables().isEmpty(), "域 " + domain + " 没有声明任何表，永远探测不到变更");
        }
    }

    private static Set<String> tablesInRevisionQuery() throws Exception {
        try (InputStream in = ConfigCacheDomainTest.class
                .getResourceAsStream("/mapper/ConfigRevisionMapper.xml")) {
            assertNotNull(in, "ConfigRevisionMapper.xml 必须在测试 classpath 上");
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("from\\s+(kb_[a-z_]+)").matcher(xml);
            Set<String> tables = new HashSet<>();
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
            assertTrue(!tables.isEmpty(), "未能从 ConfigRevisionMapper.xml 解析出任何表名");
            return tables;
        }
    }
}
