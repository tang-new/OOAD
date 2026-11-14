package com.xmu.rbac.structured.dto;
import cn.edu.xmu.clonefactory.CopyFrom;
/** 受控名称变更BO；ID和actor只允许用例从可信上下文赋值。 */
@CopyFrom(DisplayName.class)
public class NameChange {
    private String name;
    @CopyFrom.Exclude(DisplayName.class) private Long id;
    @CopyFrom.Exclude(DisplayName.class) private Long actorId;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getActorId() {
        return actorId;
    }
    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }
}
