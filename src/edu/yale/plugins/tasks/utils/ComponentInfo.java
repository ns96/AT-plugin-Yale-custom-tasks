package edu.yale.plugins.tasks.utils;

/**
* Created with IntelliJ IDEA.
* User: nathan
* Date: 9/30/13
* Time: 9:32 PM
*
 * This class is use to store component information
*/
class ComponentInfo {

    private Long componentId;
    private String resourceLevel;
    private String title;
    private Boolean hasChild;

    ComponentInfo(Long componentId, String resourceLevel, String title, Boolean hasChild) {
        this.componentId = componentId;
        this.resourceLevel = resourceLevel;
        this.title = title;
        this.hasChild = hasChild;
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public String getResourceLevel() {
        return resourceLevel;
    }

    public void setResourceLevel(String resourceLevel) {
        this.resourceLevel = resourceLevel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean isHasChild() {
        return hasChild;
    }

    public void setHasChild(Boolean hasChild) {
        this.hasChild = hasChild;
    }
}
