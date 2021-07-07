/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.Objects;
import org.geoserver.catalog.LegendInfo;

public class LegendInfoImpl implements LegendInfo {

    String id;

    int width;

    int height;

    String format;

    String onlineResource;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public String getOnlineResource() {
        return onlineResource;
    }

    @Override
    public void setOnlineResource(String onlineResource) {
        this.onlineResource = onlineResource;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append("[width:")
                .append(width)
                .append(", height:")
                .append(height)
                .append(", format:")
                .append(format)
                .append(", onlineResource:")
                .append(onlineResource)
                .append(']')
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, height, id, onlineResource, width);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LegendInfoImpl)) return false;
        LegendInfoImpl other = (LegendInfoImpl) obj;
        return Objects.equals(format, other.format)
                && height == other.height
                && Objects.equals(id, other.id)
                && Objects.equals(onlineResource, other.onlineResource)
                && width == other.width;
    }
}
