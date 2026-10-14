package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.converter.ModerationWordConverter;
import org.chobit.knot.gateway.dto.moderation.ModerationWordDto;
import org.chobit.knot.gateway.dto.moderation.ModerationWordImportItem;
import org.chobit.knot.gateway.dto.moderation.ModerationWordImportResult;
import org.chobit.knot.gateway.entity.ModerationWordEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.ModerationWordMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.plugin.moderation.ModerationConstants;
import org.chobit.knot.gateway.plugin.moderation.ModerationSnapshotCache;
import org.chobit.knot.gateway.plugin.moderation.TextNormalizer;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.moderation.ModerationWordItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词条管理面 CRUD + 导入导出。
 *
 * <p><b>逻辑删除四必配</b>（对齐 {@code kb_logical_models}）：{@code countByWordAndCategory} 物理行判、
 * 列表 {@code includeDeleted} + mapper {@code <if>} + 排序 {@code is_deleted asc, id desc}、
 * {@code restore} 端点、引用检查——本表无跨表引用（分类是枚举、插件不引用词行 id），
 * 故删除即停参与编译，不额外设引用拦截。</p>
 *
 * <p>写操作后调 {@link ModerationSnapshotCache#invalidate()}：本节点立即生效，
 * 其余网关节点等下一次版本轮询（最迟 {@link ModerationConstants#VERSION_CHECK_INTERVAL_MILLIS} 毫秒）。</p>
 */
@Service
public class ModerationWordService {

    /** 单次导入行数上限：防一次拖入几十万行把内存和事务打爆 */
    private static final int MAX_IMPORT_ROWS = 20_000;
    /** 单条词长上限：与 DDL 的 word VARCHAR(255) 对齐 */
    private static final int MAX_WORD_LENGTH = 255;
    private static final int SEVERITY_MIN = 1;
    private static final int SEVERITY_MAX = 3;

    private final ModerationWordMapper wordMapper;
    private final ModerationWordConverter converter;
    private final ModerationSnapshotCache snapshotCache;

    /**
     * Constructs a new instance.
     */
    public ModerationWordService(ModerationWordMapper wordMapper,
                                 ModerationWordConverter converter,
                                 ModerationSnapshotCache snapshotCache) {
        this.wordMapper = wordMapper;
        this.converter = converter;
        this.snapshotCache = snapshotCache;
    }

    /**
     * Lists matching results. Executes the public operation.
     *
     * @param includeDeleted 管理列表传 true 以便展示已删除行（浅红底 + 恢复按钮）；
     *                       其余查询保持 false，已删除项不参与匹配也不列出
     */
    public PageResult<ModerationWordDto> list(PageRequest pageRequest,
                                              String keyword,
                                              String categoryCode,
                                              Boolean includeDeleted) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ModerationWordEntity> pageInfo = new PageInfo<>(
                    wordMapper.list(normalizeKeyword(keyword), normalizeCategory(categoryCode), includeDeleted)
            );
            List<ModerationWordDto> list = pageInfo.getList().stream()
                    .map(converter::toDto)
                    .toList();
            return PageResult.of(list, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModerationWordDto getById(Long id) {
        ModerationWordEntity entity = wordMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sensitive word not found");
        }
        return converter.toDto(entity);
    }

    /**
     * 编码可用性：唯一键是 (word, category_code)，按物理行判定。
     */
    public boolean isWordAvailable(String word, String categoryCode, Long excludeId) {
        String normalized = requireWord(word);
        String category = requireCategory(categoryCode);
        Long count = wordMapper.countByWordAndCategory(normalized, category, excludeId);
        return count == null || count == 0;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ModerationWordDto create(ModerationWordDto request) {
        String word = requireWord(request.word());
        String category = requireCategory(request.categoryCode());
        assertWordAvailable(word, category, null);
        ModerationWordEntity entity = converter.toEntity(request);
        entity.setWord(word);
        entity.setCategoryCode(category);
        entity.setNormalizedWord(normalizeKey(word));
        entity.setSeverity(resolveSeverity(request.severity()));
        entity.setLang(defaultLang(request.lang()));
        entity.setStatus(EnabledStatusEnum.codeOf(request.enabled()));
        wordMapper.insert(entity);
        invalidateSnapshot();
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ModerationWordDto update(Long id, ModerationWordDto request) {
        ModerationWordEntity existing = wordMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sensitive word not found");
        }
        String word = requireWord(request.word());
        String category = requireCategory(request.categoryCode());
        assertWordAvailable(word, category, id);
        ModerationWordEntity entity = converter.toEntity(request);
        entity.setId(id);
        entity.setWord(word);
        entity.setCategoryCode(category);
        entity.setNormalizedWord(normalizeKey(word));
        entity.setSeverity(resolveSeverity(request.severity()));
        entity.setLang(defaultLang(request.lang()));
        entity.setStatus(EnabledStatusEnum.codeOf(request.enabled()));
        wordMapper.update(entity);
        invalidateSnapshot();
        return getById(id);
    }

    /**
     * Updates the sensitive word enabled status only.
     */
    @Transactional
    public ModerationWordDto updateStatus(Long id, boolean enabled) {
        if (wordMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sensitive word not found");
        }
        wordMapper.updateStatus(id, EnabledStatusEnum.codeOf(enabled));
        invalidateSnapshot();
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     *
     * <p>停用中的词才允许删除：启用词先停用再删，避免误删后匹配行为静默变化。</p>
     */
    @Transactional
    public void delete(Long id) {
        ModerationWordEntity entity = wordMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sensitive word not found");
        }
        if (EnabledStatusEnum.isEnabled(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "启用中的敏感词不能删除，请先停用");
        }
        if (wordMapper.logicalDelete(id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sensitive word not found");
        }
        invalidateSnapshot();
    }

    /**
     * Restores a logically deleted sensitive word.
     *
     * <p>编码唯一性按物理行判定（uk_word_category 不区分 is_deleted），所以逻辑删除后
     * 同 (word, category_code) 无法新建，只能恢复。</p>
     */
    @Transactional
    public ModerationWordDto restore(Long id) {
        ModerationWordEntity entity = wordMapper.getByIdIncludingDeleted(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sensitive word not found");
        }
        if (!Integer.valueOf(1).equals(entity.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONFLICT, "敏感词未被删除，无需恢复");
        }
        if (wordMapper.restore(id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "sensitive word not found");
        }
        invalidateSnapshot();
        return getById(id);
    }

    /**
     * 批量导入：逐行独立校验，**部分失败不影响已成功的行**。
     *
     * <p>去重按 (word, category_code)，两档语义不同：
     * <ul>
     *   <li><b>库内已存在</b>（含已逻辑删除行，因为唯一键不区分 is_deleted）→ 静默跳过，
     *       计入 {@code skipped}。重复导入同一份文件是常见运维操作，不该报错。</li>
     *   <li><b>本批内重复</b> → 计入 {@code errors} 并指明行号，让运营发现文件本身有重复行。</li>
     * </ul>
     * 一次性拉全量 word 做内存判重，避免逐条 count 查询。</p>
     *
     * <p>注意：判重必须含已逻辑删除行——{@code uk_word_category} 是物理唯一键，
     * 若按 {@code listForExport()}（仅未删除）判重，重复导入一个已删除词会撞唯一键，
     * {@code DataIntegrityViolationException} 直接让整个 {@code @Transactional} 回滚，
     * "部分失败不影响已成功行"的承诺就破了。</p>
     */
    @Transactional
    public ModerationWordImportResult importWords(List<ModerationWordImportItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return ModerationWordImportResult.of(0, 0, 0, List.of());
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "单次导入超过上限 " + MAX_IMPORT_ROWS + " 行，请分批导入");
        }
        Set<String> existing = existingKeys();
        Set<String> seenInBatch = new HashSet<>();
        List<String> errors = new ArrayList<>();
        int inserted = 0;
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            ModerationWordImportItem row = rows.get(i);
            int lineNo = i + 1;
            String word;
            String category;
            try {
                word = requireWord(row == null ? null : row.word());
                category = requireCategory(row == null ? null : row.categoryCode());
            } catch (BusinessException e) {
                errors.add("第 " + lineNo + " 行：" + e.getMessage());
                continue;
            }
            String key = keyOf(word, category);
            // 顺序不能反：先判本批内重复（报错，让运营知道文件里有重复行），
            // 再判库内已存在（静默跳过）。若把 existing.add(key) 放在前面，
            // 本批重复会被当成"库内已存在"静默跳过，seenInBatch 分支永远走不到。
            if (!seenInBatch.add(key)) {
                errors.add("第 " + lineNo + " 行：与本批中前面的行重复（" + word + " / " + category + "）");
                continue;
            }
            if (existing.contains(key)) {
                skipped++;
                continue;
            }
            int severity;
            try {
                severity = resolveSeverity(row.severity());
            } catch (BusinessException e) {
                errors.add("第 " + lineNo + " 行：" + e.getMessage());
                continue;
            }
            ModerationWordEntity entity = new ModerationWordEntity();
            entity.setWord(word);
            entity.setCategoryCode(category);
            entity.setNormalizedWord(normalizeKey(word));
            entity.setSeverity(severity);
            entity.setLang(defaultLang(row.lang()));
            entity.setStatus(EnabledStatusEnum.codeOf(true));
            entity.setRemark(row.remark());
            wordMapper.insert(entity);
            inserted++;
        }
        if (inserted > 0) {
            invalidateSnapshot();
        }
        return ModerationWordImportResult.of(rows.size(), inserted, skipped, errors);
    }

    /**
     * 导出全部未删除词条（含停用）：前端转 CSV 或 xlsx 由调用方决定。
     */
    public List<ModerationWordDto> exportWords() {
        return wordMapper.listForExport().stream()
                .map(converter::toDto)
                .toList();
    }

    /**
     * Builds the audit snapshot recorded by {@code @OperationLog}.
     * Evaluated by the {@code @Around} aspect before the method body runs, so a logical
     * delete still captures the full pre-delete state.
     */
    public Map<String, Object> auditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            ModerationWordEntity entity = wordMapper.getByIdIncludingDeleted(id);
            return entity == null ? null : JsonKit.toMap(converter.toDto(entity));
        } catch (BusinessException e) {
            return null;
        }
    }

    private void invalidateSnapshot() {
        snapshotCache.invalidate();
    }

    private Set<String> existingKeys() {
        // 必须含已逻辑删除行：uk_word_category 是物理唯一键，删除行仍占键位。
        // 若按 listForExport()（仅未删除）判重，重复导入已删除词会撞唯一键，
        // DataIntegrityViolationException 直接让整个 @Transactional 回滚。
        Set<String> keys = new HashSet<>();
        for (ModerationWordEntity entity : wordMapper.list(null, null, Boolean.TRUE)) {
            keys.add(keyOf(entity.getWord(), entity.getCategoryCode()));
        }
        return keys;
    }

    private static String keyOf(String word, String categoryCode) {
        return normalizeKey(word) + "\u0000" + categoryCode;
    }

    private void assertWordAvailable(String word, String categoryCode, Long excludeId) {
        Long count = wordMapper.countByWordAndCategory(word, categoryCode, excludeId);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "同分类下已存在相同敏感词");
        }
    }

    private static String requireWord(String word) {
        String value = word == null ? null : word.trim();
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "敏感词不能为空");
        }
        if (value.length() > MAX_WORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "敏感词长度超过上限 " + MAX_WORD_LENGTH + " 字符");
        }
        return value;
    }

    private static String requireCategory(String categoryCode) {
        String value = categoryCode == null ? null : categoryCode.trim();
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "敏感词分类不能为空");
        }
        return value;
    }

    private static int resolveSeverity(Integer severity) {
        if (severity == null) {
            return 2;
        }
        if (severity < SEVERITY_MIN || severity > SEVERITY_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "严重度必须为 " + SEVERITY_MIN + "-" + SEVERITY_MAX);
        }
        return severity;
    }

    private static String normalizeKey(String word) {
        String normalized = TextNormalizer.normalize(word);
        return normalized == null ? word.trim() : normalized;
    }

    private static String defaultLang(String lang) {
        return lang == null || lang.isBlank() ? "zh" : lang.trim();
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    private static String normalizeCategory(String categoryCode) {
        String value = categoryCode != null ? categoryCode.trim() : "";
        return value.isEmpty() ? null : value;
    }
}
