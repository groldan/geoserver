/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.rest.v2.controller;

import static org.junit.Assert.assertEquals;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import net.sf.json.JSON;

public class ManifestsApiControllerIT extends GeoServerSystemTestSupport {

    @Test
    public void testGetVersions() throws Exception {
        MockHttpServletResponse response = super.getAsServletResponse("/rest/v2/about/version");
        JSON json = super.json(response);
        System.out.println(json);
        assertEquals(200, response.getStatus());
        // assertEquals("application/json", response.getContentType());
    }
}
