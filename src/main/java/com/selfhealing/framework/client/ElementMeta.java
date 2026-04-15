package com.selfhealing.framework.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ElementMeta {

    private String tag;
    private String id;
    private String text;
    private List<String> classes;
    private String type;
    private String name;

    @JsonProperty("aria_label")
    private String ariaLabel;

    private String placeholder;
    private String role;

    @JsonProperty("data_testid")
    private String dataTestId;

    @JsonProperty("parent_tag")
    private String parentTag;

    @JsonProperty("siblings_count")
    private int siblingsCount;

    public ElementMeta() {}

    public ElementMeta(String tag, String id, String text, List<String> classes,
                       String type, String name, String ariaLabel, String placeholder,
                       String role, String dataTestId, String parentTag, int siblingsCount) {
        this.tag = tag;
        this.id = id;
        this.text = text;
        this.classes = classes;
        this.type = type;
        this.name = name;
        this.ariaLabel = ariaLabel;
        this.placeholder = placeholder;
        this.role = role;
        this.dataTestId = dataTestId;
        this.parentTag = parentTag;
        this.siblingsCount = siblingsCount;
    }

    public String getTag()                  { return tag; }
    public String getId()                   { return id; }
    public String getText()                 { return text; }
    public List<String> getClasses()        { return classes; }
    public String getType()                 { return type; }
    public String getName()                 { return name; }
    public String getAriaLabel()            { return ariaLabel; }
    public String getPlaceholder()          { return placeholder; }
    public String getRole()                 { return role; }
    public String getDataTestId()           { return dataTestId; }
    public String getParentTag()            { return parentTag; }
    public int getSiblingsCount()           { return siblingsCount; }
}