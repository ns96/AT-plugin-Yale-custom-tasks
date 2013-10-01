package edu.yale.plugins.tasks.utils;

/**
* Created with IntelliJ IDEA.
* User: nathan
* Date: 9/30/13
* Time: 8:40 PM
* To change this template use File | Settings | File Templates.
*/
class SeriesInfo {

    private String uniqueId;
    private String seriesTitle;
    private String componentIds = null;
    private Long resourceId = null;

    SeriesInfo(String uniqueId, String seriesTitle) {
        this.uniqueId = uniqueId;
        this.seriesTitle = seriesTitle;
    }

    SeriesInfo(Long resourceId, String uniqueId, String seriesTitle) {
        this.resourceId = resourceId;
        this.uniqueId = uniqueId;
        this.seriesTitle = seriesTitle;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getSeriesTitle() {
        return seriesTitle;
    }

    public void setSeriesTitle(String seriesTitle) {
        this.seriesTitle = seriesTitle;
    }

    public String getComponentIds() {
        return componentIds;
    }

    public void addComponentId(Long componentId) {
        if (this.componentIds == null) {
            this.componentIds = componentId.toString();
        } else {
            this.componentIds += ", " + componentId;
        }
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }
}
