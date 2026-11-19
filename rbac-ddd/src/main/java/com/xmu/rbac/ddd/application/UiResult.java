package com.xmu.rbac.ddd.application;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.Ui;

/** UI 应用快照，仅包含对外展示所需的属性。 */
@CopyFrom(Ui.class)
public record UiResult(
        Long id,
        String systemCode,
        Long parentId,
        String type,
        String code,
        String name,
        String route,
        String component,
        Integer sortOrder,
        String status,
        String permissionCode) {
    /** 复制资源的可见属性，响应子树由调用方单独组装。 */
    public static UiResult from(Ui ui) {
        return CloneFactory.copy(new UiResult(null, null, null, null, null, null,
                null, null, null, null, null), ui);
    }
}
