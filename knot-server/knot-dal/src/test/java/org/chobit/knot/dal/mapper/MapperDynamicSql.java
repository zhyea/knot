package org.chobit.knot.dal.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用真实 MyBatis {@link Configuration} 载入某个 Mapper 的 XML，取<b>动态节点展开后</b>的 SQL。
 *
 * <p><b>为什么能在纯单测里跑</b>：mybatis 3.5.x 自带 shaded 的 {@code org.apache.ibatis.ognl}
 * （外部 ognl 是 optional 依赖、不传递），所以 {@code <if>} / {@code <foreach>} / {@code <where>}
 * 的求值既不需要数据库，也不需要 Spring 上下文。</p>
 *
 * <p><b>为什么值得测</b>：service 单测里 Mapper 全是 mock，真启只证明 XML「能被解析」。
 * 于是下面三种错法在「1296 例全绿 + 双 app 真启零 ERROR」之后<b>仍然可以是坏的</b>：
 * {@code <if test>} 表达式写错、参数名对不上集合、条件被求值成 false 而<b>静默丢弃</b>
 * （判例：{@code status != ''} 吃掉了状态 0，筛选静默返回全量）。</p>
 *
 * <p><b>不判什么</b>：列名、join、{@code now(3)} 的时区口径、MySQL 方言下的
 * {@code order by ... limit} 是否合法——仍只能靠 {@code schema.sql} 启动执行 + 真库冒烟
 * （见 {@link MapperStatementCoverageTest} 类注释）。这里只补「参数绑定与动态节点」这一块。</p>
 *
 * <p>namespace 与 XML 路径都由接口类反推，不留第二个包名/文件名字面量：
 * 字符串形式的包名不参与编译校验，是包迁移时唯一会静默失效的口径。</p>
 */
final class MapperDynamicSql {

    /** {@code where|and <列> = ?}：允许任意空白，值必须是占位符（状态码不许拼进 SQL） */
    private static final String EQUALS_TEMPLATE = "(?i)\\b(where|and)\\s+%s\\s*=\\s*\\?";

    private final Configuration configuration;
    private final String namespace;

    private MapperDynamicSql(Configuration configuration, Class<?> mapperInterface) {
        this.configuration = configuration;
        this.namespace = mapperInterface.getName();
    }

    /**
     * 解析 {@code mapper/<接口简名>.xml}。
     *
     * <p>⚠ 刻意不做缓存：{@link Configuration} 是有状态的（statement 注册表 + 片段表），
     * 跨测试复用会让「前一个测试解析成功」掩盖后一个测试的解析失败。XML 只有几十 KB，不值得省。</p>
     */
    static MapperDynamicSql of(Class<?> mapperInterface) {
        String resource = "mapper/" + mapperInterface.getSimpleName() + ".xml";
        Configuration configuration = new Configuration();
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + resource + " 失败", e);
        }
        return new MapperDynamicSql(configuration, mapperInterface);
    }

    /** 展开后的单行 SQL（空白压平，便于做正则断言） */
    String sql(String id, Map<String, Object> params) {
        MappedStatement statement = configuration.getMappedStatement(namespace + "." + id);
        return statement.getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
    }

    /**
     * {@code where|and <列> = ?} 的判据。
     *
     * <p>⚠ 必须同时接受 {@code where}：{@code <where>} 标签会把<b>紧跟其后的 {@code and} 剥掉</b>，
     * 所以「只传该条件」时展开结果是 {@code WHERE x = ?} 而不是 {@code AND x = ?}。
     * 只写 {@code and} 会让「修好 XML 之后测试仍然红」，而红的原因已经从「条件丢了」
     * 变成「判据写窄了」——这两种红必须分开看。</p>
     */
    static Pattern equalTo(String column) {
        return Pattern.compile(String.format(EQUALS_TEMPLATE, Pattern.quote(column)));
    }

    /**
     * 钉住「XML 真被解析到了」。
     *
     * <p>XML 路径写错、namespace 拼错、接口加了方法忘写语句，都会让同类的条件断言变成
     * 「没有 SQL 就没有违规」的<b>空跑绿灯</b>。这里用「解析出的 statement 数 ==
     * 接口抽象方法数」把空跑堵死。</p>
     */
    void assertParsedEveryInterfaceMethod() {
        long methods = 0;
        for (Method method : mapperInterface().getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || method.isDefault()) {
                continue;
            }
            methods++;
        }
        long statements = configuration.getMappedStatementNames().stream()
                .filter(name -> name.startsWith(namespace + "."))
                .count();
        assertEquals(methods, statements,
                namespace + " 解析出的 statement 数与接口抽象方法数不一致——"
                        + "XML 没被解析到（路径/namespace 写错），或接口加了方法没写语句");
    }

    private Class<?> mapperInterface() {
        try {
            return Class.forName(namespace);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到 Mapper 接口：" + namespace, e);
        }
    }

    /**
     * 允许 <b>null 值</b> 的入参表。
     *
     * <p>不能用 {@code Map.of}：它在构造期就对 null 抛 NPE，而「筛选条件留空」
     * 恰恰是把参数传成 null 的常规场景。</p>
     */
    static Map<String, Object> params(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("key/value 必须成对出现");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return params;
    }
}
