package com.xmu.tools.clonefactory;

import cn.edu.xmu.clonefactory.*;

@CopyFrom({RequestBean.class, StoredBean.class})
@CopyTo({StoredBean.class, PublicView.class})
@CopyNotNullFrom(PatchBean.class)
@CopyNotNullTo(StoredBean.class)
public class Domain {
    private Long id;
    private String name;
    private Integer age;
    private Boolean enabled;
    @CopyFrom.Exclude(RequestBean.class)
    @CopyTo.Exclude(PublicView.class)
    @CopyNotNullFrom.Exclude(PatchBean.class)
    private String password;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
