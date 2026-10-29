package org.chobit.knot.gateway.archive;

import org.chobit.knot.gateway.archive.ArchiveAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认转储实现的契约：<b>原样返回，不改写</b>。
 *
 * <p>这条看着像废话，但它守的是一个真会发生的退化：有人觉得「既然是转储层，顺手做个签名代理 /
 * 加个统计参数 / 换成本地缓存路径」更贴心——于是没有对象存储的部署里，客户端拿到的地址
 * 悄悄变了一个。判据很简单：{@code passthrough} 的输出必须与输入<b>逐字相等</b>。</p>
 */
class PassthroughArchiveStoreTest {

    @Test
    @DisplayName("默认实现逐字返回供应商地址")
    void returnsSourceUrlAsIs() {
        ArchiveAsset asset = new ArchiveAsset("tc-1", "up-1",
                "https://up.example/a.mp4?sign=abc123&expires=1730000000",
                "kling-3", "kling-3-turbo", "kling", "rule-1",
                LocalDateTime.of(2026, 10, 28, 12, 0));

        assertEquals(asset.sourceUrl(), new PassthroughArchiveStore().archive(asset),
                "默认实现不得改写地址：没有对象存储时它必须与供应商给的完全一致");
    }

    @Test
    @DisplayName("查询参数与编码原样保留（不做 URI 归一化）")
    void keepsQueryStringUntouched() {
        String source = "https://up.example/a.mp4?x=1%2F2&y=%E4%B8%AD";
        String archived = new PassthroughArchiveStore().archive(
                new ArchiveAsset("tc-2", null, source, null, null, null, null, LocalDateTime.now()));

        assertEquals(source, archived);
        assertTrue(archived.contains("%E4%B8%AD"), "百分号编码不能被解码后再拼回去");
    }
}
